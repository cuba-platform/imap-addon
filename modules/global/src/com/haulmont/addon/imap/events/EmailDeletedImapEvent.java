package com.haulmont.addon.imap.events;

import com.haulmont.addon.imap.entity.ImapMessage;

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
