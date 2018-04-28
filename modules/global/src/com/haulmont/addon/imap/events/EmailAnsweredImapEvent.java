package com.haulmont.addon.imap.events;

import com.haulmont.addon.imap.entity.ImapMessage;

/**
 * Event triggered when new reply for message arrived {@link #getMessage()} references to older message
 */
public class EmailAnsweredImapEvent extends BaseImapEvent {

    @SuppressWarnings("WeakerAccess")
    public EmailAnsweredImapEvent(ImapMessage message) {
        super(message);
    }

}