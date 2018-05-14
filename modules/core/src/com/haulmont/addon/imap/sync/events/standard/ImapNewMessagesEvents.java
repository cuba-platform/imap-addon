package com.haulmont.addon.imap.sync.events.standard;

import com.google.common.collect.ImmutableMap;
import com.haulmont.addon.imap.api.ImapFlag;
import com.haulmont.addon.imap.config.ImapConfig;
import com.haulmont.addon.imap.core.ImapHelper;
import com.haulmont.addon.imap.core.ImapOperations;
import com.haulmont.addon.imap.core.Task;
import com.haulmont.addon.imap.dao.ImapDao;
import com.haulmont.addon.imap.entity.ImapFolder;
import com.haulmont.addon.imap.entity.ImapMailBox;
import com.haulmont.addon.imap.entity.ImapMessage;
import com.haulmont.addon.imap.events.BaseImapEvent;
import com.haulmont.addon.imap.events.EmailAnsweredImapEvent;
import com.haulmont.addon.imap.events.EmailFlagChangedImapEvent;
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
import java.util.*;

@Component("imap_NewMessagesEvents")
public class ImapNewMessagesEvents {
    private final static Logger log = LoggerFactory.getLogger(ImapNewMessagesEvents.class);

    private final ImapHelper imapHelper;
    private final ImapOperations imapOperations;
    private final ImapConfig imapConfig;
    private final Authentication authentication;
    private final Persistence persistence;
    private final ImapDao dao;
    private final Metadata metadata;

    @SuppressWarnings("CdiInjectionPointsInspection")
    @Inject
    public ImapNewMessagesEvents(ImapHelper imapHelper,
                                 ImapOperations imapOperations,
                                 @SuppressWarnings("SpringJavaInjectionPointsAutowiringInspection") ImapConfig imapConfig,
                                 Authentication authentication,
                                 Persistence persistence,
                                 ImapDao dao,
                                 Metadata metadata) {
        this.imapHelper = imapHelper;
        this.imapOperations = imapOperations;
        this.imapConfig = imapConfig;
        this.authentication = authentication;
        this.persistence = persistence;
        this.dao = dao;
        this.metadata = metadata;
    }

    @SuppressWarnings("WeakerAccess")
    protected Collection<BaseImapEvent> generate(@Nonnull ImapFolder cubaFolder) {
        return imapHelper.doWithFolder(cubaFolder.getMailBox(), cubaFolder.getName(), new Task<>(
                "fetch new messages",
                true,
                imapFolder -> generate(cubaFolder, imapFolder)
        ));
    }

    @SuppressWarnings("WeakerAccess")
    protected Collection<BaseImapEvent> generate(@Nonnull ImapFolder cubaFolder, @Nonnull Collection<IMAPMessage> newMessages) {
        if (newMessages.isEmpty()) {
            return Collections.emptyList();
        }

        return generate(cubaFolder, (IMAPFolder) newMessages.iterator().next().getFolder());
    }

    private Collection<BaseImapEvent> generate(@Nonnull ImapFolder cubaFolder, @Nonnull IMAPFolder imapFolder) {
        log.debug("[{}]handle events for new messages", cubaFolder);
        try {
            ImapMailBox mailBox = cubaFolder.getMailBox();
            List<IMAPMessage> imapMessages = imapOperations.search(
                    imapFolder,
                    new FlagTerm(imapHelper.cubaFlags(mailBox), false),
                    mailBox
            );
            log.debug("[{}]handle events for new messages. New messages: {}", cubaFolder, imapMessages);

            Collection<BaseImapEvent> imapEvents = saveNewMessages(
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
                    imapHelper.cubaFlags(mailBox),
                    true
            );

            imapOperations.setAnsweredFlag(mailBox, imapEvents);
            return imapEvents;
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

    private Collection<BaseImapEvent> saveNewMessages(Collection<IMAPMessage> imapMessages,
                                                      IMAPFolder imapFolder,
                                                      ImapFolder cubaFolder) throws MessagingException {

        Collection<NewEmailImapEvent> newMessageEvents = new ArrayList<>(imapMessages.size());

        authentication.begin();
        try (Transaction tx = persistence.createTransaction()) {
            EntityManager em = persistence.getEntityManager();
            boolean toCommit = false;

            for (IMAPMessage msg : imapMessages) {
                ImapMessage newMessage = insertNewMessage(em, msg, imapFolder, cubaFolder);
                toCommit |= (newMessage != null);

                if (newMessage != null) {
                    newMessageEvents.add(new NewEmailImapEvent(newMessage));
                }
            }
            if (toCommit) {
                tx.commit();
            }

        } finally {
            authentication.end();
        }

        Collection<BaseImapEvent> imapEvents = new ArrayList<>(newMessageEvents);
        imapEvents.addAll(generateAnsweredEvents(cubaFolder, newMessageEvents));

        return imapEvents;
    }

    private Collection<BaseImapEvent> generateAnsweredEvents(ImapFolder cubaFolder,
                                                             Collection<NewEmailImapEvent> newMessageEvents) {

        authentication.begin();
        try (Transaction tx = persistence.createTransaction()) {
            EntityManager em = persistence.getEntityManager();
            boolean toCommit = false;

            Collection<BaseImapEvent> imapEvents = new ArrayList<>();
            for (NewEmailImapEvent newMessageEvent : newMessageEvents) {
                ImapMessage message = newMessageEvent.getMessage();
                if (message.getReferenceId() != null) {
                    ImapMessage parentMessage = dao.findMessageByImapMessageId(
                            cubaFolder.getMailBox().getId(), message.getReferenceId()
                    );
                    if (parentMessage != null) {
                        boolean added = dao.addFlag(parentMessage, ImapFlag.ANSWERED, em);
                        toCommit |= added;

                        if (added) {
                            imapEvents.add(new EmailAnsweredImapEvent(parentMessage));
                            imapEvents.add(new EmailFlagChangedImapEvent(parentMessage, ImmutableMap.of(ImapFlag.ANSWERED, true)));
                        }
                    }
                }
            }

            if (toCommit) {
                tx.commit();
            }

            return imapEvents;
        } finally {
            authentication.end();
        }
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

        int sameUIDs = em.createQuery(
                "select m from imap$Message m where m.msgUid = :uid and m.folder.id = :mailFolderId"
        )
                .setParameter("uid", uid)
                .setParameter("mailFolderId", cubaFolder)
                .setMaxResults(1)
                .getResultList()
                .size();
        if (sameUIDs == 0) {
            log.trace("Save new message {}", msg);
            ImapMessage entity = metadata.create(ImapMessage.class);
            entity.setMsgUid(uid);
            entity.setFolder(cubaFolder);
            entity.setUpdateTs(new Date());
            entity.setImapFlags(flags);
            entity.setCaption(imapOperations.getSubject(msg));
            entity.setMessageId(msg.getHeader(ImapOperations.MESSAGE_ID_HEADER, null));
            entity.setReferenceId(imapOperations.getRefId(msg));
            entity.setThreadId(imapOperations.getThreadId(msg, cubaFolder.getMailBox()));
            entity.setMsgNum(msg.getMessageNumber());
            em.persist(entity);
            return entity;
        }
        return null;
    }
}
