package com.haulmont.addon.imap.sync.events;

import com.haulmont.addon.imap.entity.ImapFolder;
import com.haulmont.addon.imap.events.BaseImapEvent;
import com.sun.mail.imap.IMAPMessage;

import java.util.Collection;

public interface ImapEventsGenerator {
    void init(ImapFolder cubaFolder);
    void shutdown(ImapFolder cubaFolder);
    Collection<? extends BaseImapEvent> generateForNewMessages(ImapFolder cubaFolder);
    Collection<? extends BaseImapEvent> generateForChangedMessages(ImapFolder cubaFolder);
    Collection<? extends BaseImapEvent> generateForMissedMessages(ImapFolder cubaFolder);
}
