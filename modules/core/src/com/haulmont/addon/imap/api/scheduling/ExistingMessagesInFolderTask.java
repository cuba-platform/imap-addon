package com.haulmont.addon.imap.api.scheduling;

import com.haulmont.addon.imap.core.FolderTask;
import com.haulmont.addon.imap.core.MsgHeader;
import com.haulmont.addon.imap.entity.ImapFolder;
import com.haulmont.addon.imap.entity.ImapMailBox;
import com.haulmont.addon.imap.entity.ImapMessage;
import com.haulmont.addon.imap.events.BaseImapEvent;
import com.haulmont.cuba.core.EntityManager;
import com.haulmont.cuba.core.Transaction;
import com.sun.mail.imap.IMAPFolder;

import javax.mail.MessagingException;
import java.util.*;
import java.util.stream.Collectors;

abstract class ExistingMessagesInFolderTask extends AbstractFolderTask {

    ExistingMessagesInFolderTask(ImapMailBox mailBox, ImapFolder cubaFolder, ImapScheduling scheduling) {
        super(mailBox, cubaFolder, scheduling);
    }

    @Override
    List<BaseImapEvent> makeEvents() {
        int batchSize = scheduling.config.getUpdateBatchSize();
        long windowSize = Math.min(
                getCount(),
                (mailBox.getUpdateSliceSize() != null) ? Math.max(mailBox.getUpdateSliceSize(), batchSize) : batchSize
        );
        log.debug("[{} for {}]handle events for existing messages using windowSize {} and batchSize {}",
                taskDescription(), cubaFolder, windowSize, batchSize);
        List<BaseImapEvent> modificationEvents = new ArrayList<>((int) windowSize);
        for (int i = 0; i < windowSize; i += batchSize) {
            int thisBatchSize = (int) Math.min(batchSize, windowSize - i);
            log.trace("[{} for {}]handle batch#{} with size {}",
                    taskDescription(), cubaFolder, i, thisBatchSize);

            List<BaseImapEvent> batchEvents = scheduling.imapHelper.doWithFolder(
                    mailBox,
                    cubaFolder.getName(),
                    new FolderTask<>(
                            taskDescription(),
                            true,
                            false,
                            f -> handleBatch(f, batchSize)
                    )
            );

            modificationEvents.addAll(batchEvents);
        }
        modificationEvents.addAll(trailEvents());
        return modificationEvents;
    }

    private long getCount() {
        scheduling.authentication.begin();
        try (Transaction ignored = scheduling.persistence.createTransaction()) {
            EntityManager em = scheduling.persistence.getEntityManager();
            return ((Number) em.createQuery("select count(m.id) from imapcomponent$ImapMessage m where m.folder.id = :mailFolderId")
                    .setParameter("mailFolderId", cubaFolder)
                    .getSingleResult()).longValue();
        } finally {
            scheduling.authentication.end();
        }
    }

    private List<BaseImapEvent> handleBatch(IMAPFolder folder, int count) throws MessagingException {
        scheduling.authentication.begin();
        try (Transaction tx = scheduling.persistence.createTransaction()) {
            EntityManager em = scheduling.persistence.getEntityManager();

            List<ImapMessage> messages = em.createQuery(
                    "select m from imapcomponent$ImapMessage m where m.folder.id = :mailFolderId order by m.updateTs asc nulls first",
                    ImapMessage.class
            )
                    .setParameter("mailFolderId", cubaFolder)
                    .setMaxResults(count)
                    .setViewName("imap-msg-full")
                    .getResultList();

            List<MsgHeader> imapMessages = scheduling.imapHelper.getAllByUids(
                    folder, messages.stream().mapToLong(ImapMessage::getMsgUid).toArray()
            );
            log.trace("[{} for {}]batch messages from db: {}, from IMAP server: {}",
                    taskDescription(), cubaFolder, messages, imapMessages);

            Map<Long, MsgHeader> headersByUid = new HashMap<>(imapMessages.size());
            for (MsgHeader msg : imapMessages) {
                headersByUid.put(msg.getUid(), msg);
            }

            List<BaseImapEvent> events = messages.stream()
                    .flatMap(msg -> handleMessage(em, msg, headersByUid).stream())
                    .collect(Collectors.toList());
            tx.commit();

            return events;

        } finally {
            scheduling.authentication.end();
        }

    }

    protected abstract List<BaseImapEvent> handleMessage(EntityManager em, ImapMessage msg, Map<Long, MsgHeader> msgsByUid);

    protected abstract String taskDescription();

    protected Collection<BaseImapEvent> trailEvents() {
        return Collections.emptyList();
    }
}
