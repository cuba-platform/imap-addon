package com.haulmont.components.imap.events;

import com.haulmont.components.imap.entity.ImapMessageRef;

public class EmailAnsweredImapEvent extends BaseImapEvent {

    public EmailAnsweredImapEvent(ImapMessageRef messageRef) {
        super(messageRef);
    }

    @Override
    public String toString() {
        return "EmailAnsweredImapEvent{" +
                "messageRef=" + messageRef +
                '}';
    }
}