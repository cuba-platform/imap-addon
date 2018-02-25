package com.haulmont.components.imap.scheduling;

import com.haulmont.components.imap.config.ImapConfig;
import com.haulmont.components.imap.core.ImapHelper;
import com.haulmont.components.imap.entity.ImapMessageRef;
import com.haulmont.components.imap.entity.MailBox;
import com.haulmont.components.imap.entity.PredefinedEventType;
import com.haulmont.components.imap.entity.MailFolder;
import com.haulmont.components.imap.events.*;
import com.haulmont.cuba.core.EntityManager;
import com.haulmont.cuba.core.Persistence;
import com.haulmont.cuba.core.Transaction;
import com.haulmont.cuba.core.TypedQuery;
import com.haulmont.cuba.core.global.Events;
import com.haulmont.cuba.core.global.Metadata;
import com.haulmont.cuba.core.global.TimeSource;
import com.haulmont.cuba.security.app.Authentication;
import com.sun.mail.imap.IMAPFolder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import javax.inject.Inject;
import javax.mail.*;
import javax.mail.search.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;

@Component(ImapSchedulingAPI.NAME)
public class ImapScheduling implements ImapSchedulingAPI {

    private final static Logger log = LoggerFactory.getLogger(ImapScheduling.class);

    @Inject
    private Persistence persistence;

    @Inject
    private TimeSource timeSource;

    @Inject
    private Events events;

    @Inject
    private Authentication authentication;

    @Inject
    private ImapHelper imapHelper;

    @Inject
    private ImapConfig config;

    @Inject
    private Metadata metadata;

    protected ForkJoinPool forkJoinPool = new ForkJoinPool();

    protected ConcurrentMap<MailBox, Long> runningTasks = new ConcurrentHashMap<>();

    protected Map<MailBox, Long> lastStartCache = new ConcurrentHashMap<>();

    protected Map<MailBox, Long> lastFinishCache = new ConcurrentHashMap<>();

    @Override
    public void processMailBoxes() {
        authentication.begin();
        try (Transaction tx = persistence.createTransaction()) {
            EntityManager em = persistence.getEntityManager();
            TypedQuery<MailBox> query = em.createQuery(
                    "select distinct b from mailcomponent$MailBox b " +
                            "left join fetch b.rootCertificate " +
                            "join fetch b.authentication " +
                            "left join fetch b.folders",
                    MailBox.class
            );
            query.getResultList().stream().flatMap(mb -> mb.getFolders().stream()).forEach(f -> f.getEvents().size());
            query.getResultList().stream().flatMap(mb -> mb.getFolders().stream()).forEach(f -> f.getMailBox().getPollInterval());
            query.getResultList().forEach(this::processMailBox);
        } finally {
            authentication.end();
        }
    }

    private void processMailBox(MailBox mailBox) {
        if (isRunning(mailBox)) {
            log.trace("{} is running", mailBox);
            return;
        }

        long now = timeSource.currentTimeMillis();

        Long lastStart = lastStartCache.getOrDefault(mailBox, 0L);
        Long lastFinish = lastFinishCache.getOrDefault(mailBox, 0L);

        log.trace("{}\n now={} lastStart={} lastFinish={}", mailBox, now, lastStart, lastFinish);
        if ((lastStart == 0 || lastStart < lastFinish) && now >= lastFinish + mailBox.getPollInterval() * 1000L) {
            lastStartCache.put(mailBox, now);
            forkJoinPool.execute(new MailBoxProcessingTask(mailBox));
        } else {
            log.trace("{}\n time has not come", mailBox);
        }
    }

    private class MailBoxProcessingTask extends RecursiveAction {

        private final MailBox mailBox;

        MailBoxProcessingTask(MailBox mailBox) {
            this.mailBox = mailBox;
        }

