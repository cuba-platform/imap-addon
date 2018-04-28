package com.haulmont.addon.imap.events;

import com.haulmont.addon.imap.entity.ImapMessage;

/**
 * Event triggered when new IMAP message thread was created
 */
public class NewThreadImapEvent extends BaseImapEvent {

    public NewThreadImapEvent(ImapMessage message) {
        super(message);
    }

}
