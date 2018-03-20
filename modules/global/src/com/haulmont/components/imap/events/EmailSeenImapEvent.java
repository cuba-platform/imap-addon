package com.haulmont.components.imap.events;

import com.haulmont.components.imap.entity.ImapMessage;

public class EmailSeenImapEvent extends BaseImapEvent {

    public EmailSeenImapEvent(ImapMessage message) {
        super(message);
    }

    @Override
    public String toString() {
        return "EmailSeenImapEvent{" +
                "message=" + message +
                '}';
    }
}
