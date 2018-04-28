package com.haulmont.addon.imap.events;

import com.haulmont.addon.imap.entity.ImapMessage;

/**
 * Event triggered when IMAP message was marked by {@link javax.mail.Flags.Flag#SEEN} flag
 */
public class EmailSeenImapEvent extends BaseImapEvent {

    @SuppressWarnings("WeakerAccess")
    public EmailSeenImapEvent(ImapMessage message) {
        super(message);
    }

}
