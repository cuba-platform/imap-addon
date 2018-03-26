package com.haulmont.addon.imap.events;

import com.haulmont.addon.imap.entity.ImapMessage;

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