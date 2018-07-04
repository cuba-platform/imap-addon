package com.haulmont.addon.imap.sync;

import com.haulmont.addon.imap.api.ImapFlag;
import com.haulmont.addon.imap.config.ImapConfig;
import com.haulmont.addon.imap.core.FolderKey;
import com.haulmont.addon.imap.core.ImapHelper;
import com.haulmont.addon.imap.core.ImapOperations;
import com.haulmont.addon.imap.core.MailboxKey;
import com.haulmont.addon.imap.dao.ImapDao;
import com.haulmont.addon.imap.dao.ImapMessageSyncDao;
import com.haulmont.addon.imap.entity.*;
import com.haulmont.addon.imap.execution.DelayableTask;
import com.haulmont.addon.imap.execution.ImapExecutor;
import com.haulmont.cuba.core.EntityManager;
import com.haulmont.cuba.core.Persistence;
import com.haulmont.cuba.core.Transaction;
import com.haulmont.cuba.core.global.Metadata;
import com.sun.mail.imap.IMAPFolder;
import com.sun.mail.imap.IMAPMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import javax.inject.Inject;
import javax.mail.Flags;
import javax.mail.Message;
import javax.mail.MessagingException;
import javax.mail.search.FlagTerm;
import javax.mail.search.MessageIDTerm;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component("imap_Synchronizer")
public class ImapSynchronizer {

    private final static Logger log = LoggerFactory.getLogger(ImapSynchronizer.class);

    private final ImapHelper imapHelper;
    private final ImapExecutor imapExecutor;
    private final ImapOperations imapOperations;
    private final ImapConfig imapConfig;
    private final Persistence persistence;
    private final ImapDao dao;
    private final ImapMessageSyncDao messageSyncDao;
    private final Metadata metadata;

    @SuppressWarnings("CdiInjectionPointsInspection")
    @Inject
    public ImapSynchronizer(ImapHelper imapHelper,
                            ImapExecutor imapExecutor,
                            ImapOperations imapOperations,
                            @SuppressWarnings("SpringJavaInjectionPointsAutowiringInspection") ImapConfig imapConfig,
                            Persistence persistence,
                            ImapDao dao,
                            ImapMessageSyncDao messageSyncDao,
                            Metadata metadata) {

        this.imapHelper = imapHelper;
        this.imapExecutor = imapExecutor;
        this.imapOperations = imapOperations;
        this.imapConfig = imapConfig;
        this.persistence = persistence;
        this.dao = dao;
        this.messageSyncDao = messageSyncDao;
        this.metadata = metadata;
    }

    public void synchronize(ImapFolder cubaFolder) {
        log.debug("[{}]synchronize", cubaFolder);
        ImapMailBox mailBox = cubaFolder.getMailBox();
        FolderKey folderKey = folderKey(cubaFolder);

        Calendar calendar = Calendar.getInstance();
        calendar.add(Calendar.MINUTE, -10);
        messageSyncDao.removeOldSyncs(cubaFolder.getId(), calendar.getTime());

        getNewMessages(cubaFolder, mailBox, folderKey);
        syncExistingMessages(cubaFolder, folderKey);
    }

    private void syncExistingMessages(ImapFolder cubaFolder, FolderKey folderKey) {
        log.debug("[{}]synchronize: sync existing messages", cubaFolder);
        Collection<ImapMessage> messagesForSync = messageSyncDao.findMessagesForSync(cubaFolder.getId());
        messageSyncDao.createSyncForMessages(messagesForSync, ImapSyncStatus.IN_SYNC);
        Map<Long, ImapMessage> messagesByUid = messagesForSync.stream()
                .collect(Collectors.toMap(ImapMessage::getMsgUid, Function.identity()));
        Collection<String> otherFolders = getOtherFolders(cubaFolder);
        imapExecutor.submitDelayable(new DelayableTask<>(
                folderKey,
                imapFolder -> {
                    List<IMAPMessage> imapMessages = imapOperations.getAllByUIDs(
                            imapFolder, messagesForSync.stream().mapToLong(ImapMessage::getMsgUid).toArray(), cubaFolder.getMailBox()
                    );
                    log.trace("[{}]synchronize: sync existing messages: messages from db: {}, from IMAP server: {}",
                            cubaFolder, messagesForSync, imapMessages);

                    for (IMAPMessage imapMessage : imapMessages) {
                        ImapMessage cubaMessage = messagesByUid.remove(imapFolder.getUID(imapMessage));
                        messageSyncDao.updateSyncStatus(cubaMessage,
                                ImapSyncStatus.REMAIN, ImapSyncStatus.IN_SYNC,
                                imapMessage.getFlags(), null, null);
                    }

                    for (ImapMessage cubaMessage : messagesByUid.values()) {
                        messageSyncDao.updateSyncStatus(
                                cubaMessage,
                                ImapSyncStatus.MISSED, ImapSyncStatus.IN_SYNC,
                                null, otherFolders.size(), null);
                    }
                    return messagesByUid.values();
                },
                cubaMessages -> handleMissedMessages(cubaFolder, cubaMessages, otherFolders),
                "sync existing messages"
        ));
    }

