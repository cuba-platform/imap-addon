package com.haulmont.addon.imap.dao;

import com.haulmont.addon.imap.entity.*;
import com.haulmont.cuba.core.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import javax.inject.Inject;
import java.util.Collection;
import java.util.UUID;

@Component("imap_Dao")
public class ImapDao {

    private final static Logger log = LoggerFactory.getLogger(ImapDao.class);

    private final Persistence persistence;

    @SuppressWarnings("CdiInjectionPointsInspection")
    @Inject
    public ImapDao(Persistence persistence) {
        this.persistence = persistence;
    }

    public Collection<ImapMailBox> findMailBoxes() {
        try (Transaction ignored = persistence.createTransaction()) {
            EntityManager em = persistence.getEntityManager();
            TypedQuery<ImapMailBox> query = em.createQuery(
                    "select distinct b from imap$MailBox b",
                    ImapMailBox.class
            ).setViewName("imap-mailbox-edit");

            return query.getResultList();
        }
    }

    public ImapMailBox findMailBox(UUID id) {
        try (Transaction ignored = persistence.createTransaction()) {
            EntityManager em = persistence.getEntityManager();
            return em.createQuery(
                    "select distinct b from imap$MailBox b where b.id = :id",
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
                    "select f from imap$Folder f where f.id = :id",
                    ImapFolder.class
            ).setParameter("id", folderId).setViewName("imap-folder-full");
            return query.getFirstResult();
        }
    }

    public ImapMessage findMessageById(UUID messageId) {
        try (Transaction ignored = persistence.createTransaction()) {
            EntityManager em = persistence.getEntityManager();
            return em.find(ImapMessage.class, messageId, "imap-msg-full");
        }
    }

    @SuppressWarnings("UnusedReturnValue")
    public ImapMessage findMessageByUid(UUID mailFolderId, long messageUid) {
        try (Transaction ignored = persistence.createTransaction()) {
            EntityManager em = persistence.getEntityManager();
            return em.createQuery(
                    "select m from imap$Message m " +
                            "where m.msgUid = :msgUid and m.folder.id = :mailFolderId",
                    ImapMessage.class)
                    .setParameter("mailFolderId", mailFolderId)
                    .setParameter("msgUid", messageUid)
                    .setViewName("imap-msg-full")
                    .getFirstResult();
        }
    }

    public ImapMessage findMessageByImapMessageId(UUID mailBoxId, String imapMessageId) {
        try (Transaction ignored = persistence.createTransaction()) {
            EntityManager em = persistence.getEntityManager();
            return em.createQuery(
                    "select m from imap$Message m where m.messageId = :imapMessageId and " +
                            "m.folder.id in (select f.id from imap$Folder f where f.mailBox.id = :mailBoxId)",
                    ImapMessage.class)
                    .setParameter("mailBoxId", mailBoxId)
                    .setParameter("imapMessageId", imapMessageId)
                    .setViewName("imap-msg-full")
                    .getFirstResult();
        }
    }

    public Collection<ImapMessageAttachment> findAttachments(UUID messageId) {
        try (Transaction ignored = persistence.createTransaction()) {
            EntityManager em = persistence.getEntityManager();
            TypedQuery<ImapMessageAttachment> query = em.createQuery(
                    "select a from imap$MessageAttachment a where a.imapMessage.id = :msg",
                    ImapMessageAttachment.class
            ).setParameter("msg", messageId).setViewName("imap-msg-attachment-full");
            return query.getResultList();
        }
    }

    public void saveMessage(ImapMessage message) {
        try (Transaction tx = persistence.createTransaction()) {
            EntityManager em = persistence.getEntityManager();
            em.merge(message);
            tx.commit();
        }
    }

    public void saveAttachments(ImapMessage msg, Collection<ImapMessageAttachment> attachments) {
        try (Transaction tx = persistence.createTransaction()) {
            log.trace("storing {} for message {} and mark loaded", attachments, msg);
            EntityManager em = persistence.getEntityManager();
            for (ImapMessageAttachment attachment : attachments) {
                attachment.setImapMessage(msg);
                em.persist(attachment);
            }
            em.createQuery("update imap$Message m set m.attachmentsLoaded = true where m.id = :msg")
                    .setParameter("msg", msg.getId()).executeUpdate();
            tx.commit();
        }
    }
}
