package com.haulmont.components.imap.demo;

import com.haulmont.components.imap.dto.MailMessageDto;
import com.haulmont.components.imap.entity.demo.MailMessage;
import com.haulmont.components.imap.events.NewEmailEvent;
import com.haulmont.components.imap.service.ImapService;
import com.haulmont.cuba.core.EntityManager;
import com.haulmont.cuba.core.Persistence;
import com.haulmont.cuba.core.Transaction;
import com.haulmont.cuba.core.global.Metadata;
import com.haulmont.cuba.security.app.Authentication;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import javax.inject.Inject;
import javax.mail.MessagingException;

@Component
public class NewEmailPersister {

    @Inject
    private Authentication authentication;

    @Inject
    private Metadata metadata;

    @Inject
    private Persistence persistence;

    @Inject
    private ImapService service;

    @EventListener
    public void handleNewEvent(NewEmailEvent event) {
        authentication.begin();
        try (Transaction tx = persistence.createTransaction()) {
            EntityManager em = persistence.getEntityManager();
            int sameUids = em.createQuery(
                    "select m from mailcomponent$MailMessage m where m.messageUid = :uid and m.mailBox.id = :mailBoxId"
            )
                    .setParameter("uid", event.getMessageId())
                    .setParameter("mailBoxId", event.getMailBox())
                    .getResultList()
                    .size();
            if (sameUids == 0) {
                MailMessage mailMessage = metadata.create(MailMessage.class);
                mailMessage.setMessageUid(event.getMessageId());
                mailMessage.setMailBox(event.getMailBox());
                mailMessage.setFolderName(event.getFolderName());
                MailMessageDto dto = service.fetchMessage(event.getMailBox(), event.getFolderName(), event.getMessageId());
                mailMessage.setDate(dto.getDate());
                mailMessage.setSubject(dto.getSubject());
                mailMessage.setFrom(dto.getFrom());
                mailMessage.setToList(dto.getToList().toString());
                mailMessage.setBccList(dto.getBccList().toString());
                mailMessage.setCcList(dto.getCcList().toString());
                mailMessage.setFlagsList(dto.getFlags().toString());
                em.persist(mailMessage);
                tx.commit();
            }
        } catch (MessagingException e) {
            throw new RuntimeException("Can't handle new message event", e);
        } finally {
            authentication.end();
        }
    }
}
