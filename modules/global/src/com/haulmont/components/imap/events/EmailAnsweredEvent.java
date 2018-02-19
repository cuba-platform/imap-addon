package com.haulmont.components.imap.events;

import com.haulmont.components.imap.dto.MessageRef;

public class EmailAnsweredEvent extends BaseImapEvent {

    public EmailAnsweredEvent(MessageRef messageRef) {
        super(messageRef);
    }

    @Override
    public String toString() {
        return "EmailAnsweredEvent{" +
                "messageRef=" + messageRef +
                '}';
    }
}