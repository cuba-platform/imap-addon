package com.haulmont.components.imap.events;

import com.haulmont.components.imap.entity.ImapMessageRef;

public class EmailSeenEvent extends BaseImapEvent {

    public EmailSeenEvent(ImapMessageRef messageRef) {
        super(messageRef);
    }

    @Override
    public String toString() {
        return "EmailSeenEvent{" +
                "messageRef=" + messageRef +
                '}';
    }
}
