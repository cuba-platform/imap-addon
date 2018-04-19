package com.haulmont.addon.imap.sync.listener;

import com.haulmont.addon.imap.core.ImapHelper;
import com.haulmont.addon.imap.entity.ImapFolder;
import com.haulmont.addon.imap.sync.events.ImapEvents;
import com.haulmont.addon.imap.exception.ImapException;
import com.sun.mail.imap.IMAPFolder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import javax.annotation.Nonnull;
import javax.annotation.PreDestroy;
import javax.inject.Inject;
import javax.mail.Message;
import javax.mail.MessagingException;
import javax.mail.event.MessageChangedListener;
import javax.mail.event.MessageCountEvent;
import javax.mail.event.MessageCountListener;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

@Component("imap_FolderListener")
public class ImapFolderListener {

    private final static Logger log = LoggerFactory.getLogger(ImapFolderListener.class);

    private final Map<UUID, FolderObjects> folders = new HashMap<>();

    private final Object lock = new Object();

    private final ImapHelper imapHelper;

    private final ImapEvents imapEvents;

    private ExecutorService executor = Executors.newCachedThreadPool(new ThreadFactory() {
        private final AtomicInteger threadNumber = new AtomicInteger(1);
        @Override
        public Thread newThread(@Nonnull Runnable r) {
            Thread thread = new Thread(
                    r, "ImapMailBoxCountListener-" + threadNumber.getAndIncrement()
            );
            thread.setDaemon(true);
            return thread;
        }
    });

    private ScheduledExecutorService scheduledExecutorService = Executors.newSingleThreadScheduledExecutor();

    @Inject
    public ImapFolderListener(ImapHelper imapHelper,
                              ImapEvents imapEvents) {

        this.imapHelper = imapHelper;
        this.imapEvents = imapEvents;
    }

    public void unsubscribe(@Nonnull ImapFolder folder) {
        UUID folderId = folder.getId();
        log.info("unsubscribe count listener for folder#{}", folderId);

        synchronized (lock) {
            FolderObjects folderObjects = folders.remove(folderId);
            if (folderObjects == null) {
                log.debug("there is no count listeners for folder#{}", folderId);
                return;
            }

            IMAPFolder imapFolder = folderObjects.imapFolder;
            if (imapFolder != null && imapFolder.isOpen() && folderObjects.countListener != null) {
                imapFolder.removeMessageCountListener(folderObjects.countListener);
            }
            if (imapFolder != null && imapFolder.isOpen() && folderObjects.messageChangedListener != null) {
                imapFolder.removeMessageChangedListener(folderObjects.messageChangedListener);
            }
            ScheduledFuture<?> standbyTask = folderObjects.standbyTask;
            if (standbyTask != null && !standbyTask.isCancelled() && !standbyTask.isDone()) {
                standbyTask.cancel(false);
            }

            Future<?> listenTask = folderObjects.listenTask;
            if (listenTask != null && !listenTask.isCancelled() && !listenTask.isDone()) {
                listenTask.cancel(true);
            }
        }
    }

    public void subscribe(@Nonnull ImapFolder folder) {
        UUID folderId = folder.getId();
        log.info("subscribe count listener for folder#{}", folderId);

        synchronized (lock) {
            if (folders.get(folderId) != null) {
                log.debug("there is already count listener for folder#{}", folderId);
                return;
            }

            FolderObjects folderObjects = new FolderObjects(folder);
            folders.putIfAbsent(folderId, folderObjects);
            folderObjects.listen();
        }

    }

    private MessageCountListener makeListener(ImapFolder folder) {
        return new MessageCountListener() {
            @Override
            public void messagesAdded(MessageCountEvent e) {
                if (e.getMessages().length > 0) {
                    IMAPFolder imapFolder = (IMAPFolder) e.getSource();
                    imapFolder.isOpen();
                    executor.submit(() -> imapEvents.handleNewMessages(folder, e.getMessages()));
                }

            }

            @Override
            public void messagesRemoved(MessageCountEvent e) {
                if (e.getMessages().length > 0) {
                    IMAPFolder imapFolder = (IMAPFolder) e.getSource();
                    imapFolder.isOpen();
                    executor.submit(() -> imapEvents.handleMissedMessages(folder, e.getMessages()));
                }
            }
        };
    }

    private class FolderObjects {
        private ImapFolder cubaFolder;
        private IMAPFolder imapFolder;
        private MessageCountListener countListener;
        private MessageChangedListener messageChangedListener;
        private Future<?> listenTask;
        private ScheduledFuture<?> standbyTask;

        FolderObjects(ImapFolder cubaFolder) {
            this.cubaFolder = cubaFolder;
            this.countListener = makeListener(cubaFolder);
            this.messageChangedListener = e ->
                    imapEvents.handleChangedMessages(this.cubaFolder, new Message[] { e.getMessage() });
        }

        void listen() {
            this.listenTask = executor.submit(() -> {
                try {
                    this.imapFolder = imapHelper.getExclusiveFolder(cubaFolder);
                    this.imapFolder.addMessageCountListener(this.countListener);
                    this.imapFolder.addMessageChangedListener(this.messageChangedListener);
                    this.standbyTask = scheduledExecutorService.schedule((Runnable) imapFolder::isOpen, 2, TimeUnit.MINUTES);
                    this.imapFolder.idle();
                } catch (MessagingException e) {
                    throw new ImapException(e);
                } finally {
                    if (this.imapFolder != null) {
                        this.imapFolder.removeMessageCountListener(this.countListener);
                    }
                    if (this.imapFolder != null) {
                        this.imapFolder.removeMessageChangedListener(this.messageChangedListener);
                    }
                    if (!standbyTask.isDone() && !standbyTask.isCancelled()) {
                        standbyTask.cancel(false);
                    }
                    synchronized (lock) {
                        folders.remove(cubaFolder.getId());
                    }
                    subscribe(cubaFolder);
                }
            });
        }
    }

    @PreDestroy
    public void shutdownExecutor() {
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

}
