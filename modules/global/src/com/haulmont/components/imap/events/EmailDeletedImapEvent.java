package com.haulmont.components.imap.events;

import com.haulmont.components.imap.entity.ImapMessage;

public class EmailDeletedImapEvent extends BaseImapEvent {

    public EmailDeletedImapEvent(ImapMessage message) {
        super(message);
    }

    @Override
    public String toString() {
        return "EmailDeletedImapEvent{" +
                "message=" + message +
                '}';
    }
}
