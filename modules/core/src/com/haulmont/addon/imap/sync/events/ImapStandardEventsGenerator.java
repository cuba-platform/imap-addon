package com.haulmont.addon.imap.sync.events;

import com.haulmont.addon.imap.api.ImapFlag;
import com.haulmont.addon.imap.dao.ImapMessageSyncDao;
import com.haulmont.addon.imap.entity.*;
import com.haulmont.addon.imap.events.*;
import com.haulmont.addon.imap.sync.ImapSynchronizer;
import com.haulmont.cuba.core.EntityManager;
import com.haulmont.cuba.core.Persistence;
import com.haulmont.cuba.core.Query;
import com.haulmont.cuba.core.Transaction;
import com.haulmont.cuba.security.app.Authentication;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import javax.annotation.Nonnull;
import javax.annotation.PreDestroy;
import javax.inject.Inject;
import javax.mail.Flags;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

@Component(ImapStandardEventsGenerator.NAME)
public class ImapStandardEventsGenerator extends ImapEventsBatchedGenerator {

    private final static Logger log = LoggerFactory.getLogger(ImapStandardEventsGenerator.class);

    public static final String NAME = "imap_StandardEventsGenerator";

    private final ImapMessageSyncDao messageSyncDao;
    private final Authentication authentication;
    private final Persistence persistence;
    private final ImapSynchronizer imapSynchronizer;

    private final ScheduledExecutorService scheduledExecutorService = Executors.newScheduledThreadPool(10, new ThreadFactory() {
        private final AtomicInteger threadNumber = new AtomicInteger(1);

        @Override
        public Thread newThread(@Nonnull Runnable r) {
            Thread thread = new Thread(
                    r, "ImapMailBoxFolderSynchronizationThread-" + threadNumber.getAndIncrement()
            );
            thread.setDaemon(true);
            return thread;
        }
    });

    private final ConcurrentMap<UUID, ScheduledFuture<?>> syncRefreshers = new ConcurrentHashMap<>();

    @SuppressWarnings("CdiInjectionPointsInspection")
    @Inject
    public ImapStandardEventsGenerator(ImapMessageSyncDao messageSyncDao,
                                       Authentication authentication,
                                       Persistence persistence,
                                       ImapSynchronizer imapSynchronizer) {
        super(20); //todo: to config
        this.messageSyncDao = messageSyncDao;
        this.authentication = authentication;
        this.persistence = persistence;
        this.imapSynchronizer = imapSynchronizer;
    }

    @Override
    public void init(ImapFolder cubaFolder) {
        imapSynchronizer.synchronize(cubaFolder);
        syncRefreshers.put(
                cubaFolder.getId(),
                scheduledExecutorService.scheduleWithFixedDelay(
                        () -> {
                            try {
                                imapSynchronizer.synchronize(cubaFolder);
                            } catch (Exception e) {
                                log.error("Syncronization of folder " + cubaFolder.getName() + " failed with error", e);
                            }
                        },
                        30, 30, TimeUnit.SECONDS)
        );
    }

    @Override
    public void shutdown(ImapFolder cubaFolder) {
        ScheduledFuture<?> task = syncRefreshers.remove(cubaFolder.getId());
        if (task != null) {
            if (!task.isDone() && !task.isCancelled()) {
                task.cancel(false);
            }
        }
    }

    @PreDestroy
    public void shutdown() {
        for (Map.Entry<UUID, ScheduledFuture<?>> taskWithId : syncRefreshers.entrySet()) {
            ScheduledFuture<?> task = taskWithId.getValue();
            if (task != null) {
                if (!task.isDone() && !task.isCancelled()) {
                    try {
                        task.cancel(false);
                    } catch (Exception e) {
                        log.warn("Exception while shutting down synchronizer for folder " + taskWithId.getKey(), e);
                    }
                }
            }
        }

        try {
            scheduledExecutorService.shutdownNow();
        } catch (Exception e) {
            log.warn("Exception while shutting down scheduled executor", e);
        }
    }

