package com.haulmont.addon.imap.events;

import com.haulmont.addon.imap.entity.ImapMessage;

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
