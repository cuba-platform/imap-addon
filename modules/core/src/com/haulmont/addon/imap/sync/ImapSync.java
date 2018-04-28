package com.haulmont.addon.imap.sync;

import com.haulmont.addon.imap.api.ImapAPI;
import com.haulmont.addon.imap.dao.ImapDao;
import com.haulmont.addon.imap.dto.ImapFolderDto;
import com.haulmont.addon.imap.entity.*;
import com.haulmont.addon.imap.sync.events.ImapEvents;
import com.haulmont.addon.imap.sync.listener.ImapFolderSyncEvent;
import com.haulmont.addon.imap.sync.listener.ImapFolderListener;
import com.haulmont.cuba.core.sys.AppContext;
import com.haulmont.cuba.security.app.Authentication;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.core.Ordered;
import org.springframework.stereotype.Component;

import javax.annotation.Nonnull;
import javax.annotation.PostConstruct;
import javax.inject.Inject;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

@Component("imap_Sync")
public class ImapSync implements AppContext.Listener, Ordered {

    private final static Logger log = LoggerFactory.getLogger(ImapSync.class);

    private final ImapDao dao;
    private final ImapEvents imapEvents;
    private final Authentication authentication;
    private final ImapAPI imapAPI;
    private final ImapFolderListener imapFolderListener;

    private ExecutorService executor = Executors.newCachedThreadPool(new ThreadFactory() {
        private final AtomicInteger threadNumber = new AtomicInteger(1);
        @Override
        public Thread newThread(@Nonnull Runnable r) {
            Thread thread = new Thread(
                    r, "ImapMailBoxFullSync-" + threadNumber.getAndIncrement()
            );
            thread.setDaemon(true);
            return thread;
        }
    });

