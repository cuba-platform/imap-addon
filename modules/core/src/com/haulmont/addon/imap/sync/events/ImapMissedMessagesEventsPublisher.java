package com.haulmont.addon.imap.sync.events;

import com.haulmont.addon.imap.api.ImapAPI;
import com.haulmont.addon.imap.core.Task;
import com.haulmont.addon.imap.dto.ImapFolderDto;
import com.haulmont.addon.imap.entity.ImapFolder;
import com.haulmont.addon.imap.entity.ImapMailBox;
import com.haulmont.addon.imap.entity.ImapMessage;
import com.haulmont.addon.imap.events.BaseImapEvent;
import com.haulmont.addon.imap.events.EmailDeletedImapEvent;
import com.haulmont.addon.imap.events.EmailMovedImapEvent;
import com.haulmont.addon.imap.exception.ImapException;
import com.haulmont.addon.imap.sync.ImapFolderSyncAction;
import com.haulmont.addon.imap.sync.ImapFolderSyncEvent;
import com.haulmont.bali.datastruct.Pair;
import com.haulmont.cuba.core.EntityManager;
import com.haulmont.cuba.core.Query;
import com.haulmont.cuba.core.Transaction;
import com.sun.mail.imap.IMAPFolder;
import com.sun.mail.imap.IMAPMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import javax.annotation.Nonnull;
import javax.inject.Inject;
import javax.mail.Folder;
import javax.mail.Message;
import javax.mail.MessagingException;
import javax.mail.search.MessageIDTerm;
import javax.mail.search.OrTerm;
import javax.mail.search.SearchTerm;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Component("imapcomponent_ImapMissedMessagesEventsPublisher")
public class ImapMissedMessagesEventsPublisher extends ImapEventsPublisher {

    private final static Logger log = LoggerFactory.getLogger(ImapMissedMessagesEventsPublisher.class);

    private final ImapAPI imapAPI;

    @Inject
    public ImapMissedMessagesEventsPublisher(ImapAPI imapAPI) {
        this.imapAPI = imapAPI;
    }

    public void handle(@Nonnull ImapFolder cubaFolder) {
        events.publish(new ImapFolderSyncEvent(
                new ImapFolderSyncAction(cubaFolder.getId(), ImapFolderSyncAction.Type.MISSED))
        );
        Collection<IMAPFolder> otherFolders = getOtherFolders(cubaFolder);
        imapHelper.doWithFolder(cubaFolder.getMailBox(), cubaFolder.getName(), new Task<>(
                taskDescription(),
                false,
                imapFolder -> {
                    doHandle(cubaFolder, imapFolder, otherFolders);
                    return null;
                }
        ));
    }

    public void handle(@Nonnull ImapFolder cubaFolder, IMAPFolder imapFolder, Message[] deletedMessages) {
        events.publish(new ImapFolderSyncEvent(
                new ImapFolderSyncAction(cubaFolder.getId(), ImapFolderSyncAction.Type.MISSED))
        );

        Collection<IMAPFolder> otherFolders = getOtherFolders(cubaFolder);

        List<ImapMessage> messages;

        authentication.begin();
        try (Transaction ignored = persistence.createTransaction()) {
            EntityManager em = persistence.getEntityManager();

            messages = em.createQuery(
                    "select m from imapcomponent$ImapMessage m where m.folder.id = :mailFolderId and m.msgNum in :msgNums",
                    ImapMessage.class
            )
                    .setParameter("mailFolderId", cubaFolder)
                    .setParameter("msgNums",
                            Arrays.stream(deletedMessages)
                                    .map(Message::getMessageNumber)
                                    .collect(Collectors.toList())
                    )
                    .setViewName("imap-msg-full")
                    .getResultList();
        } finally {
            authentication.end();
        }
        Collection<BaseImapEvent> imapEvents = makeEvents(cubaFolder, messages, otherFolders, imapFolder);
        fireEvents(cubaFolder, imapEvents);
    }

    private void doHandle(ImapFolder cubaFolder, IMAPFolder imapFolder, Collection<IMAPFolder> otherFolders) {
        int i = 0;
        List<ImapMessage> batchMessages;
        Collection<BaseImapEvent> imapEvents = new ArrayList<>();
        //todo: process batches in parallel ?
        do {
            batchMessages = getMessages(cubaFolder, i++);

            imapEvents.addAll(makeEvents(cubaFolder, batchMessages, otherFolders, imapFolder));
        } while (batchMessages.size() == imapConfig.getUpdateBatchSize());

        fireEvents(cubaFolder, imapEvents);
    }

