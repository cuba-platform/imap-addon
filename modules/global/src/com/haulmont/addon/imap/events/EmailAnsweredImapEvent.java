package com.haulmont.addon.imap.events;

import com.haulmont.addon.imap.entity.ImapMessage;

public class EmailAnsweredImapEvent extends BaseImapEvent {

    @SuppressWarnings("WeakerAccess")
    public EmailAnsweredImapEvent(ImapMessage message) {
        super(message);
    }

}