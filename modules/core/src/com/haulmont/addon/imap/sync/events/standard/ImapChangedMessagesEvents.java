package com.haulmont.addon.imap.sync.events.standard;

import com.google.common.collect.ImmutableMap;
import com.haulmont.addon.imap.api.ImapFlag;
import com.haulmont.addon.imap.config.ImapConfig;
import com.haulmont.addon.imap.core.ImapHelper;
import com.haulmont.addon.imap.core.Task;
import com.haulmont.addon.imap.dao.ImapDao;
import com.haulmont.addon.imap.entity.ImapFolder;
import com.haulmont.addon.imap.entity.ImapMailBox;
import com.haulmont.addon.imap.entity.ImapMessage;
import com.haulmont.addon.imap.events.BaseImapEvent;
import com.haulmont.addon.imap.events.EmailAnsweredImapEvent;
import com.haulmont.addon.imap.events.EmailFlagChangedImapEvent;
import com.haulmont.addon.imap.events.EmailSeenImapEvent;
import com.haulmont.addon.imap.exception.ImapException;
import com.haulmont.cuba.core.EntityManager;
import com.haulmont.cuba.core.Persistence;
import com.haulmont.cuba.core.Transaction;
import com.haulmont.cuba.security.app.Authentication;
import com.sun.mail.imap.IMAPFolder;
import com.sun.mail.imap.IMAPMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import javax.annotation.Nonnull;
import javax.inject.Inject;
import javax.mail.Flags;
import javax.mail.Folder;
import javax.mail.Message;
import javax.mail.MessagingException;
import java.util.*;
import java.util.stream.Collectors;

@Component("imap_ChangedMessagesEvents")
public class ImapChangedMessagesEvents {
    private final static Logger log = LoggerFactory.getLogger(ImapChangedMessagesEvents.class);

    private static final String TASK_DESCRIPTION = "updating messages flags";

    private final ImapHelper imapHelper;
    private final Authentication authentication;
    private final Persistence persistence;
    private final ImapDao dao;
    private final ImapConfig imapConfig;

    @SuppressWarnings("CdiInjectionPointsInspection")
    @Inject
    public ImapChangedMessagesEvents(ImapHelper imapHelper,
                                     Authentication authentication,
                                     Persistence persistence,
                                     ImapDao dao,
                                     ImapConfig imapConfig) {
        this.imapHelper = imapHelper;
        this.authentication = authentication;
        this.persistence = persistence;
        this.dao = dao;
        this.imapConfig = imapConfig;
    }

    @SuppressWarnings("WeakerAccess")
    protected Collection<BaseImapEvent> generate(@Nonnull ImapFolder cubaFolder) {
        int batchSize = imapConfig.getUpdateBatchSize();
        ImapMailBox mailBox = cubaFolder.getMailBox();
        long windowSize = Math.min(getCount(cubaFolder), batchSize);
        log.debug("[{} for {}]handle events for existing messages using windowSize {} and batchSize {}",
                TASK_DESCRIPTION, cubaFolder, windowSize, batchSize);

        List<BaseImapEvent> modificationEvents = new ArrayList<>((int) windowSize);
        for (int i = 0; i < windowSize; i += batchSize) {
            int thisBatchSize = (int) Math.min(batchSize, windowSize - i);
            log.trace("[{} for {}]handle batch#{} with size {}",
                    TASK_DESCRIPTION, cubaFolder, i, thisBatchSize);

            Collection<BaseImapEvent> batchResult = imapHelper.doWithFolder(
                    mailBox,
                    cubaFolder.getName(),
                    new Task<>(
                            TASK_DESCRIPTION,
                            true,
                            f -> handleBatch(f, batchSize, cubaFolder)
                    )
            );
            modificationEvents.addAll(batchResult);
        }

        imapHelper.setAnsweredFlag(mailBox, modificationEvents);
        return modificationEvents;
    }

    @SuppressWarnings("WeakerAccess")
    protected Collection<BaseImapEvent> generate(@Nonnull ImapFolder cubaFolder,
                                                 @Nonnull Collection<IMAPMessage> changedMessages) {
        if (changedMessages.isEmpty()) {
            return Collections.emptyList();
        }

        IMAPFolder imapFolder = (IMAPFolder) changedMessages.iterator().next().getFolder();

        try {
            if (!imapFolder.isOpen()) {
                imapFolder.open(Folder.READ_WRITE);
            }
            List<IMAPMessage> imapMessages = imapHelper.fetchUids(imapFolder, changedMessages.toArray(new Message[0]));
            Map<Long, IMAPMessage> msgsByUid = new HashMap<>(imapMessages.size());
            for (IMAPMessage msg : imapMessages) {
                msgsByUid.put(imapFolder.getUID(msg), msg);
            }

            Collection<ImapMessage> messages = getMessages(cubaFolder, changedMessages);

            Collection<BaseImapEvent> imapEvents = generate(messages, msgsByUid);

            imapHelper.setAnsweredFlag(cubaFolder.getMailBox(), imapEvents);
            return imapEvents;
        } catch (MessagingException e) {
            throw new ImapException(e);
        }
    }

    private long getCount(ImapFolder cubaFolder) {
        authentication.begin();
        try (Transaction ignored = persistence.createTransaction()) {
            EntityManager em = persistence.getEntityManager();
            return ((Number) em.createQuery("select count(m.id) from imap$Message m where m.folder.id = :mailFolderId")
                    .setParameter("mailFolderId", cubaFolder)
                    .getSingleResult()).longValue();
        } finally {
            authentication.end();
        }
    }

