package com.haulmont.components.imap.events;

import com.haulmont.components.imap.dto.MessageRef;

public class EmailSeenEvent extends BaseImapEvent {

    public EmailSeenEvent(MessageRef messageRef) {
        super(messageRef);
    }

    @Override
    public String toString() {
        return "EmailSeenEvent{" +
                "messageRef=" + messageRef +
                '}';
    }
}
