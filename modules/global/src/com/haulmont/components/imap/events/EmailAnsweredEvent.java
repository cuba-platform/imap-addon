package com.haulmont.components.imap.events;

import com.haulmont.components.imap.entity.ImapMessageRef;

public class EmailAnsweredEvent extends BaseImapEvent {

    public EmailAnsweredEvent(ImapMessageRef messageRef) {
        super(messageRef);
    }

    @Override
    public String toString() {
        return "EmailAnsweredEvent{" +
                "messageRef=" + messageRef +
                '}';
    }
}