    private void handleMissedMessages(ImapFolder cubaFolder, Collection<ImapMessage> cubaMessages, Collection<String> otherFolders) {
        List<ImapMessage> messagesWithoutMsgIdHeader = cubaMessages.stream()
                .filter(msg -> msg.getMessageId() == null)
                .collect(Collectors.toList());
        log.debug("[{}]synchronize: sync missed messages: {} have no message-id header, they will be track as removed", cubaFolder, messagesWithoutMsgIdHeader);
        for (ImapMessage imapMessage : messagesWithoutMsgIdHeader) {
            messageSyncDao.updateSyncStatus(imapMessage,
                    ImapSyncStatus.REMOVED, ImapSyncStatus.MISSED,
                    null, null, null);
            cubaMessages.remove(imapMessage);
        }

        Collection<ImapMessage> movedMessagesInDb = findMovedMessagesInDb(cubaFolder, cubaMessages);
        log.debug("[{}]synchronize: sync missed messages: found {} in db in other folders", cubaFolder, movedMessagesInDb);

        cubaMessages.removeAll(movedMessagesInDb);
        log.debug("[{}]synchronize: sync missed messages: will try to find {} in IMAP in other folders", cubaFolder, cubaMessages);
        Map<String, ImapMessage> messagesByIds = cubaMessages.stream()
                .collect(Collectors.toMap(ImapMessage::getMessageId, Function.identity()));
        for (String otherFolder : otherFolders) {
            ImapSyncStatus successStatus = otherFolder.equals(cubaFolder.getMailBox().getTrashFolderName())
                    ? ImapSyncStatus.REMOVED : ImapSyncStatus.MOVED;
            FolderKey folderKey = new FolderKey(new MailboxKey(cubaFolder.getMailBox()), otherFolder);
            for (Map.Entry<String, ImapMessage> messageEntry : messagesByIds.entrySet()) {
                imapExecutor.submitDelayable(new DelayableTask<>(
                        folderKey,
                        imapFolder -> findMessageIdsInOtherFolder(otherFolder, successStatus, messageEntry, imapFolder),
                        foundMessageIds -> trackNotFoundMessagesDuringMissedSync(messagesByIds, foundMessageIds),
                        "search message " + messageEntry.getValue() + " missed in original folder " + cubaFolder.getName()
                ));
            }
        }
    }

    private List<String> findMessageIdsInOtherFolder(String folderName,
                                                     ImapSyncStatus successStatus,
                                                     Map.Entry<String, ImapMessage> messageEntry,
                                                     IMAPFolder imapFolder) throws MessagingException {

        List<IMAPMessage> imapMessages = imapOperations.searchMessageIds(
                imapFolder,
                new MessageIDTerm(messageEntry.getKey())
        );
        //todo: GreenMail can't handle properly OR combination of MessageID terms, need to check out with real IMAP servers to avoid extra hits
            /*List<IMAPMessage> messages = imapHelper.searchMessageIds(
                    imapFolder,
                    new OrTerm(messageIds.stream().map(MessageIDTerm::new).toArray(SearchTerm[]::new))
            );*/
        List<String> result = new ArrayList<>(imapMessages.size());
        for (IMAPMessage imapMessage : imapMessages) {
            String messageID = imapMessage.getMessageID();
            messageSyncDao.updateSyncStatus(messageEntry.getValue(),
                    successStatus, ImapSyncStatus.MISSED,
                    null, null, folderName);
            result.add(messageID);
        }

        return result;
    }

