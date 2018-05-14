package com.haulmont.addon.imap.sync;

import com.haulmont.addon.imap.api.ImapAPI;
import com.haulmont.addon.imap.dao.ImapDao;
import com.haulmont.addon.imap.dto.ImapFolderDto;
import com.haulmont.addon.imap.entity.*;
import com.haulmont.addon.imap.sync.events.ImapEvents;
import com.haulmont.addon.imap.sync.listener.ImapFolderSyncActivationEvent;
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
    private static boolean TRACK_FOLDER_ACTIVATION = true;

    private final ImapDao dao;
    private final ImapEvents imapEvents;
    private final Authentication authentication;
    private final ImapAPI imapAPI;
    private final ImapFolderListener imapFolderListener;

    private final ExecutorService executor = Executors.newCachedThreadPool(new ThreadFactory() {
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

    private final ScheduledExecutorService scheduledExecutorService = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread thread = new Thread(
                r, "ImapMailBoxFullSyncRefresher"
        );
        thread.setDaemon(true);
        return thread;
    });

    private final ConcurrentMap<UUID, ScheduledFuture<?>> fullSyncRefreshers = new ConcurrentHashMap<>();
    private final ConcurrentMap<UUID, Future<?>> fullSyncTasks = new ConcurrentHashMap<>();

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
                Collection<ImapFolderDto> allFolders = ImapFolderDto.flattenList(imapAPI.fetchFolders(mailBox));
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
                    tasks.add(folderFullSyncTask(cubaFolder));
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

        try {
            scheduledExecutorService.shutdownNow();
        } catch (Exception e) {
            log.warn("Exception while shutting down scheduled executor", e);
        }
    }

    @Override
    public int getOrder() {
        return LOWEST_PLATFORM_PRECEDENCE;
    }

    @EventListener
    public void handleFolderEvent(ImapFolderSyncActivationEvent event) {
        if (!TRACK_FOLDER_ACTIVATION) {
            return;
        }
        ImapFolder cubaFolder = event.getFolder();

        if (event.getType() == ImapFolderSyncActivationEvent.Type.ACTIVATE) {
            executeFullSyncTasks(Collections.singleton(folderFullSyncTask(cubaFolder)));
            imapFolderListener.subscribe(cubaFolder);
        } else {
            cancel(fullSyncRefreshers.remove(cubaFolder.getId()), false);
            cancel(fullSyncTasks.remove(cubaFolder.getId()), true);
            imapFolderListener.unsubscribe(cubaFolder);
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

    private Future<?> folderFullSyncTask(@Nonnull ImapFolder cubaFolder) {
        UUID folderId = cubaFolder.getId();
        Future<?> task = fullSyncTasks.get(folderId);
        if (task != null && !task.isDone() && !task.isCancelled()) {
            return task;
        }

        CompletableFuture<Void> cf = CompletableFuture.runAsync(() -> {
            imapEvents.handleNewMessages(cubaFolder);
            imapEvents.handleMissedMessages(cubaFolder);
            imapEvents.handleChangedMessages(cubaFolder);
            ScheduledFuture<?> newTask = scheduledExecutorService.schedule(() -> {
                ImapFolder folder = getFolder(folderId);
                if (folder != null) {
                    folderFullSyncTask(cubaFolder);
                    imapFolderListener.subscribe(cubaFolder);
                }
                fullSyncRefreshers.remove(folderId);
            }, 5, TimeUnit.SECONDS);

            ScheduledFuture<?> oldTask = fullSyncRefreshers.put(folderId, newTask);
            cancel(oldTask, false);
        }, executor);
        fullSyncTasks.put(folderId, cf);
        cf.thenAccept(ignore -> fullSyncTasks.remove(folderId, cf));
        return cf;
    }

    private ImapFolder getFolder(UUID folderId) {
        authentication.begin();
        try {
            return dao.findFolder(folderId);
        } finally {
            authentication.end();
        }
    }

    private void cancel(Future<?> task, boolean interrupt) {
        if (task != null) {
            if (!task.isDone() && !task.isCancelled()) {
                task.cancel(interrupt);
            }
        }
    }

}
