package com.haulmont.components.imap.api.scheduling;

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
        Flags flags = newMsgHeader.getFlags();
        Flags oldFlags = msg.getImapFlags();

        List<BaseImapEvent> modificationEvents = new ArrayList<>(3);
        boolean oldSeen = oldFlags.contains(Flags.Flag.SEEN);
        boolean newSeen = flags.contains(Flags.Flag.SEEN);
        boolean oldDeleted = oldFlags.contains(Flags.Flag.DELETED);
        boolean newDeleted = flags.contains(Flags.Flag.DELETED);
        boolean oldFlagged = oldFlags.contains(Flags.Flag.FLAGGED);
        boolean newFlagged = flags.contains(Flags.Flag.FLAGGED);
        boolean oldAnswered = oldFlags.contains(Flags.Flag.ANSWERED);
        boolean newAnswered = flags.contains(Flags.Flag.ANSWERED);
        String oldRefId = msg.getReferenceId();
        String newRefId = newMsgHeader.getRefId();

        //todo: handle custom flags

        if (oldSeen != newSeen || oldDeleted != newDeleted || oldAnswered != newAnswered || oldFlagged != newFlagged || !Objects.equals(oldRefId, newRefId)) {
            HashMap<String, Boolean> changedFlagsWithNewValue = new HashMap<>();
            if (oldSeen != newSeen) {
                changedFlagsWithNewValue.put("SEEN", newSeen);
                if (newSeen && cubaFolder.hasEvent(ImapEventType.EMAIL_SEEN)) {
                    modificationEvents.add(new EmailSeenImapEvent(msg));
                }
            }

            if (oldAnswered != newAnswered || !Objects.equals(oldRefId, newRefId)) {
                changedFlagsWithNewValue.put("ANSWERED", newAnswered);
                if (newAnswered || newRefId != null) {
                    modificationEvents.add(new EmailAnsweredImapEvent(msg));
                }
            }

            if (oldDeleted != newDeleted) {
                changedFlagsWithNewValue.put("DELETED", newDeleted);
            }
            if (oldFlagged != newFlagged) {
                changedFlagsWithNewValue.put("FLAGGED", newFlagged);
            }
            if (cubaFolder.hasEvent(ImapEventType.FLAGS_UPDATED)) {
                modificationEvents.add(new EmailFlagChangedImapEvent(msg, changedFlagsWithNewValue));
            }

        }
        msg.setReferenceId(newRefId);
        msg.setImapFlags(flags);
        msg.setThreadId(newMsgHeader.getThreadId());  // todo: fire thread event
        msg.setUpdateTs(new Date());
        em.persist(msg);

        return modificationEvents;
    }

    @Override
    protected String taskDescription() {
        return "updating messages flags";
    }


}
