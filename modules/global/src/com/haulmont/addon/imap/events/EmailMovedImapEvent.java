package com.haulmont.addon.imap.events;

import com.haulmont.addon.imap.entity.ImapMessage;
import org.apache.commons.lang.builder.ToStringBuilder;

/**
 * Event triggered when IMAP message was moved to different folder
 */
public class EmailMovedImapEvent extends BaseImapEvent {

    private final String newFolderName;

    @SuppressWarnings("WeakerAccess")
    public EmailMovedImapEvent(ImapMessage message, String newFolderName) {
        super(message);

        this.newFolderName = newFolderName;
    }

    @SuppressWarnings("unused")
    public String getOldFolderName() {
        return message.getFolder().getName();
    }

    @SuppressWarnings("unused")
    public String getNewFolderName() {
        return newFolderName;
    }

    @Override
    public String toString() {
        return new ToStringBuilder(this).
                append("newFolderName", newFolderName).
                append("message", message).
                toString();
    }
}
