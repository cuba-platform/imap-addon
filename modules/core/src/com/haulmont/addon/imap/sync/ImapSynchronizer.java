package com.haulmont.addon.imap.sync;

import com.haulmont.addon.imap.api.ImapFlag;
import com.haulmont.addon.imap.config.ImapConfig;
import com.haulmont.addon.imap.core.ImapHelper;
import com.haulmont.addon.imap.core.ImapOperations;
import com.haulmont.addon.imap.dao.ImapDao;
import com.haulmont.addon.imap.dao.ImapMessageSyncDao;
import com.haulmont.addon.imap.entity.*;
import com.haulmont.addon.imap.exception.ImapException;
import com.haulmont.cuba.core.EntityManager;
import com.haulmont.cuba.core.Persistence;
import com.haulmont.cuba.core.Transaction;
import com.haulmont.cuba.core.global.Metadata;
import com.haulmont.cuba.security.app.Authentication;
import com.sun.mail.imap.IMAPFolder;
import com.sun.mail.imap.IMAPMessage;
import com.sun.mail.imap.IMAPStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import javax.inject.Inject;
import javax.mail.Flags;
import javax.mail.Folder;
import javax.mail.Message;
import javax.mail.MessagingException;
import javax.mail.search.FlagTerm;
import javax.mail.search.MessageIDTerm;
import java.util.*;

@Component("imap_Synchronizer")
public class ImapSynchronizer {

    private final static Logger log = LoggerFactory.getLogger(ImapSynchronizer.class);

    private final ImapHelper imapHelper;
    private final ImapOperations imapOperations;
    private final ImapConfig imapConfig;
    private final Authentication authentication;
    private final Persistence persistence;
    private final ImapDao dao;
    private final ImapMessageSyncDao messageSyncDao;
    private final Metadata metadata;

    @SuppressWarnings("CdiInjectionPointsInspection")
    @Inject
    public ImapSynchronizer(ImapHelper imapHelper,
                            ImapOperations imapOperations,
                            @SuppressWarnings("SpringJavaInjectionPointsAutowiringInspection") ImapConfig imapConfig,
                            Authentication authentication,
                            Persistence persistence,
                            ImapDao dao,
                            ImapMessageSyncDao messageSyncDao,
                            Metadata metadata) {

        this.imapHelper = imapHelper;
        this.imapOperations = imapOperations;
        this.imapConfig = imapConfig;
        this.authentication = authentication;
        this.persistence = persistence;
        this.dao = dao;
        this.messageSyncDao = messageSyncDao;
        this.metadata = metadata;
    }

    public void synchronize(UUID mailBoxId) {
        authentication.begin();
        try {
            ImapMailBox mailBox = dao.findMailBox(mailBoxId);
            if (mailBox == null) {
                return;
            }
            IMAPStore store = imapHelper.getStore(mailBox);
            try {
                List<ImapMessage> checkAnswers = new ArrayList<>();
                List<ImapMessage> missedMessages = new ArrayList<>();
                for (ImapFolder cubaFolder : mailBox.getProcessableFolders()) {
                    if (Thread.currentThread().isInterrupted()) {
                        logInterruption(mailBoxId);
                        return;
                    }
                    IMAPFolder imapFolder = null;
                    try {
                        Calendar calendar = Calendar.getInstance();
                        calendar.add(Calendar.MINUTE, -10);
                        Date tenMinutesAgo = calendar.getTime();
                        messageSyncDao.removeOldSyncs(cubaFolder.getId(), tenMinutesAgo);

                        imapFolder = (IMAPFolder) store.getFolder(cubaFolder.getName());
                        imapFolder.open(Folder.READ_WRITE);

                        //new
                        handleNewMessages(checkAnswers, cubaFolder, imapFolder);

                        //existing
                        handleExistingMessages(checkAnswers, missedMessages, cubaFolder, imapFolder);

                    } catch (MessagingException e) {
                        log.warn("synchronization of folder " + cubaFolder.getName() + " of mailbox " + mailBox + " failed", e);
                    } finally {
                        close(mailBox, imapFolder);
                    }
                }

                // answers
                setAnswersFlag(mailBox, store, checkAnswers);

                // missed
                handleMissedMessages(mailBox, store, missedMessages);
            } finally {
                store.close();
            }
        } catch (MessagingException e) {
            throw new ImapException("synchronization of mailBox#" + mailBoxId + "failed", e);
        } catch (Exception e) {
            log.error("Synchronization failed", e);
        } finally {
            authentication.end();
        }
    }

    private void logInterruption(UUID mailBoxId) {
        log.info("synchronization for mailbox#" + mailBoxId + " was interrupted");
    }