    @Override
    public Collection<? extends BaseImapEvent> generateForNewMessages(ImapFolder cubaFolder, int batchSize) {
        authentication.begin();
        try {
            Collection<ImapMessage> newMessages = messageSyncDao.findMessagesWithSyncStatus(
                    cubaFolder.getId(), ImapSyncStatus.ADDED, batchSize);

            Collection<BaseImapEvent> newMessageEvents = newMessages.stream()
                    .map(NewEmailImapEvent::new)
                    .collect(Collectors.toList());

            messageSyncDao.removeMessagesSyncs(newMessages.stream().map(ImapMessage::getId).collect(Collectors.toList()));

            return newMessageEvents;
        } catch (Exception e) {
            log.error("New messages events for " + cubaFolder.getName() + " failure", e);
            return Collections.emptyList();
        } finally {
            authentication.end();
        }
    }

    @Override
    public Collection<? extends BaseImapEvent> generateForChangedMessages(ImapFolder cubaFolder, int batchSize) {
        authentication.begin();
        try {
            Collection<ImapMessageSync> remainMessageSyncs = messageSyncDao.findMessagesSyncs(
                    cubaFolder.getId(), ImapSyncStatus.REMAIN, batchSize);

            Collection<BaseImapEvent> updateMessageEvents = remainMessageSyncs.stream()
                    .flatMap(messageSync -> generateUpdateEvents(messageSync).stream())
                    .collect(Collectors.toList());

            messageSyncDao.removeMessagesSyncs(remainMessageSyncs.stream()
                    .map(ms -> ms.getMessage().getId())
                    .distinct()
                    .collect(Collectors.toList()));

            return updateMessageEvents;
        } catch (Exception e) {
            log.error("Changed messages events for " + cubaFolder.getName() + " failure", e);
            return Collections.emptyList();
        } finally {
            authentication.end();
        }

    }

    private List<BaseImapEvent> generateUpdateEvents(ImapMessageSync messageSync) {
        Flags newFlags = messageSync.getImapFlags();
        ImapMessage msg = messageSync.getMessage();
        Flags oldFlags = msg.getImapFlags();

        List<BaseImapEvent> modificationEvents = new ArrayList<>(3);
        if (!Objects.equals(newFlags, oldFlags)) {
            log.trace("Update message {}. Old flags: {}, new flags: {}", msg, oldFlags, newFlags);

            HashMap<ImapFlag, Boolean> changedFlagsWithNewValue = new HashMap<>();
            if (isSeen(newFlags, oldFlags)) {
                modificationEvents.add(new EmailSeenImapEvent(msg));
            }

            if (isAnswered(newFlags, oldFlags)) {
                modificationEvents.add(new EmailAnsweredImapEvent(msg));
            }

            for (String userFlag : oldFlags.getUserFlags()) {
                if (!newFlags.contains(userFlag)) {
                    changedFlagsWithNewValue.put(new ImapFlag(userFlag), false);
                }
            }

            for (Flags.Flag systemFlag : oldFlags.getSystemFlags()) {
                if (!newFlags.contains(systemFlag)) {
                    changedFlagsWithNewValue.put(new ImapFlag(ImapFlag.SystemFlag.valueOf(systemFlag)), false);
                }
            }

            for (String userFlag : newFlags.getUserFlags()) {
                if (!oldFlags.contains(userFlag)) {
                    changedFlagsWithNewValue.put(new ImapFlag(userFlag), true);
                }
            }

            for (Flags.Flag systemFlag : newFlags.getSystemFlags()) {
                if (!oldFlags.contains(systemFlag)) {
                    changedFlagsWithNewValue.put(new ImapFlag(ImapFlag.SystemFlag.valueOf(systemFlag)), true);
                }
            }

            modificationEvents.add(new EmailFlagChangedImapEvent(msg, changedFlagsWithNewValue));
            msg.setImapFlags(newFlags);
            msg.setUpdateTs(new Date());
//            msg.setThreadId();
            authentication.begin();
            try (Transaction tx = persistence.createTransaction()) {
                EntityManager em = persistence.getEntityManager();
                em.merge(msg);

                tx.commit();
            } finally {
                authentication.end();
            }

        }
        return modificationEvents;
    }