    private void trackNotFoundMessagesDuringMissedSync(Map<String, ImapMessage> messagesByIds, List<String> foundMessageIds) {
        log.debug("[{}]synchronize: sync missed messages: found messages with message-id headers {} in other folder among {}, will try to mark them removed",
                foundMessageIds, messagesByIds);
        List<ImapMessage> notFoundMessages = messagesByIds.entrySet().stream()
                .filter(entry -> !foundMessageIds.contains(entry.getKey()))
                .map(Map.Entry::getValue)
                .collect(Collectors.toList());

        for (ImapMessage notFoundMessage : notFoundMessages) {
            ImapMessageSync messageSync = messageSyncDao.trackCheckedFolder(notFoundMessage);
            if (messageSync.getFoldersToCheck() != null
                    && messageSync.getFoldersToCheck().equals(messageSync.getCheckedFolders())) {
                messageSyncDao.updateSyncStatus(notFoundMessage,
                        ImapSyncStatus.REMOVED, ImapSyncStatus.MISSED,
                        null, null, null);
            }
        }
    }

    private Collection<ImapMessage> findMovedMessagesInDb(ImapFolder cubaFolder, Collection<ImapMessage> cubaMessages) {
        Map<Long, ImapMessage> msgByUid = cubaMessages.stream()
                .collect(Collectors.toMap(ImapMessage::getMsgUid, Function.identity()));
        List<ImapMessage> messagesInOtherFolders;
        try (Transaction ignore = persistence.createTransaction()) {
            EntityManager em = persistence.getEntityManager();

            messagesInOtherFolders = em.createQuery(
                    "select m from imap$Message m where m.msgUid in :msgUids and m.folder.id != :mailFolderId",
                    ImapMessage.class
            )
                    .setParameter("msgUids", msgByUid.keySet())
                    .setParameter("mailFolderId", cubaFolder)
                    .setViewName("imap-msg-full")
                    .getResultList();
        }

        List<ImapMessage> processedMessages = new ArrayList<>(messagesInOtherFolders.size());
        if (!messagesInOtherFolders.isEmpty()) {
            for (ImapMessage messageInOtherFolder : messagesInOtherFolders) {
                ImapMessage cubaMessage = msgByUid.get(messageInOtherFolder.getMsgUid());
                processedMessages.add(cubaMessage);
                messageSyncDao.updateSyncStatus(cubaMessage,
                        ImapSyncStatus.MOVED, ImapSyncStatus.MISSED,
                        null, null, messageInOtherFolder.getFolder().getName());
            }
        }

        return processedMessages;
    }

    private void getNewMessages(ImapFolder cubaFolder, ImapMailBox mailBox, FolderKey folderKey) {
        imapExecutor.submitDelayable(new DelayableTask<>(
                folderKey,
                imapFolder -> {
                    log.debug("[{}]synchronize: fetch new messages", cubaFolder);
                    return imapOperations.search(
                            imapFolder,
                            new FlagTerm(imapHelper.cubaFlags(mailBox), false),
                            mailBox
                    );
                },
                imapMessages -> handleNewMessages(cubaFolder, imapMessages),
                "fetch new messages using flag"
        ));
    }

    private void handleNewMessages(ImapFolder cubaFolder, List<IMAPMessage> imapMessages) {
        log.debug("[{}]handle new messages: {}", cubaFolder, imapMessages);
        for (IMAPMessage imapMessage : imapMessages) {
            imapExecutor.submitDelayable(new DelayableTask<>(
                folderKey(cubaFolder),
                imapFolder -> {
                    if (Boolean.TRUE.equals(imapConfig.getClearCustomFlags())) {
                        log.trace("[{}]clear custom flags for message with uid {}",
                                cubaFolder, imapFolder.getUID(imapMessage));
                        unsetCustomFlags(imapMessage);
                    }
                    imapMessage.setFlags(imapHelper.cubaFlags(cubaFolder.getMailBox()), true);
                    return insertNewMessage(imapMessage, cubaFolder);
                },
                cubaMessage -> {
                    if (cubaMessage != null) {
                        handleAnswer(cubaMessage);
                    }
                },
                "handle new message"
            ));
        }
    }

