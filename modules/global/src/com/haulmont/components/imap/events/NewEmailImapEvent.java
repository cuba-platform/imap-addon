package com.haulmont.components.imap.events;

import com.haulmont.components.imap.entity.ImapMessage;

public class NewEmailImapEvent extends BaseImapEvent {

    public NewEmailImapEvent(ImapMessage message) {
        super(message);
    }

    public Long getMessageId() {
        return message.getMsgUid();
    }
}