    private Collection<IMAPFolder> getOtherFolders(ImapFolder cubaFolder) {
        ImapMailBox mailBox = cubaFolder.getMailBox();
        List<String> otherFoldersNames = mailBox.getProcessableFolders().stream()
                .filter(f -> !f.getName().equals(cubaFolder.getName()))
                .map(ImapFolder::getName)
                .collect(Collectors.toList());
        if (mailBox.getTrashFolderName() != null) {
            otherFoldersNames.add(mailBox.getTrashFolderName());
        }
        String[] folderNames = otherFoldersNames.toArray(new String[0]);
        Collection<IMAPFolder> otherFolders = imapAPI.fetchFolders(mailBox, folderNames).stream()
                .filter(f -> Boolean.TRUE.equals(f.getCanHoldMessages()))
                .map(ImapFolderDto::getImapFolder)
                .collect(Collectors.toList());
        if (log.isDebugEnabled()) {
            log.debug("Missed messages task will use folder {} to trigger MOVE event",
                    otherFolders.stream().map(IMAPFolder::getFullName).collect(Collectors.toList())
            );
        }
        return otherFolders;
    }

    private List<ImapMessage> getMessages(ImapFolder cubaFolder, int iterNum) {
        authentication.begin();
        try (Transaction ignored = persistence.createTransaction()) {
            EntityManager em = persistence.getEntityManager();

            return em.createQuery(
                    "select m from imapcomponent$ImapMessage m where m.folder.id = :mailFolderId order by m.msgNum",
                    ImapMessage.class
            )
                    .setParameter("mailFolderId", cubaFolder)
                    .setFirstResult(iterNum * imapConfig.getUpdateBatchSize())
                    .setMaxResults(imapConfig.getUpdateBatchSize())
                    .setViewName("imap-msg-full")
                    .getResultList();
        } finally {
            authentication.end();
        }
    }

    private Collection<BaseImapEvent> makeEvents(ImapFolder cubaFolder,
                                                 List<ImapMessage> cubaMessages,
                                                 Collection<IMAPFolder> otherFolders,
                                                 IMAPFolder imapFolder) {
        try {
            List<IMAPMessage> imapMessages = imapHelper.getAllByUids(
                    imapFolder,
                    cubaMessages.stream().mapToLong(ImapMessage::getMsgUid).toArray(),
                    cubaFolder.getMailBox()
            );
            log.trace("[updating messages flags for {}]batch messages from db: {}, from IMAP server: {}",
                    taskDescription(), cubaFolder, cubaMessages, imapMessages);

            Map<Long, IMAPMessage> msgsByUid = new HashMap<>(imapMessages.size());
            for (IMAPMessage imapMessage : imapMessages) {
                msgsByUid.put(imapFolder.getUID(imapMessage), imapMessage);
            }

            Collection<ImapMessage> missedMessages = cubaMessages.stream()
                    .filter(message -> !msgsByUid.containsKey(message.getMsgUid()))
                    .collect(Collectors.toList());
            return handleMissedMessages(cubaFolder, missedMessages, otherFolders);
        } catch (MessagingException e) {
            throw new ImapException(e);
        }
    }

