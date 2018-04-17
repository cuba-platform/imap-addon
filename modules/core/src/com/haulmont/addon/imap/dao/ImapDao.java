package com.haulmont.addon.imap.dao;

import com.haulmont.addon.imap.api.ImapFlag;
import com.haulmont.addon.imap.entity.*;
import com.haulmont.cuba.core.*;
import com.haulmont.cuba.core.global.PersistenceHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import javax.inject.Inject;
import javax.mail.Flags;
import java.util.Collection;
import java.util.Date;
import java.util.UUID;

@Component("imapcomponent_ImapDao")
public class ImapDao {

    private final static Logger log = LoggerFactory.getLogger(ImapDao.class);

    private final Persistence persistence;
    private final PersistenceTools persistenceTools;

    @SuppressWarnings("CdiInjectionPointsInspection")
    @Inject
    public ImapDao(Persistence persistence, PersistenceTools persistenceTools) {
        this.persistence = persistence;
        this.persistenceTools = persistenceTools;
    }

    public Collection<ImapMailBox> findMailBoxes() {
        try (Transaction ignored = persistence.createTransaction()) {
            EntityManager em = persistence.getEntityManager();
            TypedQuery<ImapMailBox> query = em.createQuery(
                    "select distinct b from imapcomponent$ImapMailBox b",
                    ImapMailBox.class
            ).setViewName("imap-mailbox-edit");

            return query.getResultList();
        }
    }

    public ImapMailBox findMailBox(UUID id) {
        try (Transaction ignored = persistence.createTransaction()) {
            EntityManager em = persistence.getEntityManager();
            return em.createQuery(
                    "select distinct b from imapcomponent$ImapMailBox b where b.id = :id",
                    ImapMailBox.class
            ).setParameter("id", id).setViewName("imap-mailbox-edit").getSingleResult();
        }
    }

    public String getPersistedPassword(ImapMailBox mailBox) {
        try (Transaction ignored = persistence.createTransaction()) {
            EntityManager em = persistence.getEntityManager();
            ImapSimpleAuthentication persisted = em.find(ImapSimpleAuthentication.class, mailBox.getAuthentication().getId());
            return persisted != null ? persisted.getPassword() : null;
        }
    }

    public ImapFolder findFolder(UUID folderId) {
        try (Transaction ignored = persistence.createTransaction()) {
            EntityManager em = persistence.getEntityManager();
            TypedQuery<ImapFolder> query = em.createQuery(
                    "select f from imapcomponent$ImapFolder f where f.id = :id",
                    ImapFolder.class
            ).setParameter("id", folderId).setViewName("imap-folder-full");
            return query.getFirstResult();
        }
    }

    public Collection<ImapMessage> findMessagesByImapNums(UUID folderId, Collection<Integer> messageNums) {
        try (Transaction ignored = persistence.createTransaction()) {
            EntityManager em = persistence.getEntityManager();

            return em.createQuery(
                    "select m from imapcomponent$ImapMessage m where m.folder.id = :folderId and m.msgNum in :msgNums",
                    ImapMessage.class)
                    .setParameter("mailFolderId", folderId)
                    .setParameter("msgNums", messageNums)
                    .setViewName("imap-msg-full")
                    .getResultList();
        }
    }

    public ImapMessage findMessageById(UUID messageId) {
        try (Transaction ignored = persistence.createTransaction()) {
            EntityManager em = persistence.getEntityManager();
            return em.find(ImapMessage.class, messageId, "imap-msg-full");
        }
    }

    public ImapMessage findMessageByImapMessageId(UUID mailBoxId, String imapMessageId) {
        try (Transaction ignored = persistence.createTransaction()) {
            EntityManager em = persistence.getEntityManager();
            return em.createQuery(
                    "select m from imapcomponent$ImapMessage m where m.messageId = :imapMessageId and " +
                            "m.folder.id in (select f.id from imapcomponent$ImapFolder f where f.mailBox.id = :mailBoxId)",
                    ImapMessage.class)
                    .setParameter("mailBoxId", mailBoxId)
                    .setParameter("imapMessageId", imapMessageId)
                    .setViewName("imap-msg-full")
                    .getFirstResult();
        }
    }

    public boolean addFlag(ImapMessage imapMessage, ImapFlag flag, EntityManager em) {
        Flags imapFlags = imapMessage.getImapFlags();
        if (!imapFlags.contains(flag.imapFlags())) {
            imapFlags.add(flag.imapFlags());
            imapMessage.setImapFlags(imapFlags);

            if (PersistenceHelper.isNew(imapMessage)) {
                em.persist(imapMessage);
            } else {
                em.createQuery("update imapcomponent$ImapMessage m set m.updateTs = :updateTs, m.flags = :flags " +
                        "where m.id = :id")
                        .setParameter("updateTs", new Date())
                        .setParameter("flags", imapMessage.getFlags())
                        .setParameter("id", imapMessage.getId())
                        .executeUpdate();
            }
            return true;
        }

        return false;
    }

    public Collection<ImapMessageAttachment> findAttachments(UUID messageId) {
        try (Transaction ignored = persistence.createTransaction()) {
            EntityManager em = persistence.getEntityManager();
            TypedQuery<ImapMessageAttachment> query = em.createQuery(
                    "select a from imapcomponent$ImapMessageAttachment a where a.imapMessage.id = :msg",
                    ImapMessageAttachment.class
            ).setParameter("msg", messageId).setViewName("imap-msg-attachment-full");
            return query.getResultList();
        }
    }

    public void saveAttachments(ImapMessage msg, Collection<ImapMessageAttachment> attachments) {
        try (Transaction tx = persistence.createTransaction()) {
            log.trace("storing {} for message {} and mark loaded", attachments, msg);
            EntityManager em = persistence.getEntityManager();
            attachments.forEach(it -> {
                it.setImapMessage(msg);
                em.persist(it);
            });
            em.createQuery("update imapcomponent$ImapMessage m set m.attachmentsLoaded = true where m.id = :msg")
                    .setParameter("msg", msg.getId()).executeUpdate();
            tx.commit();
        }
    }
}
