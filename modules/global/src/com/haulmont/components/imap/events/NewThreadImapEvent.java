package com.haulmont.components.imap.events;

import com.haulmont.components.imap.entity.ImapMessageRef;

public class NewThreadImapEvent extends BaseImapEvent {

    public NewThreadImapEvent(ImapMessageRef messageRef) {
        super(messageRef);
    }

    @Override
    public String toString() {
        return "NewThreadImapEvent{" +
                "messageRef=" + messageRef +
                '}';
    }
}
