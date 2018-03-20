package com.haulmont.components.imap.events;

import com.haulmont.components.imap.entity.ImapMessage;

public class NewThreadImapEvent extends BaseImapEvent {

    public NewThreadImapEvent(ImapMessage message) {
        super(message);
    }

    @Override
    public String toString() {
        return "NewThreadImapEvent{" +
                "message=" + message +
                '}';
    }
}
