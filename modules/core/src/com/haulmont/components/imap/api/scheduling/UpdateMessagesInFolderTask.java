package com.haulmont.components.imap.api.scheduling;

import com.haulmont.components.imap.api.ImapFlag;
import com.haulmont.components.imap.core.MsgHeader;
import com.haulmont.components.imap.entity.ImapEventType;
import com.haulmont.components.imap.entity.ImapFolder;
import com.haulmont.components.imap.entity.ImapMailBox;
import com.haulmont.components.imap.entity.ImapMessage;
import com.haulmont.components.imap.events.*;
import com.haulmont.cuba.core.EntityManager;
import com.sun.mail.imap.IMAPFolder;

import javax.mail.Flags;
import java.util.*;

class UpdateMessagesInFolderTask extends ExistingMessagesInFolderTask {

    UpdateMessagesInFolderTask(ImapMailBox mailBox, ImapFolder cubaFolder, IMAPFolder folder, ImapScheduling scheduling) {
        super(mailBox, cubaFolder, folder, scheduling);
    }

    @Override
    protected List<BaseImapEvent> handleMessage(EntityManager em, ImapMessage msg, Map<Long, MsgHeader> msgsByUid) {
        MsgHeader newMsgHeader = msgsByUid.get(msg.getMsgUid());
        if (newMsgHeader == null) {
            return Collections.emptyList();
        }
        Flags newFlags = newMsgHeader.getFlags();
        Flags oldFlags = msg.getImapFlags();

        List<BaseImapEvent> modificationEvents = new ArrayList<>(3);
        if (!Objects.equals(newFlags, oldFlags)) {

            HashMap<ImapFlag, Boolean> changedFlagsWithNewValue = new HashMap<>();
            if (isSeen(newFlags, oldFlags)) {
                modificationEvents.add(new EmailSeenImapEvent(msg));
            }

            if (isAnswered(newFlags, oldFlags)) { //todo: handle answered event based on refs
                modificationEvents.add(new EmailAnsweredImapEvent(msg));
            }

            for (String userFlag : oldFlags.getUserFlags()) {
                if (!newFlags.contains(userFlag)) {
                    changedFlagsWithNewValue.put(new ImapFlag(userFlag), false);
                }
            }

            for (Flags.Flag systemFlag : oldFlags.getSystemFlags()) {
                if (!newFlags.contains(systemFlag)) {
                    changedFlagsWithNewValue.put(new ImapFlag(ImapFlag.SystemFlag.valueOf(systemFlag)), false);
                }
            }

            for (String userFlag : newFlags.getUserFlags()) {
                if (!oldFlags.contains(userFlag)) {
                    changedFlagsWithNewValue.put(new ImapFlag(userFlag), true);
                }
            }

            for (Flags.Flag systemFlag : newFlags.getSystemFlags()) {
                if (!oldFlags.contains(systemFlag)) {
                    changedFlagsWithNewValue.put(new ImapFlag(ImapFlag.SystemFlag.valueOf(systemFlag)), true);
                }
            }

            if (cubaFolder.hasEvent(ImapEventType.FLAGS_UPDATED)) {
                modificationEvents.add(new EmailFlagChangedImapEvent(msg, changedFlagsWithNewValue));
            }

        }
        msg.setReferenceId(newMsgHeader.getRefId());
        msg.setImapFlags(newFlags);
        msg.setThreadId(newMsgHeader.getThreadId());  // todo: fire thread event
        msg.setUpdateTs(new Date());
        em.persist(msg);

        return modificationEvents;
    }

    private boolean isSeen(Flags newflags, Flags oldFlags) {
        return !oldFlags.contains(Flags.Flag.SEEN)
                && newflags.contains(Flags.Flag.SEEN)
                && cubaFolder.hasEvent(ImapEventType.EMAIL_SEEN);
    }

    private boolean isAnswered(Flags newflags, Flags oldFlags) {
        return !oldFlags.contains(Flags.Flag.ANSWERED)
                && newflags.contains(Flags.Flag.ANSWERED)
                && cubaFolder.hasEvent(ImapEventType.NEW_ANSWER);
    }

    @Override
    protected String taskDescription() {
        return "updating messages flags";
    }


}