    private ScheduledExecutorService scheduledExecutorService = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread thread = new Thread(
                r, "ImapMailBoxFullSyncRefresher"
        );
        thread.setDaemon(true);
        return thread;
    });

    private ConcurrentMap<ImapFolderSyncAction, ScheduledFuture<?>> fullSyncRefreshers = new ConcurrentHashMap<>();
    private ConcurrentMap<ImapFolderSyncAction, Future<?>> fullSyncTasks = new ConcurrentHashMap<>();

    @SuppressWarnings("CdiInjectionPointsInspection")
    @Inject
    public ImapSync(ImapDao dao,
                    ImapEvents imapEvents,
                    Authentication authentication,
                    ImapAPI imapAPI,
                    ImapFolderListener imapFolderListener) {

        this.dao = dao;
        this.imapEvents = imapEvents;
        this.authentication = authentication;
        this.imapAPI = imapAPI;
        this.imapFolderListener = imapFolderListener;
    }

    @PostConstruct
    public void listenContext() {
        AppContext.addListener(this);
    }

    @Override
    public void applicationStarted() {
        authentication.begin();
        try {
            Collection<ImapFolder> allListenedFolders = new ArrayList<>();
            Collection<Future<?>> tasks = new ArrayList<>();
            for (ImapMailBox mailBox : dao.findMailBoxes()) {
                log.debug("{}: synchronizing", mailBox);
                Collection<ImapFolderDto> allFolders = imapAPI.fetchFolders(mailBox);
                Collection<ImapFolder> processableFolders = mailBox.getProcessableFolders();
                Collection<ImapFolder> listenedFolders = new ArrayList<>(processableFolders.size());
                for (ImapFolder cubaFolder : processableFolders) {
                    boolean imapFolderExists = allFolders.stream()
                            .filter(f -> f.getName().equals(cubaFolder.getName()))
                            .findFirst()
                            .map(ImapFolderDto::getImapFolder).isPresent();

                    if (!imapFolderExists) {
                        log.info("Can't find folder {}. Probably it was removed", cubaFolder.getName());
                        continue;
                    }
                    listenedFolders.add(cubaFolder);
                    tasks.addAll(folderFullSyncTasks(cubaFolder));
                }

                allListenedFolders.addAll(listenedFolders);
            }

            executeFullSyncTasks(tasks);
            allListenedFolders.forEach(imapFolderListener::subscribe);
        } finally {
            authentication.end();
        }
    }

    @Override
    public void applicationStopped() {
        try {
            executor.shutdownNow();
        } catch (Exception e) {
            log.warn("Exception while shutting down executor", e);
        }

    }

    @Override
    public int getOrder() {
        return LOWEST_PLATFORM_PRECEDENCE;
    }

    @EventListener
    public void handleFolderEvent(ImapFolderSyncEvent event) {
        ImapFolder cubaFolder = event.getFolder();

        if (event.getType() == ImapFolderSyncEvent.Type.ADDED) {
            executeFullSyncTasks(folderFullSyncTasks(cubaFolder));
            imapFolderListener.subscribe(cubaFolder);
        } else {
            for (ImapFolderSyncAction.Type syncActionType : ImapFolderSyncAction.Type.values()) {
                ImapFolderSyncAction action = new ImapFolderSyncAction(cubaFolder.getId(), syncActionType);
                cancel(fullSyncRefreshers.remove(action), false);
                cancel(fullSyncTasks.remove(action), true);
            }
            imapFolderListener.unsubscribe(cubaFolder);
        }

    }

    @EventListener
    public void handleFolderSyncEvent(com.haulmont.addon.imap.sync.ImapFolderSyncEvent event) {
        ImapFolderSyncAction action = event.getAction();

        UUID folderId = action.getFolderId();
        ImapFolderSyncAction.Type type = action.getType();

        ScheduledFuture<?> newTask = scheduledExecutorService.schedule(() -> {
            ImapFolder cubaFolder = getFolder(folderId);
            if (cubaFolder != null) {
                fullSyncTask(cubaFolder, type);
                imapFolderListener.subscribe(cubaFolder);
            }
            ImapFolderSyncAction nextAction = new ImapFolderSyncAction(folderId, type);
            fullSyncRefreshers.remove(nextAction);
            handleFolderSyncEvent(new com.haulmont.addon.imap.sync.ImapFolderSyncEvent(nextAction));
        }, 30, TimeUnit.SECONDS);

        ScheduledFuture<?> oldTask = fullSyncRefreshers.put(action, newTask);
        cancel(oldTask, false);
    }

    private void cancel(Future<?> task, boolean interrupt) {
        if (task != null) {
            if (!task.isDone() && !task.isCancelled()) {
                task.cancel(interrupt);
            }
        }
    }

    private void executeFullSyncTasks(Collection<Future<?>> tasks) {
        executor.submit(() -> {
            try {
                for (Future<?> task : tasks) {
                    task.get(1, TimeUnit.MINUTES);
                }
            } catch (InterruptedException | TimeoutException e) {
                log.error("Synchronizing mailbox folders was interrupted", e);
                for (Future<?> task : tasks) {
                    cancel(task, true);
                }
                Thread.currentThread().interrupt();
            } catch (ExecutionException e) {
                log.error("Full sync failed", e);
                throw new RuntimeException("Can't synchronize mail boxes", e);
            }
        });
    }

    private Collection<Future<?>> folderFullSyncTasks(@Nonnull ImapFolder cubaFolder) {
        Collection<Future<?>> tasks = new ArrayList<>(3);

        Future<?> newMessagesTask = fullSyncTask(cubaFolder, ImapFolderSyncAction.Type.NEW);
        if (newMessagesTask != null) {
            tasks.add(newMessagesTask);
        }

        Future<?> missedMessagesTask = fullSyncTask(cubaFolder, ImapFolderSyncAction.Type.MISSED);
        if (missedMessagesTask != null) {
            tasks.add(missedMessagesTask);
        }

        Future<?> changedMessagesTask = fullSyncTask(cubaFolder, ImapFolderSyncAction.Type.CHANGED);
        if (changedMessagesTask != null) {
            tasks.add(changedMessagesTask);
        }

        return tasks;
    }

    private ImapFolder getFolder(UUID folderId) {
        authentication.begin();
        try {
            return dao.findFolder(folderId);
        } finally {
            authentication.end();
        }
    }

    private Future<?> fullSyncTask(@Nonnull ImapFolder cubaFolder, ImapFolderSyncAction.Type type) {
        ImapFolderSyncAction action = new ImapFolderSyncAction(cubaFolder.getId(), type);
        Future<?> task = fullSyncTasks.get(action);
        if (task != null && !task.isDone() && !task.isCancelled()) {
            return task;
        }
        Runnable runnable = null;
        switch (type) {
            case NEW:

                runnable = () -> {
                    log.info("Search new messages for {}", cubaFolder);
                    imapEvents.handleNewMessages(cubaFolder);
                };
                break;
            case MISSED:
                runnable = () -> {
                    log.info("Track deleted/moved messages for {}", cubaFolder);
                    imapEvents.handleMissedMessages(cubaFolder);
                };
                break;
            case CHANGED:
                runnable = () -> {
                    log.info("Update messages for {}", cubaFolder);
                    imapEvents.handleChangedMessages(cubaFolder);
                };
        }

        CompletableFuture<Void> cf = CompletableFuture.runAsync(runnable, executor);
        fullSyncTasks.put(action, cf);
        cf.thenAccept(ignore -> fullSyncTasks.remove(action, cf));
        return cf;

    }

}
