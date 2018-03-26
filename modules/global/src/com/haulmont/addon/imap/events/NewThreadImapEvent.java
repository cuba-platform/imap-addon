package com.haulmont.addon.imap.events;

import com.haulmont.addon.imap.entity.ImapMessage;

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
