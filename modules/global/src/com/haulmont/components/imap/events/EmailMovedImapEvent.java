package com.haulmont.components.imap.events;

import com.haulmont.components.imap.entity.ImapMessage;

public class EmailMovedImapEvent extends BaseImapEvent {

    private final String oldFolderName;

    public EmailMovedImapEvent(ImapMessage message, String oldFodlerName) {
        super(message);

        this.oldFolderName = oldFodlerName;
    }

    public String getOldFolderName() {
        return oldFolderName;
    }

    public String getNewFolderName() {
        return message.getFolder().getName();
    }

    @Override
    public String toString() {
        return "EmailMovedImapEvent{" +
                "oldFolderName='" + oldFolderName + '\'' +
                ", message=" + message +
                '}';
    }
}