    private void handleExistingMessages(List<ImapMessage> checkAnswers,
                                        List<ImapMessage> missedMessages,
                                        ImapFolder cubaFolder,
                                        IMAPFolder imapFolder) throws MessagingException {

        Calendar calendar = Calendar.getInstance();
        calendar.add(Calendar.MINUTE, -10);
        Date tenMinutesAgo = calendar.getTime();
        calendar.add(Calendar.MINUTE, 7);
        Date threeMinutesAgo = calendar.getTime();

        Collection<ImapMessage> messagesForSync = new ArrayList<>(messageSyncDao.findMessagesForSync(cubaFolder.getId()));
        try (Transaction tx = persistence.createTransaction()) {
            EntityManager em = persistence.getEntityManager();
            for (ImapMessage message : messagesForSync) {
                if (Thread.currentThread().isInterrupted()) {
                    logInterruption(cubaFolder.getMailBox().getId());
                    return;
                }
                if (messageSyncDao.findSync(message) == null) {
                    ImapMessageSync messageSync = metadata.create(ImapMessageSync.class);
                    messageSync.setMessage(message);
                    messageSync.setStatus(ImapSyncStatus.IN_SYNC);
                    messageSync.setFolder(message.getFolder());
                    em.persist(messageSync);
                    message.setUpdateTs(new Date());
                    em.merge(message);
                }
            }

            tx.commit();
        }
        Collection<ImapMessage> oldInSync = messageSyncDao.findMessagesWithSyncStatus(
                cubaFolder.getId(), ImapSyncStatus.IN_SYNC, tenMinutesAgo, threeMinutesAgo);
        messagesForSync.addAll(oldInSync);
        for (ImapMessage cubaMessage : messagesForSync) {
            if (Thread.currentThread().isInterrupted()) {
                logInterruption(cubaFolder.getMailBox().getId());
                return;
            }
            Message imapMessage = imapFolder.getMessageByUID(cubaMessage.getMsgUid());
            if (imapMessage != null) {
                messageSyncDao.updateSyncStatus(cubaMessage,
                        ImapSyncStatus.REMAIN, ImapSyncStatus.IN_SYNC,
                        imapMessage.getFlags(), null);
                if (cubaMessage.getReferenceId() != null) {
                    checkAnswers.add(cubaMessage);
                }
            } else {
                missedMessages.add(cubaMessage);
                messageSyncDao.updateSyncStatus(
                        cubaMessage,
                        ImapSyncStatus.MISSED, ImapSyncStatus.IN_SYNC,
                        null, null);
            }
        }

        Collection<ImapMessage> missed = new ArrayList<>(messageSyncDao.findMessagesWithSyncStatus(
                cubaFolder.getId(), ImapSyncStatus.MISSED, tenMinutesAgo, threeMinutesAgo));
        missedMessages.addAll(missed);
    }

    private void handleNewMessages(List<ImapMessage> checkAnswers, ImapFolder cubaFolder, IMAPFolder imapFolder) throws MessagingException {
        ImapMailBox mailBox = cubaFolder.getMailBox();
        List<IMAPMessage> imapMessages = imapOperations.search(
                imapFolder,
                new FlagTerm(imapHelper.cubaFlags(mailBox), false),
                mailBox
        );
        if (!imapMessages.isEmpty()) {
            for (IMAPMessage imapMessage : imapMessages) {
                if (Thread.currentThread().isInterrupted()) {
                    logInterruption(mailBox.getId());
                    return;
                }
                if (Boolean.TRUE.equals(imapConfig.getClearCustomFlags())) {
                    log.trace("[{}]clear custom flags for message with uid {}",
                            cubaFolder, imapFolder.getUID(imapMessage));
                    unsetCustomFlags(imapMessage);
                }
                imapMessage.setFlags(imapHelper.cubaFlags(mailBox), true);
                log.debug("[{}]insert message with uid {} to db after changing flags on server",
                        cubaFolder, imapFolder.getUID(imapMessage));
                ImapMessage cubaMessage = insertNewMessage(imapMessage, cubaFolder);
                if (cubaMessage != null && cubaMessage.getReferenceId() != null) {
                    checkAnswers.add(cubaMessage);
                }
            }
        }
    }

    private void handleMissedMessages(ImapMailBox mailBox, IMAPStore store, List<ImapMessage> missedMessages) throws MessagingException {
        List<ImapMessage> foundMessages = new ArrayList<>();
        for (ImapFolder cubaFolder : mailBox.getProcessableFolders()) {
            IMAPFolder imapFolder = null;
            try {
                imapFolder = (IMAPFolder) store.getFolder(cubaFolder.getName());
                imapFolder.open(Folder.READ_ONLY);

                for (ImapMessage cubaMessage : missedMessages) {
                    if (Thread.currentThread().isInterrupted()) {
                        logInterruption(mailBox.getId());
                        return;
                    }
                    if (foundMessages.contains(cubaMessage)) {
                        continue;
                    }

                    if (cubaMessage.getMessageId() == null) {
                        foundMessages.add(cubaMessage);
                        messageSyncDao.updateSyncStatus(cubaMessage,
                                ImapSyncStatus.REMOVED, ImapSyncStatus.MISSED,
                                null, null);
                        continue;
                    }

                    List<IMAPMessage> imapMessages = imapOperations.searchMessageIds(
                            imapFolder,
                            new MessageIDTerm(cubaMessage.getMessageId())
                    );
                    if (!imapMessages.isEmpty()) {
                        foundMessages.add(cubaMessage);
                        if (cubaFolder.getName().equals(mailBox.getTrashFolderName())) {
                            messageSyncDao.updateSyncStatus(cubaMessage,
                                    ImapSyncStatus.REMOVED, ImapSyncStatus.MISSED,
                                    null, null);
                        } else {
                            messageSyncDao.updateSyncStatus(cubaMessage,
                                    ImapSyncStatus.MOVED, ImapSyncStatus.MISSED,
                                    null, cubaFolder.getName());
                        }
                    }
                }

            } finally {
                close(mailBox, imapFolder);
            }
        }
        missedMessages.removeAll(foundMessages);
        for (ImapMessage cubaMessage : missedMessages) {
            messageSyncDao.updateSyncStatus(cubaMessage,
                    ImapSyncStatus.REMOVED, ImapSyncStatus.MISSED,
                    null, null);
        }
    }

