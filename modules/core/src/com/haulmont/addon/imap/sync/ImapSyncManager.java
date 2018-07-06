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
    private static boolean TRACK_MAILBOX_ACTIVATION = true;

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
    private final ConcurrentMap<UUID, CompletableFuture<?>> syncTasks = new ConcurrentHashMap<>();

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
            for (ImapMailBox mailBox : dao.findMailBoxes()) {
                log.debug("{}: synchronizing", mailBox);
                UUID mailBoxId = mailBox.getId();
                CompletableFuture.runAsync(() -> imapEvents.init(mailBox), executor);
                runEventsEmitter(mailBoxId);
            }
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

        authentication.begin();
        try {
            for (ImapMailBox mailBox : dao.findMailBoxes()) {
                try {
                    imapEvents.shutdown(mailBox);
                } catch (Exception e) {
                    log.warn("Exception while shutting down imapEvents for mailbox " + mailBox, e);
                }
            }
        } finally {
            authentication.end();
        }
    }

    @Override
    public int getOrder() {
        return LOWEST_PLATFORM_PRECEDENCE;
    }

    @EventListener
    public void handleActivationEvent(ImapMailboxSyncActivationEvent event) {
        if (!TRACK_MAILBOX_ACTIVATION) {
            return;
        }
        UUID mailboxId = event.getMailBox().getId();

        if (event.getType() == ImapMailboxSyncActivationEvent.Type.ACTIVATE) {
            CompletableFuture.runAsync(() -> imapEvents.init(event.getMailBox()), executor);
            runEventsEmitter(mailboxId);
        } else {
            CompletableFuture.runAsync(() -> imapEvents.shutdown(event.getMailBox()), executor);
            cancel(syncRefreshers.remove(mailboxId));
            cancel(syncTasks.remove(mailboxId));
        }

    }

    private void runEventsEmitter(UUID mailboxId) {
        CompletableFuture<?> task = syncTasks.get(mailboxId);
        if (task != null && !task.isDone() && !task.isCancelled()) {
            return;
        }

        CompletableFuture<Void> cf = CompletableFuture.runAsync(() -> {
            authentication.begin();
            try {
                ImapMailBox mailBox = dao.findMailBox(mailboxId);
                if (mailBox == null) {
                    return;
                }
                for (ImapFolder cubaFolder : mailBox.getProcessableFolders()) {
                    imapEvents.handleNewMessages(cubaFolder);
                    imapEvents.handleMissedMessages(cubaFolder);
                    imapEvents.handleChangedMessages(cubaFolder);
                }
            } finally {
                authentication.end();
            }
            ScheduledFuture<?> newTask = scheduledExecutorService.schedule(() -> {
                runEventsEmitter(mailboxId);
                syncRefreshers.remove(mailboxId);
            }, 5, TimeUnit.SECONDS);

            ScheduledFuture<?> oldTask = syncRefreshers.put(mailboxId, newTask);
            cancel(oldTask);
        }, executor);
        syncTasks.put(mailboxId, cf);
        cf.thenRunAsync(() -> syncTasks.remove(mailboxId, cf), executor);
    }

    private void cancel(Future<?> task) {
        if (task != null) {
            if (!task.isDone() && !task.isCancelled()) {
                task.cancel(false);
            }
        }
    }

}
