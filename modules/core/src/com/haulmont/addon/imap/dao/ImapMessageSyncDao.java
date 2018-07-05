package com.haulmont.addon.imap.dao;

import com.haulmont.addon.imap.config.ImapConfig;
import com.haulmont.addon.imap.entity.ImapMessage;
import com.haulmont.addon.imap.entity.ImapMessageSync;
import com.haulmont.addon.imap.entity.ImapSyncStatus;
import com.haulmont.cuba.core.EntityManager;
import com.haulmont.cuba.core.Persistence;
import com.haulmont.cuba.core.Transaction;
import com.haulmont.cuba.core.TypedQuery;
import com.haulmont.cuba.core.global.Metadata;
import org.springframework.stereotype.Component;

import javax.inject.Inject;
import javax.mail.Flags;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

@Component("imap_MessageSyncDao")
public class ImapMessageSyncDao {

    private final Persistence persistence;
    private final ImapConfig imapConfig;
    private final Metadata metadata;

    @SuppressWarnings("CdiInjectionPointsInspection")
    @Inject
    public ImapMessageSyncDao(Persistence persistence,
                   @SuppressWarnings("SpringJavaInjectionPointsAutowiringInspection") ImapConfig imapConfig,
                   Metadata metadata) {
        this.persistence = persistence;
        this.imapConfig = imapConfig;
        this.metadata = metadata;
    }

    public Collection<ImapMessage> findMessagesWithSyncStatus(UUID folderId, ImapSyncStatus status) {
        try (Transaction ignored = persistence.createTransaction()) {
            EntityManager em = persistence.getEntityManager();
            TypedQuery<ImapMessage> query = em.createQuery(
                    "select m from imap$Message m where m.id in " +
                            "(select ms.message.id from imap$MessageSync ms where ms.folder.id = :folder and ms.status = :status)",
                    ImapMessage.class
            ).setParameter("folder", folderId).setParameter("status", status).setViewName("imap-msg-full");
            return query.getResultList();
        }
    }

    public Collection<ImapMessage> findMessagesWithSyncStatus(UUID folderId, ImapSyncStatus status, Date minUpdateDate, Date maxUpdateDate) {
        try (Transaction ignored = persistence.createTransaction()) {
            EntityManager em = persistence.getEntityManager();
            TypedQuery<ImapMessage> query = em.createQuery(
                    "select m from imap$Message m where m.id in " +
                            "(select ms.message.id from imap$MessageSync ms where ms.folder.id = :folder and ms.status = :status" +
                            " and ms.updateTs >= :minUpdateDate and ms.updateTs < :maxUpdateDate)",
                    ImapMessage.class
            )
                    .setParameter("folder", folderId)
                    .setParameter("status", status)
                    .setParameter("minUpdateDate", minUpdateDate)
                    .setParameter("maxUpdateDate", maxUpdateDate)
                    .setViewName("imap-msg-full");
            return query.getResultList();
        }
    }

    public Collection<ImapMessageSync> findMessagesSyncs(UUID folderId, ImapSyncStatus status) {
        try (Transaction ignored = persistence.createTransaction()) {
            EntityManager em = persistence.getEntityManager();
            TypedQuery<ImapMessageSync> query = em.createQuery(
                    "select ms from imap$MessageSync ms where ms.folder.id = :folder and ms.status = :status",
                    ImapMessageSync.class
            ).setParameter("folder", folderId).setParameter("status", status).setViewName("imap-msg-sync-with-message");
            return query.getResultList();
        }
    }

    public Collection<ImapMessage> findMessagesForSync(UUID folderId) {
        try (Transaction ignored = persistence.createTransaction()) {
            EntityManager em = persistence.getEntityManager();
            TypedQuery<ImapMessage> query = em.createQuery(
                    "select m from imap$Message m where m.folder.id = :folder and m.id not in " +
                            "(select ms.message.id from imap$MessageSync ms)",
                    ImapMessage.class
            ).setParameter("folder", folderId).setViewName("imap-msg-full").setMaxResults(imapConfig.getUpdateBatchSize());
            return query.getResultList();
        }
    }