    private ImapMessage insertNewMessage(IMAPMessage msg,
                                         ImapFolder cubaFolder) throws MessagingException {

        Flags flags = new Flags(msg.getFlags());
        /*if (Boolean.TRUE.equals(imapConfig.getClearCustomFlags())) {
            log.trace("[{}]clear custom flags", cubaFolder);
            for (String userFlag : flags.getUserFlags()) {
                flags.remove(userFlag);
            }
        }
        flags.add(cubaFolder.getMailBox().getCubaFlag());*/
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
                ImapMessage entity = metadata.create(ImapMessage.class);
                entity.setMsgUid(uid);
                entity.setFolder(cubaFolder);
                entity.setUpdateTs(new Date());
                entity.setImapFlags(flags);
                entity.setCaption(imapOperations.getSubject(msg));
                entity.setMessageId(msg.getHeader(ImapOperations.MESSAGE_ID_HEADER, null));
                entity.setReferenceId(imapOperations.getRefId(msg));
                entity.setThreadId(imapOperations.getThreadId(msg, cubaFolder.getMailBox()));
                entity.setMsgNum(msg.getMessageNumber());
                em.persist(entity);

                ImapMessageSync messageSync = metadata.create(ImapMessageSync.class);
                messageSync.setMessage(entity);
                messageSync.setStatus(ImapSyncStatus.ADDED);
                messageSync.setFolder(cubaFolder);
                em.persist(messageSync);

                tx.commit();

                return entity;
            }

            return null;
        }
    }

    private void handleAnswer(ImapMessage cubaMessage) {
        if (cubaMessage.getReferenceId() != null) {
            ImapMessage parentMessage = dao.findMessageByImapMessageId(
                    cubaMessage.getFolder().getMailBox().getId(), cubaMessage.getReferenceId()
            );
            imapExecutor.submitDelayable(DelayableTask.noResultTask(
                    folderKey(cubaMessage.getFolder()),
                    imapFolder -> {
                        Message imapMessage = imapFolder.getMessageByUID(cubaMessage.getMsgUid());
                        imapMessage.setFlag(Flags.Flag.ANSWERED, true);
                    },
                    "set ANSWERED flag on IMAP server for " + cubaMessage
            ));
            if (parentMessage != null && !parentMessage.getImapFlags().contains(ImapFlag.ANSWERED.imapFlags())) {
                addAnsweredToSync(parentMessage);
            }
        }
    }

    private void addAnsweredToSync(ImapMessage cubaMessage) {
        ImapMessageSync messageSync = messageSyncDao.findMessageSync(cubaMessage);
        Flags flags = new Flags(cubaMessage.getImapFlags());
        if (messageSync != null) {
            if (messageSync.getFlags() != null) {
                flags = messageSync.getImapFlags();
            }
            flags.add(ImapFlag.ANSWERED.imapFlags());

        } else {
            messageSync = metadata.create(ImapMessageSync.class);
            messageSync.setMessage(cubaMessage);
            messageSync.setFolder(cubaMessage.getFolder());
        }

        try (Transaction tx = persistence.createTransaction()) {
            EntityManager em = persistence.getEntityManager();

            messageSync.setImapFlags(flags);
            messageSync.setStatus(ImapSyncStatus.REMAIN);
            em.persist(messageSync);

            tx.commit();
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

    private Collection<String> getOtherFolders(ImapFolder cubaFolder) {
        ImapMailBox mailBox = dao.findMailBox(cubaFolder.getMailBox().getId());
        if (log.isTraceEnabled()) {
            log.trace("processable folders: {}",
                    mailBox.getProcessableFolders().stream().map(ImapFolder::getName).collect(Collectors.toList())
            );
        }
        Set<String> otherFoldersNames = mailBox.getProcessableFolders().stream()
                .filter(f -> !f.getName().equals(cubaFolder.getName()))
                .map(ImapFolder::getName)
                .collect(Collectors.toSet());
        if (mailBox.getTrashFolderName() != null && !mailBox.getTrashFolderName().equals(cubaFolder.getName())) {
            otherFoldersNames.add(mailBox.getTrashFolderName());
        }
        log.debug("Missed messages task will use folders {} to trigger MOVE event", otherFoldersNames);
        return otherFoldersNames;
    }

    private FolderKey folderKey(ImapFolder cubaFolder) {
        return new FolderKey(new MailboxKey(cubaFolder.getMailBox()), cubaFolder.getName());
    }
}
