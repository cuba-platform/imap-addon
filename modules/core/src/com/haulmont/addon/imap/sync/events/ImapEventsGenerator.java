package com.haulmont.addon.imap.sync.events;

import com.haulmont.addon.imap.entity.ImapFolder;
import com.haulmont.addon.imap.entity.ImapMailBox;
import com.haulmont.addon.imap.events.BaseImapEvent;

import java.util.Collection;

public interface ImapEventsGenerator {
    void init(ImapMailBox mailBox);
    void shutdown(ImapMailBox mailBox);
    Collection<? extends BaseImapEvent> generateForNewMessages(ImapFolder cubaFolder);
    Collection<? extends BaseImapEvent> generateForChangedMessages(ImapFolder cubaFolder);
    Collection<? extends BaseImapEvent> generateForMissedMessages(ImapFolder cubaFolder);
}
