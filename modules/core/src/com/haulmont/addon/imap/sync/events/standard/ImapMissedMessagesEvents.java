package com.haulmont.addon.imap.sync.events.standard;

import com.haulmont.addon.imap.config.ImapConfig;
import com.haulmont.addon.imap.core.ImapHelper;
import com.haulmont.addon.imap.core.Task;
import com.haulmont.addon.imap.dao.ImapDao;
import com.haulmont.addon.imap.entity.ImapFolder;
import com.haulmont.addon.imap.entity.ImapMailBox;
import com.haulmont.addon.imap.entity.ImapMessage;
import com.haulmont.addon.imap.events.BaseImapEvent;
import com.haulmont.addon.imap.events.EmailDeletedImapEvent;
import com.haulmont.addon.imap.events.EmailMovedImapEvent;
import com.haulmont.addon.imap.exception.ImapException;
import com.haulmont.bali.datastruct.Pair;
import com.haulmont.cuba.core.EntityManager;
import com.haulmont.cuba.core.Persistence;
import com.haulmont.cuba.core.Query;
import com.haulmont.cuba.core.Transaction;
import com.haulmont.cuba.security.app.Authentication;
import com.sun.mail.imap.IMAPFolder;
import com.sun.mail.imap.IMAPMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import javax.annotation.Nonnull;
import javax.inject.Inject;
import javax.mail.Message;
import javax.mail.MessagingException;
import javax.mail.search.MessageIDTerm;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Component("imap_MissedMessagesEvents")
public class ImapMissedMessagesEvents {

    private final static Logger log = LoggerFactory.getLogger(ImapMissedMessagesEvents.class);

    private static final String TASK_DESCRIPTION = "moved and deleted messages";

    private final ImapHelper imapHelper;
    private final Authentication authentication;
    private final Persistence persistence;
    private final ImapDao dao;
    private final ImapConfig imapConfig;

    @SuppressWarnings("CdiInjectionPointsInspection")
    @Inject
    public ImapMissedMessagesEvents(ImapHelper imapHelper,
                                    Authentication authentication,
                                    Persistence persistence,
                                    ImapDao dao,
                                    @SuppressWarnings("SpringJavaInjectionPointsAutowiringInspection") ImapConfig imapConfig) {
        this.imapHelper = imapHelper;
        this.authentication = authentication;
        this.persistence = persistence;
        this.dao = dao;
        this.imapConfig = imapConfig;
    }

    @SuppressWarnings("WeakerAccess")
    protected Collection<BaseImapEvent> generate(@Nonnull ImapFolder cubaFolder) {
        Collection<String> otherFolders = getOtherFolders(cubaFolder);
        return imapHelper.doWithFolder(cubaFolder.getMailBox(), cubaFolder.getName(), new Task<>(
                TASK_DESCRIPTION,
                true,
                imapFolder -> {
                    int i = 0;
                    List<ImapMessage> batchMessages;
                    Collection<BaseImapEvent> imapEvents = new ArrayList<>();
                    //todo: process batches in parallel ?
                    do {
                        batchMessages = getMessages(cubaFolder, i++);

                        imapEvents.addAll(generate(cubaFolder, batchMessages, otherFolders, imapFolder));
                    } while (batchMessages.size() == imapConfig.getUpdateBatchSize());

                    return imapEvents;
                }
        ));
    }

    @SuppressWarnings("WeakerAccess")
    protected Collection<BaseImapEvent> generate(@Nonnull ImapFolder cubaFolder,
                                                 @Nonnull Collection<IMAPMessage> missedMessages) {
        if (missedMessages.isEmpty()) {
            return Collections.emptyList();
        }

        Collection<String> otherFoldersNames = getOtherFolders(cubaFolder);

        Collection<ImapMessage> messages;

        authentication.begin();
        try (Transaction ignored = persistence.createTransaction()) {
            messages = dao.findMessagesByImapNumbers(
                    cubaFolder.getId(),
                    missedMessages.stream().map(Message::getMessageNumber).collect(Collectors.toList())
            );
        } finally {
            authentication.end();
        }
        return generate(
                cubaFolder, messages, otherFoldersNames, (IMAPFolder) missedMessages.iterator().next().getFolder()
        );
    }

