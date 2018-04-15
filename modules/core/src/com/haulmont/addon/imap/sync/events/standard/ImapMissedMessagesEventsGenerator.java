package com.haulmont.addon.imap.sync.events.standard;

import com.haulmont.addon.imap.api.ImapAPI;
import com.haulmont.addon.imap.config.ImapConfig;
import com.haulmont.addon.imap.core.ImapHelper;
import com.haulmont.addon.imap.core.Task;
import com.haulmont.addon.imap.dto.ImapFolderDto;
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

@Component("imapcomponent_ImapMissedMessagesEventsGenerator")
public class ImapMissedMessagesEventsGenerator {

    private final static Logger log = LoggerFactory.getLogger(ImapMissedMessagesEventsGenerator.class);

    private static final String TASK_DESCRIPTION = "moved and deleted messages";

    private final ImapAPI imapAPI;
    private final ImapHelper imapHelper;
    private final Authentication authentication;
    private final Persistence persistence;
    private final ImapConfig imapConfig;

    @SuppressWarnings("CdiInjectionPointsInspection")
    @Inject
    public ImapMissedMessagesEventsGenerator(ImapAPI imapAPI,
                                             ImapHelper imapHelper,
                                             Authentication authentication,
                                             Persistence persistence,
                                             ImapConfig imapConfig) {
        this.imapAPI = imapAPI;
        this.imapHelper = imapHelper;
        this.authentication = authentication;
        this.persistence = persistence;
        this.imapConfig = imapConfig;
    }

    Collection<BaseImapEvent> generate(@Nonnull ImapFolder cubaFolder) {
        Collection<IMAPFolder> otherFolders = getOtherFolders(cubaFolder);
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

    Collection<BaseImapEvent> generate(@Nonnull ImapFolder cubaFolder,
                                       @Nonnull Collection<IMAPMessage> missedMessages) {
        if (missedMessages.isEmpty()) {
            return Collections.emptyList();
        }

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
                            missedMessages.stream()
                                    .map(Message::getMessageNumber)
                                    .collect(Collectors.toList())
                    )
                    .setViewName("imap-msg-full")
                    .getResultList();
        } finally {
            authentication.end();
        }
        return generate(
                cubaFolder, messages, otherFolders, (IMAPFolder) missedMessages.iterator().next().getFolder()
        );
    }

    private Collection<BaseImapEvent> generate(ImapFolder cubaFolder,
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
                    TASK_DESCRIPTION, cubaFolder, cubaMessages, imapMessages);

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

    @SuppressWarnings("WeakerAccess")
    protected Collection<BaseImapEvent> handleMissedMessages(ImapFolder cubaFolder,
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

    @SuppressWarnings("WeakerAccess")
    protected Collection<IMAPFolder> getOtherFolders(ImapFolder cubaFolder) {
        ImapMailBox mailBox = getMailbox(cubaFolder.getMailBox().getId());
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

    private ImapMailBox getMailbox(UUID id) {
        authentication.begin();
        try (Transaction ignored = persistence.createTransaction()) {
            EntityManager em = persistence.getEntityManager();
            return em.createQuery(
                    "select distinct b from imapcomponent$ImapMailBox b where b.id = :id",
                    ImapMailBox.class
            ).setParameter("id", id).setViewName("imap-mailbox-edit").getSingleResult();
        } finally {
            authentication.end();
        }
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

}
