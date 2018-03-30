package com.haulmont.addon.imap.api.scheduling;

import com.haulmont.addon.imap.core.FolderTask;
import com.haulmont.addon.imap.core.MsgHeader;
import com.haulmont.addon.imap.entity.ImapFolder;
import com.haulmont.addon.imap.entity.ImapMailBox;
import com.haulmont.addon.imap.entity.ImapMessage;
import com.haulmont.addon.imap.events.NewEmailImapEvent;
import com.haulmont.cuba.core.EntityManager;
import com.haulmont.cuba.core.Transaction;

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
        LOG.debug("[{}]handle events for new messages", cubaFolder);
        return scheduling.imapHelper.doWithFolder(
                mailBox,
                cubaFolder.getName(),
                new FolderTask<>(
                        "get new messages",
                        true,
                        true,
                        f -> {
                            List<MsgHeader> imapMessages = scheduling.imapHelper.search(
                                    f, new NotTerm(new FlagTerm(cubaFlags(mailBox), true))
                            );

                            LOG.debug("[{}]handle events for new messages. New messages: {}", cubaFolder, imapMessages);

                            //todo: optimization: should not fetch all message data by uid,
                            // it is excessive since we have already what we need in msg headers
                            Message[] messages = f.getMessagesByUID(imapMessages.stream().mapToLong(MsgHeader::getUid).toArray());
                            if (Boolean.TRUE.equals(scheduling.config.getClearCustomFlags())) {
                                LOG.trace("[{}]clear custom flags", cubaFolder);
                                for (Message message : messages) {
                                    unsetCustomFlags(message);
                                }
                                imapMessages.forEach(msg -> {
                                    Flags flags = msg.getFlags();
                                    for (String userFlag : flags.getUserFlags()) {
                                        flags.remove(userFlag);
                                    }
                                });
                            }

                            List<NewEmailImapEvent> newEmailImapEvents = saveNewMessages(imapMessages);

                            f.setFlags(messages, cubaFlags(mailBox), true);
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

    private List<NewEmailImapEvent> saveNewMessages(List<MsgHeader> imapMessages) {
        List<NewEmailImapEvent> newEmailImapEvents = new ArrayList<>(imapMessages.size());
        boolean toCommit = false;
        scheduling.authentication.begin();
        try (Transaction tx = scheduling.persistence.createTransaction()) {
            EntityManager em = scheduling.persistence.getEntityManager();

            for (MsgHeader msg : imapMessages) {
                ImapMessage newMessage = insertNewMessage(em, msg);
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

    private ImapMessage insertNewMessage(EntityManager em, MsgHeader msg) {
        msg.getFlags().add(mailBox.getCubaFlag());
        long uid = msg.getUid();

        int sameUids = em.createQuery(
                "select m from imapcomponent$ImapMessage m where m.msgUid = :uid and m.folder.id = :mailFolderId"
        )
                .setParameter("uid", uid)
                .setParameter("mailFolderId", cubaFolder)
                .setMaxResults(1)
                .getResultList()
                .size();
        if (sameUids == 0) {
            LOG.trace("Save new message {}", msg);
            ImapMessage entity = scheduling.metadata.create(ImapMessage.class);
            entity.setMsgUid(uid);
            entity.setFolder(cubaFolder);
            entity.setUpdateTs(new Date());
            entity.setImapFlags(msg.getFlags());
            entity.setCaption(msg.getCaption());
            entity.setMessageId(msg.getMsgId());
            entity.setReferenceId(msg.getRefId());
            entity.setThreadId(msg.getThreadId());
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
