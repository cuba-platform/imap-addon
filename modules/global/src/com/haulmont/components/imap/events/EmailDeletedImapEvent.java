package com.haulmont.components.imap.events;

import com.haulmont.components.imap.entity.ImapMessageRef;

public class EmailDeletedImapEvent extends BaseImapEvent {

    public EmailDeletedImapEvent(ImapMessageRef messageRef) {
        super(messageRef);
    }

    @Override
    public String toString() {
        return "EmailDeletedImapEvent{" +
                "messageRef=" + messageRef +
                '}';
    }
}
