package com.haulmont.addon.imap.events;

import com.haulmont.addon.imap.entity.ImapMessage;

/**
 * Event triggered when message was deleted
 */
public class EmailDeletedImapEvent extends BaseImapEvent {

    @SuppressWarnings("WeakerAccess")
    public EmailDeletedImapEvent(ImapMessage message) {
        super(message);
    }

}
