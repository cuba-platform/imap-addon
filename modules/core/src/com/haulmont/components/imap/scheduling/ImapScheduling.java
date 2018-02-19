package com.haulmont.components.imap.scheduling;

import com.haulmont.bali.datastruct.Pair;
import com.haulmont.components.imap.config.ImapConfig;
import com.haulmont.components.imap.core.ImapHelper;
import com.haulmont.components.imap.dto.MessageRef;
import com.haulmont.components.imap.entity.MailBox;
import com.haulmont.components.imap.entity.MailMessageMeta;
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
                List<String> listenedFolders = mailBox.getFolders().stream()
                        .filter(f -> f.hasEvent(PredefinedEventType.NEW_EMAIL))
                        .map(MailFolder::getName)
                        .collect(Collectors.toList());
                List<RecursiveAction> folderSubtasks = new ArrayList<>(listenedFolders.size() * 2);
                for (String folderName : listenedFolders) {
                    IMAPFolder folder = (IMAPFolder) store.getFolder(folderName);
                    NewMessagesInFolderTask subtask = new NewMessagesInFolderTask(mailBox, folder);
                    UpdateMessagesInFolderTask updateSubtask = new UpdateMessagesInFolderTask(mailBox, folder);
                    folderSubtasks.add(subtask);
                    folderSubtasks.add(updateSubtask);
                    subtask.fork();
                    updateSubtask.fork();
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
        private final MailBox mailBox;
        private final IMAPFolder folder;

        public UpdateMessagesInFolderTask(MailBox mailBox, IMAPFolder folder) {
            this.mailBox = mailBox;
            this.folder = folder;
        }

        @Override
        protected void compute() {
            int batchSize = config.getUpdateBatchSize();
            long windowSize = Math.min(
                    getCount(),
                    (mailBox.getUpdateSliceSize() != null) ? Math.max(mailBox.getUpdateSliceSize(), batchSize) : batchSize
            );

            imapHelper.doWithFolder(
                    mailBox,
                    folder,
                    new ImapHelper.FolderTask<>(
                            "updating messages",
                            false,
                            true,
                            f -> {
                                if (imapHelper.canHoldMessages(folder)) {
                                    for (int i = 0; i < windowSize; i += batchSize) {
                                        List<BaseImapEvent> modificationEvents = updateMessages((int) Math.min(batchSize, windowSize - i));
                                        modificationEvents.forEach(events::publish);
                                    }
                                }
                                if (imapHelper.canHoldFolders(folder)) {
                                    List<UpdateMessagesInFolderTask> subTasks = new LinkedList<>();

                                    for (Folder childFolder : folder.list()) {
                                        UpdateMessagesInFolderTask childFolderTask = new UpdateMessagesInFolderTask(mailBox, (IMAPFolder) childFolder);
                                        subTasks.add(childFolderTask);
                                        childFolderTask.fork();
                                    }

                                    subTasks.forEach(ForkJoinTask::join);
                                }

                                return null;
                            })
            );
        }

        private long getCount() {
            authentication.begin();
            try (Transaction tx = persistence.createTransaction()) {
                EntityManager em = persistence.getEntityManager();
                return ((Number) em.createQuery("select count(m.id) from mailcomponent$MailMessageMeta m where m.mailBox.id = :mailBoxId")
                        .setParameter("mailBoxId", mailBox)
                        .getSingleResult()).longValue();
            } finally {
                authentication.end();
            }
        }

        private List<BaseImapEvent> updateMessages(int count) throws MessagingException {
            authentication.begin();
            try (Transaction tx = persistence.createTransaction()) {
                EntityManager em = persistence.getEntityManager();

                List<MailMessageMeta> messages = em.createQuery(
                        "select m from mailcomponent$MailMessageMeta m where m.mailBox.id = :mailBoxId order by m.updatedTs asc nulls first",
                        MailMessageMeta.class
                )
                        .setParameter("mailBoxId", mailBox)
                        .setMaxResults(count)
                        .getResultList();

                List<Pair<Long, Flags>> imapMessages = (List<Pair<Long, Flags>>) folder.doCommand(
                        imapHelper.uidFetchWithFlagsCommand( messages.stream().mapToLong(MailMessageMeta::getMsgUid).toArray() )
                );
                Map<Long, Flags> flagsByUid = imapMessages.stream().collect(Collectors.toMap(Pair::getFirst, Pair::getSecond));

                List<BaseImapEvent> modificationEvents = messages.stream().flatMap(msg -> updateMessage(em, msg, flagsByUid).stream()).collect(Collectors.toList());

                tx.commit();
                return modificationEvents;
            } finally {
                authentication.end();
            }

        }

        private List<BaseImapEvent> updateMessage(EntityManager em, MailMessageMeta msg, Map<Long, Flags> flagsByUid) {
            MessageRef messageRef = new MessageRef(mailBox, folder.getFullName(), msg.getMsgUid());
            Flags flags = flagsByUid.get(msg.getMsgUid());
            if (flags == null) {
                em.remove(msg);
                return Collections.singletonList(new EmailDeletedEvent(messageRef));
            }

            List<BaseImapEvent> modificationEvents = new ArrayList<>(3);
            boolean oldSeen = Boolean.TRUE.equals(msg.getSeen());
            boolean newSeen = flags.contains(Flags.Flag.SEEN);
            boolean oldDeleted = Boolean.TRUE.equals(msg.getDeleted());
            boolean newDeleted = flags.contains(Flags.Flag.DELETED);
            boolean oldAnswered = Boolean.TRUE.equals(msg.getAnswered());
            boolean newAnswered = flags.contains(Flags.Flag.ANSWERED);
            boolean oldFlagged = Boolean.TRUE.equals(msg.getFlagged());
            boolean newFlagged = flags.contains(Flags.Flag.FLAGGED);

            if (oldSeen != newSeen || oldDeleted != newDeleted || oldAnswered != newAnswered || oldFlagged != newFlagged) {
                HashMap<String, Boolean> changedFlagsWithNewValue = new HashMap<>();
                if (oldSeen != newSeen) {
                    changedFlagsWithNewValue.put("SEEN", newSeen);
                    if (newSeen) {
                        modificationEvents.add(new EmailSeenEvent(messageRef));
                    }
                }

                if (oldAnswered != newAnswered) {
                    changedFlagsWithNewValue.put("ANSWERED", newAnswered);
                    if (newAnswered) {
                        modificationEvents.add(new EmailAnsweredEvent(messageRef));
                    }
                }

                if (oldDeleted != newDeleted) {
                    changedFlagsWithNewValue.put("DELETED", newDeleted);
                }
                if (oldFlagged != newFlagged) {
                    changedFlagsWithNewValue.put("FLAGGED", newFlagged);
                }
                modificationEvents.add(new EmailFlagChangedEvent(messageRef, changedFlagsWithNewValue));
                msg.setSeen(newSeen);
                msg.setDeleted(newDeleted);
                msg.setAnswered(newAnswered);
                msg.setFlagged(newFlagged);
            }
            msg.setUpdatedTs(new Date());
            em.persist(msg);

            return modificationEvents;
        }
    }

    private class NewMessagesInFolderTask extends RecursiveAction {

        private final MailBox mailBox;
        private final IMAPFolder folder;

        public NewMessagesInFolderTask(MailBox mailBox, IMAPFolder folder) {
            this.mailBox = mailBox;
            this.folder = folder;
        }

        @Override
        protected void compute() {
            imapHelper.doWithFolder(
                    mailBox,
                    folder,
                    new ImapHelper.FolderTask<>(
                            "get new messages",
                            false,
                            true,
                            f -> {
                                if (imapHelper.canHoldMessages(folder)) {
                                    List<Pair<Long, Flags>> uidsAndFlags = imapHelper.searchUidAndFlags(
                                            folder, new NotTerm(new FlagTerm(cubaFlags(mailBox), true))
                                    );

                                    List<NewEmailEvent> newEmailEvents = saveNewMessages(uidsAndFlags);

                                    Message[] messages = folder.getMessagesByUID(uidsAndFlags.stream().mapToLong(Pair::getFirst).toArray());
                                    folder.setFlags(messages, cubaFlags(mailBox), true);
                                    newEmailEvents.forEach(events::publish);
                                }
                                if (imapHelper.canHoldFolders(folder)) {
                                    List<NewMessagesInFolderTask> subTasks = new LinkedList<>();

                                    for (Folder childFolder : folder.list()) {
                                        NewMessagesInFolderTask childFolderTask = new NewMessagesInFolderTask(mailBox, (IMAPFolder) childFolder);
                                        subTasks.add(childFolderTask);
                                        childFolderTask.fork();
                                    }

                                    subTasks.forEach(ForkJoinTask::join);
                                }

                                return null;
                            })
            );

        }

        private List<NewEmailEvent> saveNewMessages(List<Pair<Long, Flags>> uidsAndFlags) {
            List<NewEmailEvent> newEmailEvents = new ArrayList<>(uidsAndFlags.size());
            boolean toCommit = false;
            authentication.begin();
            try (Transaction tx = persistence.createTransaction()) {
                EntityManager em = persistence.getEntityManager();

                for (Pair<Long, Flags> uidAndFlag : uidsAndFlags) {
                    long uid = uidAndFlag.getFirst();
                    Flags flags = uidAndFlag.getSecond();

                    toCommit |= insertNewMessage(em, uid, flags);

                    newEmailEvents.add(new NewEmailEvent(mailBox, folder.getFullName(), uid));
                }
                if (toCommit) {
                    tx.commit();
                }

            } finally {
                authentication.end();
            }
            return newEmailEvents;
        }

        private boolean insertNewMessage(EntityManager em, long uid, Flags flags) {
            int sameUids = em.createQuery(
                    "select m from mailcomponent$MailMessageMeta m where m.msgUid = :uid and m.mailBox.id = :mailBoxId"
            )
                    .setParameter("uid", uid)
                    .setParameter("mailBoxId", mailBox)
                    .setMaxResults(1)
                    .getResultList()
                    .size();
            if (sameUids == 0) {
                MailMessageMeta entity = metadata.create(MailMessageMeta.class);
                entity.setMsgUid(uid);
                entity.setFolderName(folder.getFullName());
                entity.setMailBox(mailBox);
                entity.setUpdatedTs(new Date());
                entity.setAnswered(flags.contains(Flags.Flag.ANSWERED));
                entity.setDeleted(flags.contains(Flags.Flag.DELETED));
                entity.setFlagged(flags.contains(Flags.Flag.FLAGGED));
                entity.setSeen(flags.contains(Flags.Flag.SEEN));
                em.persist(entity);
            }
            return sameUids == 0;
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
