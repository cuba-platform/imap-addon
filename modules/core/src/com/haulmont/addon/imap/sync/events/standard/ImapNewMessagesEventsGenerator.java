package com.haulmont.addon.imap.sync.events.standard;

import com.haulmont.addon.imap.config.ImapConfig;
import com.haulmont.addon.imap.core.ImapHelper;
import com.haulmont.addon.imap.core.Task;
import com.haulmont.addon.imap.entity.ImapFolder;
import com.haulmont.addon.imap.entity.ImapMessage;
import com.haulmont.addon.imap.events.NewEmailImapEvent;
import com.haulmont.addon.imap.exception.ImapException;
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

import javax.annotation.Nonnull;
import javax.inject.Inject;
import javax.mail.Flags;
import javax.mail.Message;
import javax.mail.MessagingException;
import javax.mail.search.FlagTerm;
import javax.mail.search.NotTerm;
import java.util.*;

@Component("imapcomponent_ImapNewMessagesEventsGenerator")
public class ImapNewMessagesEventsGenerator {
    private final static Logger log = LoggerFactory.getLogger(ImapNewMessagesEventsGenerator.class);

    private final ImapHelper imapHelper;
    private final ImapConfig imapConfig;
    private final Authentication authentication;
    private final Persistence persistence;
    private final Metadata metadata;

    @SuppressWarnings("CdiInjectionPointsInspection")
    @Inject
    public ImapNewMessagesEventsGenerator(ImapHelper imapHelper,
                                          ImapConfig imapConfig,
                                          Authentication authentication,
                                          Persistence persistence,
                                          Metadata metadata) {
        this.imapHelper = imapHelper;
        this.imapConfig = imapConfig;
        this.authentication = authentication;
        this.persistence = persistence;
        this.metadata = metadata;
    }

    @SuppressWarnings("WeakerAccess")
    protected Collection<NewEmailImapEvent> generate(@Nonnull ImapFolder cubaFolder) {
        return imapHelper.doWithFolder(cubaFolder.getMailBox(), cubaFolder.getName(), new Task<>(
                "fetch new messages",
                true,
                imapFolder -> generate(cubaFolder, imapFolder)
        ));
    }

    @SuppressWarnings("WeakerAccess")
    protected Collection<NewEmailImapEvent> generate(@Nonnull ImapFolder cubaFolder, @Nonnull Collection<IMAPMessage> newMessages) {
        if (newMessages.isEmpty()) {
            return Collections.emptyList();
        }

        return generate(cubaFolder, (IMAPFolder) newMessages.iterator().next().getFolder());
    }

    private Collection<NewEmailImapEvent> generate(@Nonnull ImapFolder cubaFolder, @Nonnull IMAPFolder imapFolder) {
        log.debug("[{}]handle events for new messages", cubaFolder);
        try {
            List<IMAPMessage> imapMessages = imapHelper.search(
                    imapFolder,
                    new NotTerm(new FlagTerm(imapHelper.cubaFlags(cubaFolder.getMailBox()), true)),
                    cubaFolder.getMailBox()
            );
            log.debug("[{}]handle events for new messages. New messages: {}", cubaFolder, imapMessages);

            Collection<NewEmailImapEvent> newEmailImapEvents = saveNewMessages(
                    imapMessages, imapFolder, cubaFolder
            );

            if (Boolean.TRUE.equals(imapConfig.getClearCustomFlags())) {
                log.trace("[{}]clear custom flags on server", cubaFolder);
                for (Message message : imapMessages) {
                    unsetCustomFlags(message);
                }
            }
            imapFolder.setFlags(
                    imapMessages.toArray(new Message[0]),
                    imapHelper.cubaFlags(cubaFolder.getMailBox()),
                    true
            );
            return newEmailImapEvents;
        } catch (MessagingException e) {
            throw new ImapException(e);
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

    private Collection<NewEmailImapEvent> saveNewMessages(Collection<IMAPMessage> imapMessages,
                                                      IMAPFolder imapFolder,
                                                      ImapFolder cubaFolder) throws MessagingException {

        Collection<NewEmailImapEvent> newEmailImapEvents = new ArrayList<>(imapMessages.size());
        boolean toCommit = false;
        authentication.begin();
        try (Transaction tx = persistence.createTransaction()) {
            EntityManager em = persistence.getEntityManager();

            for (IMAPMessage msg : imapMessages) {
                ImapMessage newMessage = insertNewMessage(em, msg, imapFolder, cubaFolder);
                toCommit |= (newMessage != null);

                if (newMessage != null) {
                    newEmailImapEvents.add(new NewEmailImapEvent(newMessage));
                }
            }
            if (toCommit) {
                tx.commit();
            }

        } finally {
            authentication.end();
        }
        return newEmailImapEvents;
    }

    private ImapMessage insertNewMessage(EntityManager em, IMAPMessage msg,
                                         IMAPFolder imapFolder,
                                         ImapFolder cubaFolder) throws MessagingException {

        Flags flags = new Flags(msg.getFlags());
        if (Boolean.TRUE.equals(imapConfig.getClearCustomFlags())) {
            log.trace("[{}]clear custom flags", imapFolder);
            for (String userFlag : flags.getUserFlags()) {
                flags.remove(userFlag);
            }
        }
        flags.add(cubaFolder.getMailBox().getCubaFlag());
        long uid = imapFolder.getUID(msg);

        int sameUids = em.createQuery(
                "select m from imapcomponent$ImapMessage m where m.msgUid = :uid and m.folder.id = :mailFolderId"
        )
                .setParameter("uid", uid)
                .setParameter("mailFolderId", cubaFolder)
                .setMaxResults(1)
                .getResultList()
                .size();
        if (sameUids == 0) {
            log.trace("Save new message {}", msg);
            ImapMessage entity = metadata.create(ImapMessage.class);
            entity.setMsgUid(uid);
            entity.setFolder(cubaFolder);
            entity.setUpdateTs(new Date());
            entity.setImapFlags(flags);
            entity.setCaption(imapHelper.getSubject(msg));
            entity.setMessageId(msg.getHeader(ImapHelper.MESSAGE_ID_HEADER, null));
            entity.setReferenceId(imapHelper.getRefId(msg));
            entity.setThreadId(imapHelper.getThreadId(msg));
            entity.setMsgNum(msg.getMessageNumber());
            em.persist(entity);
            return entity;
        }
        return null;
    }
}