    public void createSyncForMessages(Collection<ImapMessage> messages, ImapSyncStatus syncStatus) {
        try (Transaction tx = persistence.createTransaction()) {
            EntityManager em = persistence.getEntityManager();
            for (ImapMessage message : messages) {
                if (msgSyncQuery(message, em).getFirstResult() == null) {
                    ImapMessageSync messageSync = metadata.create(ImapMessageSync.class);
                    messageSync.setMessage(message);
                    messageSync.setStatus(syncStatus);
                    messageSync.setFolder(message.getFolder());
                    em.persist(messageSync);
                }
            }

            tx.commit();
        }
    }

    public void updateSyncStatus(ImapMessage message,
                                 ImapSyncStatus syncStatus,
                                 ImapSyncStatus oldStatus,
                                 Flags flags,
                                 Integer foldersToCheck,
                                 String newFolderName) {
        try (Transaction tx = persistence.createTransaction()) {
            EntityManager em = persistence.getEntityManager();

            TypedQuery<ImapMessageSync> query = oldStatus == null
                    ? msgSyncQuery(message, em) : msgSyncQuery(message, oldStatus, em);
            ImapMessageSync messageSync = query.getFirstResult();

            if (messageSync != null) {
                messageSync.setStatus(syncStatus);
                if (flags != null) {
                    messageSync.setImapFlags(flags);
                }
                if (foldersToCheck != null) {
                    messageSync.setFoldersToCheck(foldersToCheck);
                }
                if (newFolderName != null) {
                    messageSync.setNewFolderName(newFolderName);
                }
                em.persist(messageSync);

                tx.commit();
            }
        }
    }

    public ImapMessageSync trackCheckedFolder(ImapMessage message) {
        try (Transaction tx = persistence.createTransaction()) {
            EntityManager em = persistence.getEntityManager();
            em.createQuery("update imap$MessageSync ms where ms.message.id = :msgId set ms.checkedFolders = ms.checkedFolders + 1)")
                    .setParameter("msgId", message)
                    .executeUpdate();

            tx.commit();

            return msgSyncQuery(message, em).getSingleResult();
        }
    }

    private TypedQuery<ImapMessageSync> msgSyncQuery(ImapMessage message, EntityManager em) {
        return em.createQuery(
                "select ms from imap$MessageSync ms where ms.message.id = :msgId", ImapMessageSync.class
        ).setParameter("msgId", message);
    }

    private TypedQuery<ImapMessageSync> msgSyncQuery(ImapMessage message, ImapSyncStatus status, EntityManager em) {
        return em.createQuery(
                "select ms from imap$MessageSync ms where ms.message.id = :msgId and ms.status = :status", ImapMessageSync.class
        ).setParameter("msgId", message).setParameter("status", status);
    }

    public void removeMessagesSyncs(Collection<UUID> messageIds) {
        try (Transaction tx = persistence.createTransaction()) {
            EntityManager em = persistence.getEntityManager();
            final AtomicInteger counter = new AtomicInteger(0);
            Collection<List<UUID>> partitions = messageIds.stream()
                    .collect(Collectors.groupingBy(it -> counter.getAndIncrement() / 1000))
                    .values();
            for (List<UUID> messageIdsPartition : partitions) {
                em.createQuery("delete from imap$MessageSync ms where ms.message.id in :msgIds")
                        .setParameter("msgIds", messageIdsPartition)
                        .executeUpdate();
            }

            tx.commit();
        }
    }

    public void removeOldSyncs(UUID folderId, Date minUpdateDate) {
        try (Transaction tx = persistence.createTransaction()) {
            EntityManager em = persistence.getEntityManager();
            em.createQuery("delete from imap$MessageSync ms where ms.folder.id = :folderId and ms.updateTs < :minUpdateDate")
                    .setParameter("folderId", folderId)
                    .setParameter("minUpdateDate", minUpdateDate)
                    .executeUpdate();

            tx.commit();
        }
    }
}
