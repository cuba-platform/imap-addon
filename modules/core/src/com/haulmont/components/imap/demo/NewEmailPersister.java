package com.haulmont.components.imap.demo;

import com.haulmont.components.imap.dto.MailMessageDto;
import com.haulmont.components.imap.entity.MailBox;
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
import java.util.*;

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

    private Timer timer;

    private volatile List<ImapService.MessageRef> messageRefs = new ArrayList<>();

    @EventListener
    public void handleNewEvent(NewEmailEvent event) {
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
                if (timer != null) {
                    timer.cancel();
                }
                synchronized (messageRefs) {
                    ImapService.MessageRef ref = new ImapService.MessageRef();
                    ref.setMailBox(event.getMailBox());
                    ref.setFolderName(event.getFolderName());
                    ref.setUid(event.getMessageId());
                    messageRefs.add(ref);
                    timer = new Timer();
                    if (messageRefs.size() > 20) {
                        timer.schedule(flushTask(), 0);
                    } else {
                        timer.schedule(flushTask(), 5_000);
                    }
                }
            }
        }
    }

    private TimerTask flushTask() {
        return new TimerTask() {
            @Override
            public void run() {
                authentication.begin();
                try {
                    flush();
                } finally {
                    authentication.end();
                }
            }
        };
    }

    private void flush() {
        List<MailMessageDto> dtos;
        synchronized (messageRefs) {
            try {
                dtos = service.fetchMessages(messageRefs);
                messageRefs.clear();
            } catch (MessagingException e) {
                throw new RuntimeException("Can't handle new message event", e);
            }

        }
        authentication.begin();
        try (Transaction tx = persistence.createTransaction()) {
            EntityManager em = persistence.getEntityManager();
            Map<UUID, MailBox> mailBoxes = new HashMap<>();
            for (MailMessageDto dto : dtos) {
                MailMessage mailMessage = metadata.create(MailMessage.class);
                MailMessage.fillMessage(mailMessage, dto, () -> {
                    MailBox mailBox = mailBoxes.get(dto.getMailBoxId());
                    if (mailBox == null) {
                        mailBox = em.createQuery(
                                "select mb from mailcomponent$MailBox mb where mb.id = :mailBoxId",
                                MailBox.class
                        ).setParameter("mailBoxId", dto.getMailBoxId()).getFirstResult();
                        if (mailBox == null) {
                            return null;
                        }
                        mailBoxes.put(dto.getMailBoxId(), mailBox);
                    }
                    return mailBox;
                });
                if (mailMessage.getMailBox() == null) {
                    continue;
                }
                em.persist(mailMessage);
            }
            tx.commit();
        } finally {
            authentication.end();
        }
    }
}