    private Collection<BaseImapEvent> generate(ImapFolder cubaFolder,
                                               Collection<ImapMessage> cubaMessages,
                                               Collection<String> otherFoldersNames,
                                               IMAPFolder imapFolder) {
        try {
            List<IMAPMessage> imapMessages = imapHelper.getAllByUIDs(
                    imapFolder,
                    cubaMessages.stream().mapToLong(ImapMessage::getMsgUid).toArray(),
                    cubaFolder.getMailBox()
            );
            log.trace("[updating messages flags for {}]batch messages from db: {}, from IMAP server: {}",
                    TASK_DESCRIPTION, cubaFolder, cubaMessages, imapMessages);

            Map<Long, IMAPMessage> messagesByUid = new HashMap<>(imapMessages.size());
            for (IMAPMessage imapMessage : imapMessages) {
                messagesByUid.put(imapFolder.getUID(imapMessage), imapMessage);
            }

            Collection<ImapMessage> missedMessages = cubaMessages.stream()
                    .filter(message -> !messagesByUid.containsKey(message.getMsgUid()))
                    .collect(Collectors.toList());
            return handleMissedMessages(cubaFolder, missedMessages, otherFoldersNames);
        } catch (MessagingException e) {
            throw new ImapException(e);
        }
    }

    @SuppressWarnings("WeakerAccess")
    protected Collection<BaseImapEvent> handleMissedMessages(ImapFolder cubaFolder,
                                                             Collection<ImapMessage> missedMessages,
                                                             Collection<String> otherFoldersNames) {
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
        otherFoldersNames.stream()
                .filter(folderName -> folderName.equals(mailBox.getTrashFolderName()))
                .findFirst()
                .ifPresent(trashFolder -> {
                    for ( String messageId : findMessageIds(mailBox, trashFolder, messagesByIds.keySet()) ) {
                        ImapMessage imapMessage = messagesByIds.remove(messageId);
                        result.add(new EmailDeletedImapEvent(imapMessage));
                    }
                });

        Map<String, BaseImapEvent> movedMessages = findMessagesInOtherFolders(
                mailBox,
                otherFoldersNames.stream().filter(folderName -> !folderName.equals(mailBox.getTrashFolderName())),
                messagesByIds
        );

        log.debug("Handle missed messages for folder {}. Found in other folders: {}, deleted: {}",
                cubaFolder, movedMessages.keySet(), result);

        result.addAll(movedMessages.values());

        authentication.begin();
        try (Transaction tx = persistence.createTransaction()) {
            EntityManager em = persistence.getEntityManager();
            missedMessages.forEach(em::remove);
            recalculateMessageNumbers(
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

    @SuppressWarnings("WeakerAccess")
    protected Collection<String> getOtherFolders(ImapFolder cubaFolder) {
        ImapMailBox mailBox = getMailbox(cubaFolder.getMailBox().getId());
        if (log.isTraceEnabled()) {
            log.trace("processable folders: {}",
                    mailBox.getProcessableFolders().stream().map(ImapFolder::getName).collect(Collectors.toList())
            );
        }
        List<String> otherFoldersNames = mailBox.getProcessableFolders().stream()
                .filter(f -> !f.getName().equals(cubaFolder.getName()))
                .map(ImapFolder::getName)
                .collect(Collectors.toList());
        if (mailBox.getTrashFolderName() != null && !mailBox.getTrashFolderName().equals(cubaFolder.getName())) {
            otherFoldersNames.add(mailBox.getTrashFolderName());
        }
        log.debug("Missed messages task will use folders {} to trigger MOVE event", otherFoldersNames);
        return otherFoldersNames;
        /*String[] folderNames = otherFoldersNames.toArray(new String[0]);
        if (folderNames.length == 0) {
            return Collections.emptyList();
        }
        Collection<IMAPFolder> otherFoldersNames = imapAPI.fetchFolders(mailBox, folderNames).stream()
                .filter(f -> Boolean.TRUE.equals(f.getCanHoldMessages()))
                .map(ImapFolderDto::getImapFolder)
                .collect(Collectors.toList());

        return otherFoldersNames;*/
    }

    private ImapMailBox getMailbox(UUID id) {
        authentication.begin();
        try {
            return dao.findMailBox(id);
        } finally {
            authentication.end();
        }
    }

    private List<ImapMessage> getMessages(ImapFolder cubaFolder, int iterationNum) {
        authentication.begin();
        try (Transaction ignored = persistence.createTransaction()) {
            EntityManager em = persistence.getEntityManager();

            return em.createQuery(
                    "select m from imap$Message m where m.folder.id = :mailFolderId order by m.msgNum",
                    ImapMessage.class
            )
                    .setParameter("mailFolderId", cubaFolder)
                    .setFirstResult(iterationNum * imapConfig.getUpdateBatchSize())
                    .setMaxResults(imapConfig.getUpdateBatchSize())
                    .setViewName("imap-msg-full")
                    .getResultList();
        } finally {
            authentication.end();
        }
    }

    private void recalculateMessageNumbers(EntityManager em, ImapFolder cubaFolder, List<Integer> ascMessageNumbers) {
        for (int i = 0; i < ascMessageNumbers.size(); i++) {
            String queryString = "update imap$Message m set m.msgNum = m.msgNum-" + (i + 1) +
                    " where m.folder.id = :mailFolderId and m.msgNum > :msgNum";
            if (i < ascMessageNumbers.size() - 1) {
                queryString += " and m.msgNum < :topMsgNum";
            }
            Query query = em.createQuery(queryString)
                    .setParameter("mailFolderId", cubaFolder)
                    .setParameter("msgNum", ascMessageNumbers.get(i));
            if (i < ascMessageNumbers.size() - 1) {
                query.setParameter("topMsgNum", ascMessageNumbers.get(i + 1));
            }
            query.executeUpdate();
        }

    }

    private Map<String, BaseImapEvent> findMessagesInOtherFolders(ImapMailBox mailBox,
                                                                  Stream<String> otherFoldersNames,
                                                                  Map<String, ImapMessage> missedMessagesByIds) {
        return otherFoldersNames.parallel()
                .flatMap(folderName -> {
                    Collection<String> foundMessageIds = findMessageIds(mailBox, folderName, missedMessagesByIds.keySet());
                    return foundMessageIds.stream().map(id -> new Pair<>(id, folderName));
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

    private Collection<String> findMessageIds(ImapMailBox mailBox, String folderName, Collection<String> messageIds) {
        return imapHelper.doWithFolder(
                mailBox,
                folderName,
                new Task<>("find messages ids", true, imapFolder -> {
                    List<IMAPMessage> messages = new ArrayList<>(messageIds.size());
                    for (String messageId : messageIds) {
                        messages.addAll(imapHelper.searchMessageIds(
                                imapFolder,
                                new MessageIDTerm(messageId)
                        ));
                    }
                    //todo: GreenMail can't handle properly OR combination of MessageID terms, need to check out with real IMAP servers to avoid extra hits
            /*List<IMAPMessage> messages = imapHelper.searchMessageIds(
                    imapFolder,
                    new OrTerm(messageIds.stream().map(MessageIDTerm::new).toArray(SearchTerm[]::new))
            );*/
                    if (messages.isEmpty()) {
                        return Collections.emptyList();
                    }

                    Collection<String> foundIds = new ArrayList<>(messages.size());
                    for (IMAPMessage message : messages) {
                        foundIds.add(message.getMessageID());
                    }

                    return foundIds;

                })
        );
    }

}