    private Collection<BaseImapEvent> handleBatch(IMAPFolder folder, int count, ImapFolder cubaFolder) throws MessagingException {
        Collection<ImapMessage> messages = getMessages(cubaFolder, count);

        List<IMAPMessage> imapMessages = imapHelper.getAllByUids(
                folder, messages.stream().mapToLong(ImapMessage::getMsgUid).toArray(), cubaFolder.getMailBox()
        );
        log.trace("[updating messages flags for {}]batch messages from db: {}, from IMAP server: {}",
                TASK_DESCRIPTION, cubaFolder, messages, imapMessages);

        Map<Long, IMAPMessage> msgsByUid = new HashMap<>(imapMessages.size());
        for (IMAPMessage msg : imapMessages) {
            msgsByUid.put(folder.getUID(msg), msg);
        }

        return generate(messages, msgsByUid);
    }

    private Collection<BaseImapEvent> generate(Collection<ImapMessage> messages,
                                               Map<Long, IMAPMessage> msgsByUid) throws MessagingException {
        authentication.begin();
        try (Transaction tx = persistence.createTransaction()) {
            EntityManager em = persistence.getEntityManager();

            Collection<BaseImapEvent> imapEvents = new ArrayList<>(messages.size());
            for (ImapMessage msg : messages) {
                List<BaseImapEvent> msgEvents = handleMessage(em, msg, msgsByUid);
                if (!msgEvents.isEmpty()) {
                    imapEvents.addAll(msgEvents);
                }
            }
            tx.commit();

            return imapEvents;

        } finally {
            authentication.end();
        }
    }

    private Collection<ImapMessage> getMessages(@Nonnull ImapFolder cubaFolder,
                                                @Nonnull Collection<IMAPMessage> changedMessages) {
        authentication.begin();
        try (Transaction ignore = persistence.createTransaction()) {
            return dao.findMessagesByImapNums(
                    cubaFolder.getId(),
                    changedMessages.stream().map(Message::getMessageNumber).collect(Collectors.toList())
            );
        } finally {
            authentication.end();
        }
    }

    private Collection<ImapMessage> getMessages(ImapFolder cubaFolder, int count) {
        authentication.begin();
        try (Transaction ignore = persistence.createTransaction()) {
            EntityManager em = persistence.getEntityManager();

            return em.createQuery(
                    "select m from imap$Message m where m.folder.id = :mailFolderId order by m.updateTs asc nulls first",
                    ImapMessage.class
            )
                    .setParameter("mailFolderId", cubaFolder)
                    .setMaxResults(count)
                    .setViewName("imap-msg-full")
                    .getResultList();
        } finally {
            authentication.end();
        }
    }

    private List<BaseImapEvent> handleMessage(EntityManager em,
                                              ImapMessage msg,
                                              Map<Long, IMAPMessage> msgsByUid) throws MessagingException {

        IMAPMessage newMsg = msgsByUid.get(msg.getMsgUid());
        if (newMsg == null) {
            return Collections.emptyList();
        }
        Flags newFlags = newMsg.getFlags();

        List<BaseImapEvent> modificationEvents = generate(msg, newMsg);
        String refId = imapHelper.getRefId(newMsg);
        if (refId != null) {
            ImapMessage parentMessage = dao.findMessageByImapMessageId(msg.getFolder().getMailBox().getId(), refId);
            if (parentMessage != null) {
                if (dao.addFlag(parentMessage, ImapFlag.ANSWERED, em)) {
                    modificationEvents.add(new EmailAnsweredImapEvent(parentMessage));
                    modificationEvents.add(new EmailFlagChangedImapEvent(parentMessage, ImmutableMap.of(ImapFlag.ANSWERED, true)));
                }
            }
        }
        msg.setImapFlags(newFlags);
        msg.setThreadId(imapHelper.getThreadId(newMsg, msg.getFolder().getMailBox()));  // todo: fire thread event
        msg.setUpdateTs(new Date());
        msg.setMsgNum(newMsg.getMessageNumber());

        em.createQuery("update imap$Message m set m.msgNum = :msgNum, m.threadId = :threadId, " +
                "m.updateTs = :updateTs, m.flags = :flags, m.referenceId = :refId where m.id = :id")
                .setParameter("msgNum", newMsg.getMessageNumber())
                .setParameter("threadId", imapHelper.getThreadId(newMsg, msg.getFolder().getMailBox()))
                .setParameter("refId", refId)
                .setParameter("updateTs", new Date())
                .setParameter("flags", msg.getFlags())
                .setParameter("id", msg.getId())
                .executeUpdate();

        return modificationEvents;
    }

    @SuppressWarnings("WeakerAccess")
    protected List<BaseImapEvent> generate(ImapMessage msg, IMAPMessage newMsg) {
        try {
            Flags newFlags = newMsg.getFlags();
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

            }
            return modificationEvents;
        } catch (MessagingException e) {
            throw new ImapException(e);
        }
    }

    private boolean isSeen(Flags newflags, Flags oldFlags) {
        return !oldFlags.contains(Flags.Flag.SEEN)
                && newflags.contains(Flags.Flag.SEEN);
    }

    private boolean isAnswered(Flags newflags, Flags oldFlags) {
        return !oldFlags.contains(Flags.Flag.ANSWERED)
                && newflags.contains(Flags.Flag.ANSWERED);
    }

}
