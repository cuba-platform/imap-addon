package com.haulmont.components.imap.events;

import com.haulmont.components.imap.entity.ImapMessageRef;

public class EmailMovedEvent extends BaseImapEvent {

    private final String oldFolderName;

    public EmailMovedEvent(ImapMessageRef messageRef, String oldFodlerName) {
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
        return "EmailMovedEvent{" +
                "oldFolderName='" + oldFolderName + '\'' +
                ", messageRef=" + messageRef +
                '}';
    }
}