    private void setAnswersFlag(ImapMailBox mailBox, IMAPStore store, List<ImapMessage> checkAnswers) throws MessagingException {
        Map<String, List<ImapMessage>> folderWithMessagesToAnswer = new HashMap<>();
        for (ImapMessage cubaMessage : checkAnswers) {
            ImapMessage parentMessage = dao.findMessageByImapMessageId(
                    cubaMessage.getFolder().getMailBox().getId(), cubaMessage.getReferenceId()
            );

            if (parentMessage != null && !parentMessage.getImapFlags().contains(ImapFlag.ANSWERED.imapFlags())) {
                String folderName = parentMessage.getFolder().getName();
                if (folderWithMessagesToAnswer.containsKey(folderName)) {
                    folderWithMessagesToAnswer.get(folderName).add(parentMessage);
                } else {
                    ArrayList<ImapMessage> messages = new ArrayList<>();
                    messages.add(parentMessage);
                    folderWithMessagesToAnswer.putIfAbsent(folderName, messages);
                }
            }
        }
        for (Map.Entry<String, List<ImapMessage>> folderWithMessages : folderWithMessagesToAnswer.entrySet()) {
            IMAPFolder imapFolder = null;
            try {
                imapFolder = (IMAPFolder) store.getFolder(folderWithMessages.getKey());
                imapFolder.open(Folder.READ_WRITE);

                for (ImapMessage msg : folderWithMessages.getValue()) {
                    Message imapMessage = imapFolder.getMessageByUID(msg.getMsgUid());
                    imapMessage.setFlag(Flags.Flag.ANSWERED, true);

                    ImapMessageSync sync = messageSyncDao.findSync(msg);
                    if (sync != null && sync.getStatus() == ImapSyncStatus.REMAIN) {
                        Flags imapFlags = sync.getImapFlags();
                        imapFlags.add(Flags.Flag.ANSWERED);
                        sync.setImapFlags(imapFlags);
                        messageSyncDao.saveSync(sync);
                    } else {
                        Flags imapFlags = msg.getImapFlags();
                        imapFlags.add(Flags.Flag.ANSWERED);
                        msg.setImapFlags(imapFlags);

                        dao.saveMessage(msg);
                    }
                }
            } finally {
                close(mailBox, imapFolder);
            }
        }
    }

    private void close(ImapMailBox mailBox, IMAPFolder imapFolder) {
        if (imapFolder != null) {
            try {
                imapFolder.close(false);
            } catch (MessagingException e) {
                log.warn("can't close folder " + imapFolder.getFullName() + " of mailbox " + mailBox, e);
            }
        }
    }

    private ImapMessage insertNewMessage(IMAPMessage msg,
                                         ImapFolder cubaFolder) throws MessagingException {

        long uid = ((IMAPFolder) msg.getFolder()).getUID(msg);
        try (Transaction tx = persistence.createTransaction()) {
            EntityManager em = persistence.getEntityManager();
            int sameUIDs = em.createQuery(
                    "select m from imap$Message m where m.msgUid = :uid and m.folder.id = :mailFolderId"
            )
                    .setParameter("uid", uid)
                    .setParameter("mailFolderId", cubaFolder)
                    .setMaxResults(1)
                    .getResultList()
                    .size();
            if (sameUIDs == 0) {
                log.trace("Save new message {}", msg);
                ImapMessage entity = imapOperations.map(msg, cubaFolder);
                em.persist(entity);

                ImapMessageSync messageSync = metadata.create(ImapMessageSync.class);
                messageSync.setMessage(entity);
                messageSync.setStatus(ImapSyncStatus.ADDED);
                messageSync.setFolder(cubaFolder);
                em.persist(messageSync);

                if (Thread.currentThread().isInterrupted()) {
                    logInterruption(cubaFolder.getMailBox().getId());
                    return null;
                }
                tx.commit();

                return entity;
            }

            return null;
        }
    }

    private void unsetCustomFlags(Message msg) throws MessagingException {
        Flags flags = new Flags();
        String[] userFlags = msg.getFlags().getUserFlags();
        for (String flag : userFlags) {
            flags.add(flag);
        }
        if (userFlags.length > 0) {
            msg.setFlags(flags, false);
        }
    }

}
