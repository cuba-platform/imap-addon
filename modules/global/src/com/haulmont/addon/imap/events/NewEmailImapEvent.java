package com.haulmont.addon.imap.events;

import com.haulmont.addon.imap.entity.ImapMessage;

public class NewEmailImapEvent extends BaseImapEvent {

    public NewEmailImapEvent(ImapMessage message) {
        super(message);
    }

    public Long getMessageId() {
        return message.getMsgUid();
    }
}
