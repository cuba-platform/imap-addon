package com.haulmont.addon.imap.sync;

import com.haulmont.addon.imap.dao.ImapDao;
import com.haulmont.addon.imap.entity.*;
import com.haulmont.addon.imap.sync.events.ImapEvents;
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

@Component("imap_SyncManager")
public class ImapSyncManager implements AppContext.Listener, Ordered {

    private final static Logger log = LoggerFactory.getLogger(ImapSyncManager.class);
    private static boolean TRACK_FOLDER_ACTIVATION = true;

    private final ImapDao dao;
    private final ImapEvents imapEvents;
    private final Authentication authentication;

    private final ExecutorService executor = Executors.newCachedThreadPool(new ThreadFactory() {
        private final AtomicInteger threadNumber = new AtomicInteger(1);
        @Override
        public Thread newThread(@Nonnull Runnable r) {
            Thread thread = new Thread(
                    r, "ImapMailBoxSync-" + threadNumber.getAndIncrement()
            );
            thread.setDaemon(true);
            return thread;
        }
    });

    private final ScheduledExecutorService scheduledExecutorService = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread thread = new Thread(
                r, "ImapMailBoxSyncRefresher"
        );
        thread.setDaemon(true);
        return thread;
    });

    private final ConcurrentMap<UUID, ScheduledFuture<?>> syncRefreshers = new ConcurrentHashMap<>();
    private final ConcurrentMap<UUID, Future<?>> syncTasks = new ConcurrentHashMap<>();

    @SuppressWarnings("CdiInjectionPointsInspection")
    @Inject
    public ImapSyncManager(ImapDao dao,
                           ImapEvents imapEvents,
                           Authentication authentication) {

        this.dao = dao;
        this.imapEvents = imapEvents;
        this.authentication = authentication;
    }

    @PostConstruct
    public void listenContext() {
        AppContext.addListener(this);
    }

    @Override
    public void applicationStarted() {
        authentication.begin();
        try {
            Collection<Future<?>> tasks = new ArrayList<>();
            for (ImapMailBox mailBox : dao.findMailBoxes()) {
                log.debug("{}: synchronizing", mailBox);
                imapEvents.init(mailBox);
                for (ImapFolder cubaFolder : mailBox.getProcessableFolders()) {
                    tasks.add(folderSyncTask(cubaFolder));
                }
            }

            executeSyncTasks(tasks);
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

        for (ImapMailBox mailBox : dao.findMailBoxes()) {
            try {
                imapEvents.shutdown(mailBox);
            } catch (Exception e) {
                log.warn("Exception while shutting down imapEvents for mailbox " + mailBox, e);
            }
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
            CompletableFuture.runAsync(() -> imapEvents.init(cubaFolder), executor).thenAcceptAsync(ignore ->
                    executeSyncTasks(Collections.singleton(folderSyncTask(cubaFolder)))
            );
        } else {
            CompletableFuture.runAsync(() -> imapEvents.shutdown(cubaFolder), executor);
            cancel(syncRefreshers.remove(cubaFolder.getId()), false);
            cancel(syncTasks.remove(cubaFolder.getId()), true);
        }

    }

    private void executeSyncTasks(Collection<Future<?>> tasks) {
        executor.submit(() -> {
            try {
                for (Future<?> task : tasks) {
                    task.get(5, TimeUnit.MINUTES);
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

    private Future<?> folderSyncTask(@Nonnull ImapFolder cubaFolder) {
        UUID folderId = cubaFolder.getId();
        Future<?> task = syncTasks.get(folderId);
        if (task != null && !task.isDone() && !task.isCancelled()) {
            return task;
        }

        CompletableFuture<Void> cf = CompletableFuture.runAsync(() -> {
            imapEvents.handleNewMessages(cubaFolder);
            imapEvents.handleMissedMessages(cubaFolder);
            imapEvents.handleChangedMessages(cubaFolder);
            ScheduledFuture<?> newTask = scheduledExecutorService.schedule(() -> {
                ImapFolder folder = dao.findFolder(folderId);
                if (folder != null) {
                    folderSyncTask(cubaFolder);
                }
                syncRefreshers.remove(folderId);
            }, 5, TimeUnit.SECONDS);

            ScheduledFuture<?> oldTask = syncRefreshers.put(folderId, newTask);
            cancel(oldTask, false);
        }, executor);
        syncTasks.put(folderId, cf);
        cf.thenAccept(ignore -> syncTasks.remove(folderId, cf));
        return cf;
    }

    private void cancel(Future<?> task, boolean interrupt) {
        if (task != null) {
            if (!task.isDone() && !task.isCancelled()) {
                task.cancel(interrupt);
            }
        }
    }

}
