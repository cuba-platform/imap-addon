package com.haulmont.addon.imap.events;

import com.haulmont.addon.imap.entity.ImapMessage;

/**
 * Event triggered when new IMAP message arrived
 */
public class NewEmailImapEvent extends BaseImapEvent {

    @SuppressWarnings("WeakerAccess")
    public NewEmailImapEvent(ImapMessage message) {
        super(message);
    }

    @SuppressWarnings("unused")
    public Long getMessageId() {
        return message.getMsgUid();
    }
}