    private boolean isSeen(Flags newFlags, Flags oldFlags) {
        return !oldFlags.contains(Flags.Flag.SEEN)
                && newFlags.contains(Flags.Flag.SEEN);
    }

    private boolean isAnswered(Flags newFlags, Flags oldFlags) {
        return !oldFlags.contains(Flags.Flag.ANSWERED)
                && newFlags.contains(Flags.Flag.ANSWERED);
    }

    @Override
    public Collection<? extends BaseImapEvent> generateForMissedMessages(ImapFolder cubaFolder, int batchSize) {
        authentication.begin();
        try {
            Collection<ImapMessage> removed = messageSyncDao.findMessagesWithSyncStatus(
                    cubaFolder.getId(), ImapSyncStatus.REMOVED, batchSize);
            Collection<ImapMessageSync> moved = messageSyncDao.findMessagesSyncs(
                    cubaFolder.getId(), ImapSyncStatus.MOVED, batchSize);

            Collection<BaseImapEvent> missedMessageEvents = new ArrayList<>(removed.size() + moved.size());
            List<Integer> missedMessageNums = new ArrayList<>(removed.size() + moved.size());
            for (ImapMessage imapMessage : removed) {
                missedMessageEvents.add(new EmailDeletedImapEvent(imapMessage));
                missedMessageNums.add(imapMessage.getMsgNum());
            }
            for (ImapMessageSync imapMessageSync : moved) {
                missedMessageEvents.add(new EmailMovedImapEvent(imapMessageSync.getMessage(), imapMessageSync.getNewFolderName()));
                missedMessageNums.add(imapMessageSync.getMessage().getMsgNum());
            }

            try (Transaction tx = persistence.createTransaction()) {
                EntityManager em = persistence.getEntityManager();
                for (BaseImapEvent missedMessageEvent : missedMessageEvents) {
                    em.remove(missedMessageEvent.getMessage());
                }
                tx.commit();
            }

            recalculateMessageNumbers(cubaFolder, missedMessageNums);


        /*messageSyncDao.removeMessagesSyncs(missedMessageEvents.stream()
                .map(event -> event.getMessage().getId())
                .collect(Collectors.toList()));*/

            return missedMessageEvents;
        } catch (Exception e) {
            log.error("Missed messages events for " + cubaFolder.getName() + " failure", e);
            return Collections.emptyList();
        } finally {
            authentication.end();
        }
    }

    private void recalculateMessageNumbers(ImapFolder cubaFolder, List<Integer> messageNumbers) {
        messageNumbers.sort(Comparator.naturalOrder());
        authentication.begin();
        try (Transaction tx = persistence.createTransaction()) {
            EntityManager em = persistence.getEntityManager();
            for (int i = 0; i < messageNumbers.size(); i++) {
                String queryString = "update imap$Message m set m.msgNum = m.msgNum-" + (i + 1) +
                        " where m.folder.id = :mailFolderId and m.msgNum > :msgNum";
                if (i < messageNumbers.size() - 1) {
                    queryString += " and m.msgNum < :topMsgNum";
                }
                Query query = em.createQuery(queryString)
                        .setParameter("mailFolderId", cubaFolder)
                        .setParameter("msgNum", messageNumbers.get(i));
                if (i < messageNumbers.size() - 1) {
                    query.setParameter("topMsgNum", messageNumbers.get(i + 1));
                }
                query.executeUpdate();
            }
            tx.commit();
        } finally {
            authentication.end();
        }
    }
}
