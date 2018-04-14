package com.haulmont.addon.imap.events;

import com.haulmont.addon.imap.entity.ImapMessage;

public class EmailSeenImapEvent extends BaseImapEvent {

    @SuppressWarnings("WeakerAccess")
    public EmailSeenImapEvent(ImapMessage message) {
        super(message);
    }

}
