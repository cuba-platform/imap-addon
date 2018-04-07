package com.haulmont.addon.imap.api.scheduling;

import com.haulmont.addon.imap.core.FolderTask;
import com.haulmont.addon.imap.core.ImapHelper;
import com.haulmont.addon.imap.entity.ImapFolder;
import com.haulmont.addon.imap.entity.ImapMailBox;
import com.haulmont.addon.imap.entity.ImapMessage;
import com.haulmont.addon.imap.events.NewEmailImapEvent;
import com.haulmont.cuba.core.EntityManager;
import com.haulmont.cuba.core.Transaction;
import com.sun.mail.imap.IMAPFolder;
import com.sun.mail.imap.IMAPMessage;

import javax.mail.Flags;
import javax.mail.Message;
import javax.mail.MessagingException;
import javax.mail.search.FlagTerm;
import javax.mail.search.NotTerm;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

class NewMessagesInFolderTask extends AbstractFolderTask {

    NewMessagesInFolderTask(ImapMailBox mailBox, ImapFolder cubaFolder, ImapScheduling scheduling) {
        super(mailBox, cubaFolder, scheduling);
    }

    @Override
    List<NewEmailImapEvent> makeEvents() {
        log.debug("[{}]handle events for new messages", cubaFolder);
        return scheduling.imapHelper.doWithFolder(
                mailBox,
                cubaFolder.getName(),
                new FolderTask<>(
                        "get new messages",
                        true,
                        true,
                        f -> {
                            List<IMAPMessage> imapMessages = scheduling.imapHelper.search(
                                    f, new NotTerm(new FlagTerm(cubaFlags(mailBox), true)), mailBox
                            );

                            log.debug("[{}]handle events for new messages. New messages: {}", cubaFolder, imapMessages);


                            List<NewEmailImapEvent> newEmailImapEvents = saveNewMessages(imapMessages, f);

                            if (Boolean.TRUE.equals(scheduling.config.getClearCustomFlags())) {
                                log.trace("[{}]clear custom flags on server", cubaFolder);
                                for (Message message : imapMessages) {
                                    unsetCustomFlags(message);
                                }
                            }
                            f.setFlags(imapMessages.toArray(new Message[0]), cubaFlags(mailBox), true);
                            return newEmailImapEvents;
                        }
                )
        );
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

    private List<NewEmailImapEvent> saveNewMessages(List<IMAPMessage> imapMessages, IMAPFolder folder) throws MessagingException {
        List<NewEmailImapEvent> newEmailImapEvents = new ArrayList<>(imapMessages.size());
        boolean toCommit = false;
        scheduling.authentication.begin();
        try (Transaction tx = scheduling.persistence.createTransaction()) {
            EntityManager em = scheduling.persistence.getEntityManager();

            for (IMAPMessage msg : imapMessages) {
                ImapMessage newMessage = insertNewMessage(em, msg, folder);
                toCommit |= (newMessage != null);

                if (newMessage != null) {
                    newEmailImapEvents.add(new NewEmailImapEvent(newMessage));
                }
            }
            if (toCommit) {
                tx.commit();
            }

        } finally {
            scheduling.authentication.end();
        }
        return newEmailImapEvents;
    }

    private ImapMessage insertNewMessage(EntityManager em, IMAPMessage msg, IMAPFolder folder) throws MessagingException {
        Flags flags = new Flags(msg.getFlags());
        if (Boolean.TRUE.equals(scheduling.config.getClearCustomFlags())) {
            log.trace("[{}]clear custom flags", cubaFolder);
            for (String userFlag : flags.getUserFlags()) {
                flags.remove(userFlag);
            }
        }
        flags.add(mailBox.getCubaFlag());
        long uid = folder.getUID(msg);

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
            ImapMessage entity = scheduling.metadata.create(ImapMessage.class);
            entity.setMsgUid(uid);
            entity.setFolder(cubaFolder);
            entity.setUpdateTs(new Date());
            entity.setImapFlags(flags);
            entity.setCaption(scheduling.imapHelper.getSubject(msg));
            entity.setMessageId(msg.getHeader(ImapHelper.MESSAGE_ID_HEADER, null));
            entity.setReferenceId(scheduling.imapHelper.getRefId(msg));
            entity.setThreadId(scheduling.imapHelper.getThreadId(msg));
            em.persist(entity);
            return entity;
        }
        return null;
    }

    private Flags cubaFlags(ImapMailBox mailBox) {
        Flags cubaFlags = new Flags();
        cubaFlags.add(mailBox.getCubaFlag());
        return cubaFlags;
    }
}
