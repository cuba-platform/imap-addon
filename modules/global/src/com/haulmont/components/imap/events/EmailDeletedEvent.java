package com.haulmont.components.imap.events;

import com.haulmont.components.imap.dto.MessageRef;

public class EmailDeletedEvent extends BaseImapEvent {

    public EmailDeletedEvent(MessageRef messageRef) {
        super(messageRef);
    }

    @Override
    public String toString() {
        return "EmailDeletedEvent{" +
                "messageRef=" + messageRef +
                '}';
    }
}
