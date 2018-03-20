package com.haulmont.components.imap.events;

import com.haulmont.components.imap.entity.ImapMessage;

public class EmailAnsweredImapEvent extends BaseImapEvent {

    public EmailAnsweredImapEvent(ImapMessage message) {
        super(message);
    }

    @Override
    public String toString() {
        return "EmailAnsweredImapEvent{" +
                "message=" + message +
                '}';
    }
}