        @Override
        protected void compute() {
            log.debug("{}: running", mailBox);
            Store store = null;
            try {
                store = imapHelper.getStore(mailBox);
                List<MailFolder> listenedFolders = mailBox.getFolders().stream()
                        .filter(f -> f.getEvents() != null && !f.getEvents().isEmpty())
                        .collect(Collectors.toList());
                List<RecursiveAction> folderSubtasks = new ArrayList<>(listenedFolders.size() * 2);
                for (MailFolder cubaFolder : mailBox.getFolders()) {
                    IMAPFolder imapFolder = (IMAPFolder) store.getFolder(cubaFolder.getName());
                    if (cubaFolder.hasEvent(PredefinedEventType.NEW_EMAIL)) {
                        NewMessagesInFolderTask subtask = new NewMessagesInFolderTask(mailBox, cubaFolder, imapFolder);
                        folderSubtasks.add(subtask);
                        subtask.fork();
                    }
                    if (cubaFolder.hasEvent(PredefinedEventType.EMAIL_DELETED) ||
                            cubaFolder.hasEvent(PredefinedEventType.EMAIL_SEEN) ||
                            cubaFolder.hasEvent(PredefinedEventType.FLAGS_UPDATED) ||
                            cubaFolder.hasEvent(PredefinedEventType.NEW_ANSWER) ||
                            cubaFolder.hasEvent(PredefinedEventType.NEW_THREAD)) {

                        UpdateMessagesInFolderTask updateSubtask = new UpdateMessagesInFolderTask(mailBox, cubaFolder, imapFolder);
                        folderSubtasks.add(updateSubtask);
                        updateSubtask.fork();
                    }
                }
                folderSubtasks.forEach(ForkJoinTask::join);
            } catch (Exception e) {
                throw new RuntimeException(
                        String.format("error processing mailbox %s:%d", mailBox.getHost(), mailBox.getPort()), e
                );
            } finally {
                if (store != null) {
                    try {
                        store.close();
                    } catch (MessagingException e) {
                        log.warn("Can't close store for mailBox {}:{}", mailBox.getHost(), mailBox.getPort());
                    }

                }
                lastFinishCache.put(mailBox, timeSource.currentTimeMillis());
            }
        }
    }

    @SuppressWarnings("unchecked")
    private class UpdateMessagesInFolderTask extends RecursiveAction {
        private final MailFolder cubaFolder;
        private final MailBox mailBox;
        private final IMAPFolder folder;

        public UpdateMessagesInFolderTask(MailBox mailBox, MailFolder cubaFolder, IMAPFolder folder) {
            this.mailBox = mailBox;
            this.cubaFolder = cubaFolder;
            this.folder = folder;
        }

        @Override
        protected void compute() {
            int batchSize = config.getUpdateBatchSize();
            long windowSize = Math.min(
                    getCount(),
                    (mailBox.getUpdateSliceSize() != null) ? Math.max(mailBox.getUpdateSliceSize(), batchSize) : batchSize
            );

            List<BaseImapEvent> imapEvents = imapHelper.doWithFolder(
                    mailBox,
                    folder,
                    new ImapHelper.FolderTask<>(
                            "updating messages",
                            true,
                            true,
                            f -> {
                                if (imapHelper.canHoldMessages(folder)) {
                                    List<BaseImapEvent> modificationEvents = new ArrayList<>((int) windowSize);
                                    for (int i = 0; i < windowSize; i += batchSize) {
                                        modificationEvents.addAll(updateMessages((int) Math.min(batchSize, windowSize - i)));
                                    }

                                    return modificationEvents;
                                }

                                return Collections.emptyList();
                            })
            );
            imapEvents.forEach(events::publish);

        }

        private long getCount() {
            authentication.begin();
            try (Transaction tx = persistence.createTransaction()) {
                EntityManager em = persistence.getEntityManager();
                return ((Number) em.createQuery("select count(m.id) from mailcomponent$ImapMessageRef m where m.folder.id = :mailFolderId")
                        .setParameter("mailFolderId", cubaFolder)
                        .getSingleResult()).longValue();
            } finally {
                authentication.end();
            }
        }