    private Collection<BaseImapEvent> handleMissedMessages(ImapFolder cubaFolder,
                                                           Collection<ImapMessage> missedMessages,
                                                           Collection<IMAPFolder> otherFolders) {
        if (missedMessages.isEmpty()) {
            return Collections.emptyList();
        }

        log.debug("Handle missed messages {} for folder {}", missedMessages, cubaFolder);

        Collection<BaseImapEvent> result = new ArrayList<>(missedMessages.size());

        result.addAll(missedMessages.stream()
                .filter(msg -> msg.getMessageId() == null)
                .map(EmailDeletedImapEvent::new)
                .collect(Collectors.toList())
        );
        if (!result.isEmpty()) {
            log.debug("messages {} don't contain Message-ID header, they will be treated as deleted", result);
        }
        Map<String, ImapMessage> messagesByIds = missedMessages.stream()
                .filter(msg -> msg.getMessageId() != null)
                .collect(Collectors.toMap(ImapMessage::getMessageId, Function.identity()));

        // (in Gmail we have 'All Mails' folder which contains all messages,
        // probably better to add parameter for such folder in mailbox or more general
        // folders param to exclude them from move event flow)

        ImapMailBox mailBox = cubaFolder.getMailBox();
        otherFolders.stream()
                .filter(folder -> folder.getFullName().equals(mailBox.getTrashFolderName()))
                .findFirst()
                .ifPresent(trashFolder -> findMessageIds(trashFolder, messagesByIds.keySet()).forEach(messageId -> {
                    ImapMessage imapMessage = messagesByIds.remove(messageId);
                    result.add(new EmailDeletedImapEvent(imapMessage));
                }));

        Map<String, BaseImapEvent> movedMessages = findMessagesInOtherFolders(
                otherFolders.stream().filter(folder -> !folder.getFullName().equals(mailBox.getTrashFolderName())),
                messagesByIds
        );

        log.debug("Handle missed messages for folder {}. Found in other folders: {}, deleted: {}",
                cubaFolder, movedMessages.keySet(), result);

        result.addAll(movedMessages.values());

        authentication.begin();
        try (Transaction tx = persistence.createTransaction()) {
            EntityManager em = persistence.getEntityManager();
            missedMessages.forEach(em::remove);
            recalculateMessageNums(
                    em,
                    cubaFolder,
                    missedMessages.stream()
                            .map(ImapMessage::getMsgNum)
                            .sorted()
                            .collect(Collectors.toList())
            );
            tx.commit();
            return result;
        } finally {
            authentication.end();
        }
    }

    private void recalculateMessageNums(EntityManager em, ImapFolder cubaFolder, List<Integer> ascMessageNums) {
        for (int i = 0; i < ascMessageNums.size(); i++) {
            String queryString = "update imapcomponent$ImapMessage m set m.msgNum = m.msgNum-" + (i + 1) +
                    " where m.folder.id = :mailFolderId and m.msgNum > :msgNum";
            if (i < ascMessageNums.size() - 1) {
                queryString += " and m.msgNum < :topMsgNum";
            }
            Query query = em.createQuery(queryString)
                    .setParameter("mailFolderId", cubaFolder)
                    .setParameter("msgNum", ascMessageNums.get(i));
            if (i < ascMessageNums.size() - 1) {
                query.setParameter("topMsgNum", ascMessageNums.get(i + 1));
            }
            query.executeUpdate();
        }

    }

    private Map<String, BaseImapEvent> findMessagesInOtherFolders(Stream<IMAPFolder> otherFolders,
                                                                  Map<String, ImapMessage> missedMessagesByIds) {
        return otherFolders.parallel()
                    .flatMap(imapFolder -> {
                        Collection<String> foundMessageIds = findMessageIds(imapFolder, missedMessagesByIds.keySet());
                        return foundMessageIds.stream().map(id -> new Pair<>(id, imapFolder.getFullName()));
                    })
                    .collect(Collectors.toMap(
                            Pair::getFirst,
                            pair -> {
                                ImapMessage message = missedMessagesByIds.get(pair.getFirst());
                                return new EmailMovedImapEvent(message, pair.getSecond());
                            },
                            (event1, event2) -> event2
                    ));
    }

    private Collection<String> findMessageIds(IMAPFolder imapFolder, Collection<String> messageIds) {
        boolean close = false;
        try {
            if (!imapFolder.isOpen()) {
                imapFolder.open(Folder.READ_ONLY);
                close = true;
            }
            List<IMAPMessage> messages = imapHelper.searchMessageIds(
                    imapFolder,
                    new OrTerm(messageIds.stream().map(MessageIDTerm::new).toArray(SearchTerm[]::new))
            );
            if (messages.isEmpty()) {
                return Collections.emptyList();
            }

            Collection<String> foundIds = new ArrayList<>(messages.size());
            for (IMAPMessage message : messages) {
                foundIds.add(message.getMessageID());
            }

            return foundIds;
        } catch (MessagingException e) {
            throw new ImapException(e);
        } finally {
            if (close && imapFolder.isOpen()) {
                try {
                    imapFolder.close(false);
                } catch (MessagingException e) {
                    log.warn("can't close folder " + imapFolder, e);
                }
            }
        }
    }

    private String taskDescription() {
        return "moved and deleted messages";
    }

}
