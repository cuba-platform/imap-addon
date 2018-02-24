package com.haulmont.components.imap.events;

import com.haulmont.components.imap.entity.ImapMessageRef;

public class EmailDeletedEvent extends BaseImapEvent {

    public EmailDeletedEvent(ImapMessageRef messageRef) {
        super(messageRef);
    }

    @Override
    public String toString() {
        return "EmailDeletedEvent{" +
                "messageRef=" + messageRef +
                '}';
    }
}