        private List<BaseImapEvent> updateMessages(int count) throws MessagingException {
            authentication.begin();
            try (Transaction tx = persistence.createTransaction()) {
                EntityManager em = persistence.getEntityManager();

                List<ImapMessageRef> messages = em.createQuery(
                        "select m from mailcomponent$ImapMessageRef m where m.folder.id = :mailFolderId order by m.updatedTs asc nulls first",
                        ImapMessageRef.class
                )
                        .setParameter("mailFolderId", cubaFolder)
                        .setMaxResults(count)
                        .setViewName("msg-ref-full")
                        .getResultList();

                List<ImapHelper.MsgHeader> imapMessages = imapHelper.getAllByUids(
                        folder, messages.stream().mapToLong(ImapMessageRef::getMsgUid).toArray()
                );
                Map<Long, ImapHelper.MsgHeader> headersByUid = new HashMap<>(imapMessages.size());
                for (ImapHelper.MsgHeader msg : imapMessages) {
                    headersByUid.put(msg.getUid(), msg);
                }

                List<BaseImapEvent> modificationEvents = messages.stream().flatMap(msg -> updateMessage(em, msg, headersByUid).stream()).collect(Collectors.toList());

                tx.commit();
                return modificationEvents;
            } finally {
                authentication.end();
            }

        }

        private List<BaseImapEvent> updateMessage(EntityManager em, ImapMessageRef msgRef, Map<Long, ImapHelper.MsgHeader> msgsByUid) {
            ImapHelper.MsgHeader newMsgHeader = msgsByUid.get(msgRef.getMsgUid());
            if (newMsgHeader == null) {
                em.remove(msgRef);
                return cubaFolder.hasEvent(PredefinedEventType.EMAIL_DELETED)
                        ? Collections.singletonList(new EmailDeletedEvent(msgRef)) : Collections.emptyList();
            }
            Flags flags = newMsgHeader.getFlags();

            List<BaseImapEvent> modificationEvents = new ArrayList<>(3);
            boolean oldSeen = Boolean.TRUE.equals(msgRef.getSeen());
            boolean newSeen = flags.contains(Flags.Flag.SEEN);
            boolean oldDeleted = Boolean.TRUE.equals(msgRef.getDeleted());
            boolean newDeleted = flags.contains(Flags.Flag.DELETED);
            boolean oldFlagged = Boolean.TRUE.equals(msgRef.getFlagged());
            boolean newFlagged = flags.contains(Flags.Flag.FLAGGED);
            boolean oldAnswered = Boolean.TRUE.equals(msgRef.getAnswered());
            boolean newAnswered = flags.contains(Flags.Flag.ANSWERED);
            String oldRefId = msgRef.getReferenceId();
            String newRefId = newMsgHeader.getRefId();

            if (oldSeen != newSeen || oldDeleted != newDeleted || oldAnswered != newAnswered || oldFlagged != newFlagged || !Objects.equals(oldRefId, newRefId)) {
                HashMap<String, Boolean> changedFlagsWithNewValue = new HashMap<>();
                if (oldSeen != newSeen) {
                    changedFlagsWithNewValue.put("SEEN", newSeen);
                    if (newSeen && cubaFolder.hasEvent(PredefinedEventType.EMAIL_SEEN)) {
                        modificationEvents.add(new EmailSeenEvent(msgRef));
                    }
                }

                if (oldAnswered != newAnswered || !Objects.equals(oldRefId, newRefId)) {
                    changedFlagsWithNewValue.put("ANSWERED", newAnswered);
                    if (newAnswered || newRefId != null) {
                        modificationEvents.add(new EmailAnsweredEvent(msgRef));
                    }
                }

                if (oldDeleted != newDeleted) {
                    changedFlagsWithNewValue.put("DELETED", newDeleted);
                }
                if (oldFlagged != newFlagged) {
                    changedFlagsWithNewValue.put("FLAGGED", newFlagged);
                }
                if (cubaFolder.hasEvent(PredefinedEventType.FLAGS_UPDATED)) {
                    modificationEvents.add(new EmailFlagChangedEvent(msgRef, changedFlagsWithNewValue));
                }
                msgRef.setSeen(newSeen);
                msgRef.setDeleted(newDeleted);
                msgRef.setAnswered(newAnswered);
                msgRef.setFlagged(newFlagged);
                msgRef.setReferenceId(newRefId);
            }
            msgRef.setThreadId(newMsgHeader.getThreadId());  // todo: fire thread event
            msgRef.setUpdatedTs(new Date());
            em.persist(msgRef);

            return modificationEvents;
        }
    }

