package com.haulmont.components.imap.events;

import com.haulmont.components.imap.entity.ImapMessageRef;

public class NewThreadEvent extends BaseImapEvent {

    public NewThreadEvent(ImapMessageRef messageRef) {
        super(messageRef);
    }

    @Override
    public String toString() {
        return "NewThreadEvent{" +
                "messageRef=" + messageRef +
                '}';
    }
}
