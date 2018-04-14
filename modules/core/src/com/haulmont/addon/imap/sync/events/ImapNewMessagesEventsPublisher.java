package com.haulmont.addon.imap.sync.events;

import com.haulmont.addon.imap.core.ImapHelper;
import com.haulmont.addon.imap.core.Task;
import com.haulmont.addon.imap.entity.ImapFolder;
import com.haulmont.addon.imap.entity.ImapMessage;
import com.haulmont.addon.imap.events.BaseImapEvent;
import com.haulmont.addon.imap.events.NewEmailImapEvent;
import com.haulmont.addon.imap.exception.ImapException;
import com.haulmont.addon.imap.sync.ImapFolderSyncAction;
import com.haulmont.addon.imap.sync.ImapFolderSyncEvent;
import com.haulmont.cuba.core.EntityManager;
import com.haulmont.cuba.core.Transaction;
import com.sun.mail.imap.IMAPFolder;
import com.sun.mail.imap.IMAPMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import javax.annotation.Nonnull;
import javax.mail.Flags;
import javax.mail.Message;
import javax.mail.MessagingException;
import javax.mail.search.FlagTerm;
import javax.mail.search.NotTerm;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.List;

@Component("imapcomponent_ImapNewMessagesEventsPublisher")
public class ImapNewMessagesEventsPublisher extends ImapEventsPublisher {

    private final static Logger log = LoggerFactory.getLogger(ImapNewMessagesEventsPublisher.class);

    public void handle(@Nonnull ImapFolder cubaFolder) {
        imapHelper.doWithFolder(cubaFolder.getMailBox(), cubaFolder.getName(), new Task<>(
                "fetch new messages",
                false,
                imapFolder -> {
                    handle(cubaFolder, imapFolder);
                    return null;
                }
        ));
    }

    public void handle(@Nonnull ImapFolder cubaFolder, IMAPFolder imapFolder) {
        events.publish(new ImapFolderSyncEvent(
                new ImapFolderSyncAction(cubaFolder.getId(), ImapFolderSyncAction.Type.NEW))
        );

        Collection<BaseImapEvent> imapEvents = makeEvents(cubaFolder, imapFolder);
        fireEvents(cubaFolder, imapEvents);

    }

    private Collection<BaseImapEvent> makeEvents(ImapFolder cubaFolder, IMAPFolder imapFolder) {

        log.debug("[{}]handle events for new messages", cubaFolder);
        try {
            List<IMAPMessage> imapMessages = imapHelper.search(
                    imapFolder,
                    new NotTerm(new FlagTerm(imapHelper.cubaFlags(cubaFolder.getMailBox()), true)),
                    cubaFolder.getMailBox()
            );
            log.debug("[{}]handle events for new messages. New messages: {}", cubaFolder, imapMessages);

            Collection<BaseImapEvent> newEmailImapEvents = saveNewMessages(
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

    private Collection<BaseImapEvent> saveNewMessages(List<IMAPMessage> imapMessages,
                                                    IMAPFolder imapFolder,
                                                    ImapFolder cubaFolder) throws MessagingException {

        Collection<BaseImapEvent> newEmailImapEvents = new ArrayList<>(imapMessages.size());
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
