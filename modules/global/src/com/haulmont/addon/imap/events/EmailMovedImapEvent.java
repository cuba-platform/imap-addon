package com.haulmont.addon.imap.events;

import com.haulmont.addon.imap.entity.ImapMessage;
import org.apache.commons.lang.builder.ToStringBuilder;

public class EmailMovedImapEvent extends BaseImapEvent {

    private final String newFolderName;

    public EmailMovedImapEvent(ImapMessage message, String newFolderName) {
        super(message);

        this.newFolderName = newFolderName;
    }

    public String getOldFolderName() {
        return message.getFolder().getName();
    }

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