    private class NewMessagesInFolderTask extends RecursiveAction {

        private final MailFolder cubaFolder;
        private final MailBox mailBox;
        private final IMAPFolder folder;

        public NewMessagesInFolderTask(MailBox mailBox, MailFolder cubaFolder, IMAPFolder folder) {
            this.cubaFolder = cubaFolder;
            this.mailBox = mailBox;
            this.folder = folder;
        }

        @Override
        protected void compute() {
            List<NewEmailEvent> imapEvents = imapHelper.doWithFolder(
                    mailBox,
                    folder,
                    new ImapHelper.FolderTask<>(
                            "get new messages",
                            true,
                            true,
                            f -> {
                                if (imapHelper.canHoldMessages(folder)) {
                                    List<ImapHelper.MsgHeader> imapMessages = imapHelper.search(
                                            folder, new NotTerm(new FlagTerm(cubaFlags(mailBox), true))
                                    );

                                    List<NewEmailEvent> newEmailEvents = saveNewMessages(imapMessages);

                                    Message[] messages = folder.getMessagesByUID(newEmailEvents.stream().mapToLong(NewEmailEvent::getMessageId).toArray());
                                    folder.setFlags(messages, cubaFlags(mailBox), true);
                                    return newEmailEvents;
                                }

                                return Collections.emptyList();
                            })
            );
            imapEvents.forEach(events::publish);


        }

        private List<NewEmailEvent> saveNewMessages(List<ImapHelper.MsgHeader> imapMessages) {
            List<NewEmailEvent> newEmailEvents = new ArrayList<>(imapMessages.size());
            boolean toCommit = false;
            authentication.begin();
            try (Transaction tx = persistence.createTransaction()) {
                EntityManager em = persistence.getEntityManager();

                for (ImapHelper.MsgHeader msg : imapMessages) {
                    ImapMessageRef newMessage = insertNewMessage(em, msg);
                    toCommit |= (newMessage != null);

                    if (newMessage != null) {
                        newEmailEvents.add(new NewEmailEvent(newMessage));
                    }
                }
                if (toCommit) {
                    tx.commit();
                }

            } finally {
                authentication.end();
            }
            return newEmailEvents;
        }

        private ImapMessageRef insertNewMessage(EntityManager em, ImapHelper.MsgHeader msg) {
            long uid = msg.getUid();
            Flags flags = msg.getFlags();
            String caption = msg.getCaption();
            String refId = msg.getRefId();
            Long threadId = msg.getThreadId();

            int sameUids = em.createQuery(
                    "select m from mailcomponent$ImapMessageRef m where m.msgUid = :uid and m.folder.id = :mailFolderId"
            )
                    .setParameter("uid", uid)
                    .setParameter("mailFolderId", cubaFolder)
                    .setMaxResults(1)
                    .getResultList()
                    .size();
            if (sameUids == 0) {
                ImapMessageRef entity = metadata.create(ImapMessageRef.class);
                entity.setMsgUid(uid);
                entity.setFolder(cubaFolder);
                entity.setUpdatedTs(new Date());
                entity.setAnswered(flags.contains(Flags.Flag.ANSWERED));
                entity.setDeleted(flags.contains(Flags.Flag.DELETED));
                entity.setFlagged(flags.contains(Flags.Flag.FLAGGED));
                entity.setSeen(flags.contains(Flags.Flag.SEEN));
                entity.setCaption(caption);
                entity.setReferenceId(refId);
                entity.setThreadId(threadId);
                em.persist(entity);
                return entity;
            }
            return null;
        }
    }

    private Flags cubaFlags(MailBox mailBox) {
        Flags cubaFlags = new Flags();
        cubaFlags.add(mailBox.getCubaFlag());
        return cubaFlags;
    }

    private boolean isRunning(MailBox mailBox) {
        Long startTime = runningTasks.get(mailBox);
        if (startTime != null) {
            boolean timedOut = startTime + mailBox.getProcessingTimeout() * 1000 > timeSource.currentTimeMillis();
            if (timedOut) {
                runningTasks.remove(mailBox);
            } else {
                return true;
            }
        }
        return false;
    }
}
