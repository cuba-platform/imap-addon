package com.haulmont.components.imap.events;

import com.haulmont.components.imap.entity.ImapMessageRef;

public class EmailMovedImapEvent extends BaseImapEvent {

    private final String oldFolderName;

    public EmailMovedImapEvent(ImapMessageRef messageRef, String oldFodlerName) {
        super(messageRef);

        this.oldFolderName = oldFodlerName;
    }

    public String getOldFolderName() {
        return oldFolderName;
    }

    public String getNewFolderName() {
        return messageRef.getFolder().getName();
    }

    @Override
    public String toString() {
        return "EmailMovedImapEvent{" +
                "oldFolderName='" + oldFolderName + '\'' +
                ", messageRef=" + messageRef +
                '}';
    }
}
