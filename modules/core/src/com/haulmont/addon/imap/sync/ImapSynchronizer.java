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
import com.haulmont.bali.datastruct.Pair;
import com.haulmont.cuba.core.EntityManager;
import com.haulmont.cuba.core.Persistence;
import com.haulmont.cuba.core.Transaction;
import com.haulmont.cuba.core.global.Metadata;
import com.haulmont.cuba.security.app.Authentication;
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
    private final Authentication authentication;
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
                            Authentication authentication,
                            Persistence persistence,
                            ImapDao dao,
                            ImapMessageSyncDao messageSyncDao,
                            Metadata metadata) {

        this.imapHelper = imapHelper;
        this.imapExecutor = imapExecutor;
        this.imapOperations = imapOperations;
        this.imapConfig = imapConfig;
        this.authentication = authentication;
        this.persistence = persistence;
        this.dao = dao;
        this.messageSyncDao = messageSyncDao;
        this.metadata = metadata;
    }

    public void synchronize(ImapFolder cubaFolder) {
        log.debug("[{}]synchronize", cubaFolder);
        authentication.begin();
        try {
            ImapMailBox mailBox = cubaFolder.getMailBox();
            FolderKey folderKey = folderKey(cubaFolder);

            Calendar calendar = Calendar.getInstance();
            calendar.add(Calendar.MINUTE, -10);
            Date tenMinutesAgo = calendar.getTime();
            calendar.add(Calendar.MINUTE, 7);
            Date threeMinutesAgo = calendar.getTime();
            messageSyncDao.removeOldSyncs(cubaFolder.getId(), tenMinutesAgo);

            getNewMessages(cubaFolder, mailBox, folderKey);
            syncExistingMessages(cubaFolder, folderKey);

            Collection<ImapMessage> oldInSync = messageSyncDao.findMessagesWithSyncStatus(
                    cubaFolder.getId(), ImapSyncStatus.IN_SYNC, tenMinutesAgo, threeMinutesAgo);
            doSyncExisting(cubaFolder, folderKey, oldInSync);

            Collection<ImapMessage> missed = messageSyncDao.findMessagesWithSyncStatus(
                    cubaFolder.getId(), ImapSyncStatus.MISSED, tenMinutesAgo, threeMinutesAgo);
            handleMissedMessages(cubaFolder, missed, getOtherFolders(cubaFolder));
        } finally {
            authentication.end();
        }
    }

    private void syncExistingMessages(ImapFolder cubaFolder, FolderKey folderKey) {
        log.debug("[{}]synchronize: sync existing messages", cubaFolder);
        authentication.begin();
        try {
            Collection<ImapMessage> messagesForSync = messageSyncDao.findMessagesForSync(cubaFolder.getId());
            messageSyncDao.createSyncForMessages(messagesForSync, ImapSyncStatus.IN_SYNC);
            if (messagesForSync.isEmpty()) {
                return;
            }
            doSyncExisting(cubaFolder, folderKey, messagesForSync);
        } finally {
            authentication.end();
        }
    }

    private void doSyncExisting(ImapFolder cubaFolder, FolderKey folderKey, Collection<ImapMessage> messagesForSync) {
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
                    authentication.begin();
                    try {
                        List<ImapMessage> remainMessages = new ArrayList<>(imapMessages.size());
                        for (IMAPMessage imapMessage : imapMessages) {
                            ImapMessage cubaMessage = messagesByUid.remove(imapFolder.getUID(imapMessage));
                            remainMessages.add(cubaMessage);
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
                        return new Pair<>(remainMessages, new ArrayList<>(messagesByUid.values()));
                    } finally {
                        authentication.end();
                    }
                },
                remainAndMissedMessages -> {
                    handleAnswers(remainAndMissedMessages.getFirst());
                    handleMissedMessages(cubaFolder, remainAndMissedMessages.getSecond(), otherFolders);
                },
                "sync existing messages"
        ));
    }

    private void handleMissedMessages(ImapFolder cubaFolder, Collection<ImapMessage> cubaMessages, Collection<String> otherFolders) {
        if (cubaMessages.isEmpty()) {
            return;
        }
        List<ImapMessage> messagesWithoutMsgIdHeader = cubaMessages.stream()
                .filter(msg -> msg.getMessageId() == null)
                .collect(Collectors.toList());
        log.debug("[{}]synchronize: sync missed messages: {} have no message-id header, they will be track as removed",
                cubaFolder, messagesWithoutMsgIdHeader);
        authentication.begin();
        try {
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
                String folderInSync = successStatus == ImapSyncStatus.MOVED ? otherFolder : null;
                for (Map.Entry<String, ImapMessage> messageEntry : messagesByIds.entrySet()) {
                    imapExecutor.submitDelayable(new DelayableTask<>(
                            folderKey,
                            imapFolder -> findMessageIdsInOtherFolder(folderInSync, successStatus, messageEntry, imapFolder),
                            foundMessageIds -> trackNotFoundMessagesDuringMissedSync(messagesByIds, foundMessageIds),
                            "search message " + messageEntry.getValue() + " missed in original folder " + cubaFolder.getName()
                    ));
                }
            }
        } finally {
            authentication.end();
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
        authentication.begin();
        try {
            for (IMAPMessage imapMessage : imapMessages) {
                String messageID = imapMessage.getMessageID();
                messageSyncDao.updateSyncStatus(messageEntry.getValue(),
                        successStatus, ImapSyncStatus.MISSED,
                        null, null, folderName);
                result.add(messageID);
            }
        } finally {
            authentication.end();
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
        if (cubaMessages.isEmpty()) {
            return cubaMessages;
        }
        Map<String, ImapMessage> msgById = cubaMessages.stream()
                .collect(Collectors.toMap(ImapMessage::getMessageId, Function.identity()));
        List<ImapMessage> messagesInOtherFolders;
        try (Transaction ignore = persistence.createTransaction()) {
            EntityManager em = persistence.getEntityManager();

            messagesInOtherFolders = em.createQuery(
                    "select m from imap$Message m where m.messageId in :msgIds " +
                    "and m.folder.id in (select f.id from imap$Folder f where f.mailBox.id = :mailBoxId and f.id <> :mailFolderId)",
                    ImapMessage.class
            )
                    .setParameter("msgIds", msgById.keySet())
                    .setParameter("mailFolderId", cubaFolder)
                    .setParameter("mailBoxId", cubaFolder.getMailBox())
                    .setViewName("imap-msg-full")
                    .getResultList();
        }

        List<ImapMessage> processedMessages = new ArrayList<>(messagesInOtherFolders.size());
        if (!messagesInOtherFolders.isEmpty()) {
            for (ImapMessage messageInOtherFolder : messagesInOtherFolders) {
                ImapMessage cubaMessage = msgById.get(messageInOtherFolder.getMessageId());
                processedMessages.add(cubaMessage);
                String newFolderName = messageInOtherFolder.getFolder().getName();
                ImapSyncStatus newStatus = newFolderName.equals(cubaFolder.getMailBox().getTrashFolderName())
                        ? ImapSyncStatus.REMOVED : ImapSyncStatus.MOVED;
                String folderInSync = newStatus == ImapSyncStatus.MOVED ? newFolderName : null;
                messageSyncDao.updateSyncStatus(cubaMessage,
                        newStatus, ImapSyncStatus.MISSED,
                        null, null, folderInSync);
            }
        }

        return processedMessages;
    }

    private void getNewMessages(ImapFolder cubaFolder, ImapMailBox mailBox, FolderKey folderKey) {
        imapExecutor.submitDelayable(new DelayableTask<>(
                folderKey,
                imapFolder -> {
                    log.debug("[{}]synchronize: fetch new messages", cubaFolder);
                    List<IMAPMessage> imapMessages = imapOperations.search(
                            imapFolder,
                            new FlagTerm(imapHelper.cubaFlags(mailBox), false),
                            mailBox
                    );
                    if (imapMessages.isEmpty()) {
                        return Collections.emptyList();
                    }
                    List<ImapMessage> cubaMessages = new ArrayList<>(imapMessages.size());
                    for (IMAPMessage imapMessage : imapMessages) {
                        if (Boolean.TRUE.equals(imapConfig.getClearCustomFlags())) {
                            log.trace("[{}]clear custom flags for message with uid {}",
                                    cubaFolder, imapFolder.getUID(imapMessage));
                            unsetCustomFlags(imapMessage);
                        }
                        imapMessage.setFlags(imapHelper.cubaFlags(cubaFolder.getMailBox()), true);
                        log.debug("[{}]insert message with uid {} to db after changing flags on server",
                                cubaFolder, imapFolder.getUID(imapMessage));
                        ImapMessage cubaMessage = insertNewMessage(imapMessage, cubaFolder);
                        if (cubaMessage != null) {
                            cubaMessages.add(cubaMessage);
                        }
                    }
                    return cubaMessages;
                },
                this::handleAnswers,
                "fetch new messages using flag"
        ));
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

    private void handleAnswers(List<ImapMessage> cubaMessages) {
        if (cubaMessages.isEmpty()) {
            return;
        }
        authentication.begin();
        try {
            for (ImapMessage cubaMessage : cubaMessages) {
                if (cubaMessage.getReferenceId() != null) {
                    ImapMessage parentMessage = dao.findMessageByImapMessageId(
                            cubaMessage.getFolder().getMailBox().getId(), cubaMessage.getReferenceId()
                    );

                    if (parentMessage != null && !parentMessage.getImapFlags().contains(ImapFlag.ANSWERED.imapFlags())) {
                        imapExecutor.submitDelayable(DelayableTask.noResultTask(
                                folderKey(parentMessage.getFolder()),
                                imapFolder -> {
                                    Message imapMessage = imapFolder.getMessageByUID(parentMessage.getMsgUid());
                                    imapMessage.setFlag(Flags.Flag.ANSWERED, true);
                                },
                                "set ANSWERED flag on IMAP server for message with uid " + parentMessage.getMsgUid()
                        ));
                    }
                }
            }
        } finally {
            authentication.end();
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
        authentication.begin();
        try {
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
        } finally {
            authentication.end();
        }
    }

    private FolderKey folderKey(ImapFolder cubaFolder) {
        return new FolderKey(new MailboxKey(cubaFolder.getMailBox()), cubaFolder.getName());
    }
}
