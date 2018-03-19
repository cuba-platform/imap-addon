package com.haulmont.components.imap.api.scheduling;

import com.haulmont.components.imap.config.ImapConfig;
import com.haulmont.components.imap.core.FolderTask;
import com.haulmont.components.imap.core.ImapHelper;
import com.haulmont.components.imap.core.MsgHeader;
import com.haulmont.components.imap.entity.*;
import com.haulmont.components.imap.events.*;
import com.haulmont.cuba.core.EntityManager;
import com.haulmont.cuba.core.Persistence;
import com.haulmont.cuba.core.Transaction;
import com.haulmont.cuba.core.TypedQuery;
import com.haulmont.cuba.core.global.AppBeans;
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
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
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

    protected ConcurrentMap<ImapMailBox, Long> runningTasks = new ConcurrentHashMap<>();

    protected Map<ImapMailBox, Long> lastStartCache = new ConcurrentHashMap<>();

    protected Map<ImapMailBox, Long> lastFinishCache = new ConcurrentHashMap<>();

    @Override
    public void processMailBoxes() {
        authentication.begin();
        try (Transaction tx = persistence.createTransaction()) {
            EntityManager em = persistence.getEntityManager();
            TypedQuery<ImapMailBox> query = em.createQuery(
                    "select distinct b from imapcomponent$ImapMailBox b " +
                            "left join fetch b.rootCertificate " +
                            "join fetch b.authentication " +
                            "left join fetch b.folders",
                    ImapMailBox.class
            );
            query.getResultList().stream().flatMap(mb -> mb.getFolders().stream()).forEach(f -> f.getEvents().size());
            query.getResultList().stream().flatMap(mb -> mb.getFolders().stream()).forEach(f -> f.getMailBox().getPollInterval());
            query.getResultList().forEach(this::processMailBox);
        } finally {
            authentication.end();
        }
    }

    private void processMailBox(ImapMailBox mailBox) {
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

        private final ImapMailBox mailBox;

        MailBoxProcessingTask(ImapMailBox mailBox) {
            this.mailBox = mailBox;
        }

        @Override
        protected void compute() {
            log.debug("{}: running", mailBox);
            Store store = null;
            try {
                store = imapHelper.getStore(mailBox);
                List<ImapFolder> listenedFolders = mailBox.getFolders().stream()
                        .filter(f -> f.getEvents() != null && !f.getEvents().isEmpty())
                        .collect(Collectors.toList());
                List<RecursiveAction> folderSubtasks = new ArrayList<>(listenedFolders.size() * 2);
                for (ImapFolder cubaFolder : mailBox.getFolders()) {
                    IMAPFolder imapFolder = (IMAPFolder) store.getFolder(cubaFolder.getName());
                    if (cubaFolder.hasEvent(ImapEventType.NEW_EMAIL)) {
                        NewMessagesInFolderTask subtask = new NewMessagesInFolderTask(mailBox, cubaFolder, imapFolder);
                        folderSubtasks.add(subtask);
                        subtask.fork();
                    }
                    if (cubaFolder.hasEvent(ImapEventType.EMAIL_DELETED) ||
                            cubaFolder.hasEvent(ImapEventType.EMAIL_SEEN) ||
                            cubaFolder.hasEvent(ImapEventType.FLAGS_UPDATED) ||
                            cubaFolder.hasEvent(ImapEventType.NEW_ANSWER) ||
                            cubaFolder.hasEvent(ImapEventType.NEW_THREAD)) {

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
        private final ImapFolder cubaFolder;
        private final ImapMailBox mailBox;
        private final IMAPFolder folder;

        public UpdateMessagesInFolderTask(ImapMailBox mailBox, ImapFolder cubaFolder, IMAPFolder folder) {
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
                    new FolderTask<>(
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

            fireEvents(cubaFolder, imapEvents);
        }

        private long getCount() {
            authentication.begin();
            try (Transaction tx = persistence.createTransaction()) {
                EntityManager em = persistence.getEntityManager();
                return ((Number) em.createQuery("select count(m.id) from imapcomponent$ImapMessageRef m where m.folder.id = :mailFolderId")
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
                        "select m from imapcomponent$ImapMessageRef m where m.folder.id = :mailFolderId order by m.updatedTs asc nulls first",
                        ImapMessageRef.class
                )
                        .setParameter("mailFolderId", cubaFolder)
                        .setMaxResults(count)
                        .setViewName("imap-msg-ref-full")
                        .getResultList();

                List<MsgHeader> imapMessages = imapHelper.getAllByUids(
                        folder, messages.stream().mapToLong(ImapMessageRef::getMsgUid).toArray()
                );
                Map<Long, MsgHeader> headersByUid = new HashMap<>(imapMessages.size());
                for (MsgHeader msg : imapMessages) {
                    headersByUid.put(msg.getUid(), msg);
                }

                List<BaseImapEvent> modificationEvents = messages.stream().flatMap(msg -> updateMessage(em, msg, headersByUid).stream()).collect(Collectors.toList());

                tx.commit();
                return modificationEvents;
            } finally {
                authentication.end();
            }

        }

        private List<BaseImapEvent> updateMessage(EntityManager em, ImapMessageRef msgRef, Map<Long, MsgHeader> msgsByUid) {
            MsgHeader newMsgHeader = msgsByUid.get(msgRef.getMsgUid());
            if (newMsgHeader == null) {
                em.remove(msgRef);
                return cubaFolder.hasEvent(ImapEventType.EMAIL_DELETED)
                        ? Collections.singletonList(new EmailDeletedImapEvent(msgRef)) : Collections.emptyList();
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

            //todo: handle custom flags

            if (oldSeen != newSeen || oldDeleted != newDeleted || oldAnswered != newAnswered || oldFlagged != newFlagged || !Objects.equals(oldRefId, newRefId)) {
                HashMap<String, Boolean> changedFlagsWithNewValue = new HashMap<>();
                if (oldSeen != newSeen) {
                    changedFlagsWithNewValue.put("SEEN", newSeen);
                    if (newSeen && cubaFolder.hasEvent(ImapEventType.EMAIL_SEEN)) {
                        modificationEvents.add(new EmailSeenImapEvent(msgRef));
                    }
                }

                if (oldAnswered != newAnswered || !Objects.equals(oldRefId, newRefId)) {
                    changedFlagsWithNewValue.put("ANSWERED", newAnswered);
                    if (newAnswered || newRefId != null) {
                        modificationEvents.add(new EmailAnsweredImapEvent(msgRef));
                    }
                }

                if (oldDeleted != newDeleted) {
                    changedFlagsWithNewValue.put("DELETED", newDeleted);
                }
                if (oldFlagged != newFlagged) {
                    changedFlagsWithNewValue.put("FLAGGED", newFlagged);
                }
                if (cubaFolder.hasEvent(ImapEventType.FLAGS_UPDATED)) {
                    modificationEvents.add(new EmailFlagChangedImapEvent(msgRef, changedFlagsWithNewValue));
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

        private final ImapFolder cubaFolder;
        private final ImapMailBox mailBox;
        private final IMAPFolder folder;

        public NewMessagesInFolderTask(ImapMailBox mailBox, ImapFolder cubaFolder, IMAPFolder folder) {
            this.cubaFolder = cubaFolder;
            this.mailBox = mailBox;
            this.folder = folder;
        }

        @Override
        protected void compute() {
            List<NewEmailImapEvent> imapEvents = imapHelper.doWithFolder(
                    mailBox,
                    folder,
                    new FolderTask<>(
                            "get new messages",
                            true,
                            true,
                            f -> {
                                if (imapHelper.canHoldMessages(folder)) {
                                    List<MsgHeader> imapMessages = imapHelper.search(
                                            folder, new NotTerm(new FlagTerm(cubaFlags(mailBox), true))
                                    );

                                    List<NewEmailImapEvent> newEmailImapEvents = saveNewMessages(imapMessages);

                                    Message[] messages = folder.getMessagesByUID(newEmailImapEvents.stream().mapToLong(NewEmailImapEvent::getMessageId).toArray());
                                    //todo: remove this code for unsetting custom flags
                                    for (NewEmailImapEvent event : newEmailImapEvents) {
                                        unsetCustomFlags(event);
                                    }
                                    folder.setFlags(messages, cubaFlags(mailBox), true);
                                    return newEmailImapEvents;
                                }

                                return Collections.emptyList();
                            })
            );

            fireEvents(cubaFolder, imapEvents);
        }

        private void unsetCustomFlags(NewEmailImapEvent event) throws MessagingException {
            Message msg = folder.getMessageByUID(event.getMessageId());
            Flags flags = new Flags();
            String[] userFlags = msg.getFlags().getUserFlags();
            for (String flag : userFlags) {
                flags.add(flag);
            }
            if (userFlags.length > 0) {
                msg.setFlags(flags, false);
            }
        }

        private List<NewEmailImapEvent> saveNewMessages(List<MsgHeader> imapMessages) {
            List<NewEmailImapEvent> newEmailImapEvents = new ArrayList<>(imapMessages.size());
            boolean toCommit = false;
            authentication.begin();
            try (Transaction tx = persistence.createTransaction()) {
                EntityManager em = persistence.getEntityManager();

                for (MsgHeader msg : imapMessages) {
                    ImapMessageRef newMessage = insertNewMessage(em, msg);
                    toCommit |= (newMessage != null);

                    if (newMessage != null) {
                        newEmailImapEvents.add(new NewEmailImapEvent(newMessage));
                    }
                }
                if (toCommit) {
                    tx.commit();
                }

            } finally {
                authentication.end();
            }
            return newEmailImapEvents;
        }

        private ImapMessageRef insertNewMessage(EntityManager em, MsgHeader msg) {
            long uid = msg.getUid();
            Flags flags = msg.getFlags();
            String caption = msg.getCaption();
            String refId = msg.getRefId();
            Long threadId = msg.getThreadId();

            int sameUids = em.createQuery(
                    "select m from imapcomponent$ImapMessageRef m where m.msgUid = :uid and m.folder.id = :mailFolderId"
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

    private void fireEvents(ImapFolder folder, Collection<? extends BaseImapEvent> imapEvents) {
        imapEvents.forEach(event -> {
            ImapEventType.getByEventType(event.getClass()).stream()
                    .map(folder::getEvent)
                    .filter(Objects::nonNull)
                    .filter(folderEvent -> folderEvent.getMethodName() != null)
                    .forEach(folderEvent -> invokeAttachedHandler(event, folderEvent));

            events.publish(event);
        });
    }

    private void invokeAttachedHandler(BaseImapEvent event, ImapFolderEvent folderEvent) {
        log.trace("{}: invoking bean", folderEvent);
        Object bean = AppBeans.get(folderEvent.getBeanName());
        Class<? extends BaseImapEvent> eventClass = folderEvent.getEvent().getEventClass();
        try {
            authentication.begin();
            List<Method> methods = Arrays.stream(bean.getClass().getMethods())
                    .filter(m -> m.getName().equals(folderEvent.getMethodName()))
                    .filter(m -> m.getParameterTypes().length == 1 && m.getParameterTypes()[0].isAssignableFrom(eventClass))
                    .collect(Collectors.toList());
            for (Method method : methods) {
                method.invoke(bean, event);
            }
        } catch (InvocationTargetException | IllegalAccessException e) {
            throw new RuntimeException("Can't invoke bean for imap folder event", e);
        } finally {
            authentication.end();
        }
    }

    private Flags cubaFlags(ImapMailBox mailBox) {
        Flags cubaFlags = new Flags();
        cubaFlags.add(mailBox.getCubaFlag());
        return cubaFlags;
    }

    private boolean isRunning(ImapMailBox mailBox) {
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
