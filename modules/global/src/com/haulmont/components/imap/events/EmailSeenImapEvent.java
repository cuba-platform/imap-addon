package com.haulmont.components.imap.events;

import com.haulmont.components.imap.entity.ImapMessageRef;

public class EmailSeenImapEvent extends BaseImapEvent {

    public EmailSeenImapEvent(ImapMessageRef messageRef) {
        super(messageRef);
    }

    @Override
    public String toString() {
        return "EmailSeenImapEvent{" +
                "messageRef=" + messageRef +
                '}';
    }
}
