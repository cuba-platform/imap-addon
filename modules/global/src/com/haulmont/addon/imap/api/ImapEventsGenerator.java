package com.haulmont.addon.imap.api;

import com.haulmont.addon.imap.entity.ImapFolder;
import com.haulmont.addon.imap.entity.ImapMailBox;
import com.haulmont.addon.imap.events.BaseImapEvent;
import com.haulmont.addon.imap.events.NewEmailImapEvent;

import java.util.Collection;

/**
 * An extension point for IMAP events. A bean implementing this interface can be specified in an IMAP configuration.
 * Such beans can be useful for applying IMAP extensions and custom communication mechanisms specific
 * for particular mailboxes
 */
public interface ImapEventsGenerator {

    /**
     * Performs bootstrap logic for mailbox synchronization, e.g. attaching listeners or schedule background tasks
     *
     * @param mailBox IMAP mailbox
     */
    void init(ImapMailBox mailBox);
    /**
     * Releases resources used for synchronization, e.g. detaching listeners or cancelling scheduled background tasks
     *
     * @param mailBox IMAP mailbox
     */
    void shutdown(ImapMailBox mailBox);
    /**
     * Emits events for new messages in a mailbox folder accumulated since the previous call of this method for the folder
     *
     * @param folder IMAP mailbox folder
     * @return       events related to new messages in the folder,
     * can emit not only instances of {@link NewEmailImapEvent}
     *
     */
    Collection<? extends BaseImapEvent> generateForNewMessages(ImapFolder folder);
    /**
     * Emits events for modified messages in a mailbox folder accumulated since the previous call of this method
     * for the folder
     *
     * @param folder IMAP mailbox folder
     * @return       events related to modified messages in the folder
     *
     */
    Collection<? extends BaseImapEvent> generateForChangedMessages(ImapFolder folder);
    /**
     * Emits events for missed (moved to other folder or deleted) messages in a mailbox folder accumulated since the
     * previous call of this method for the folder
     *
     * @param folder IMAP mailbox folder
     * @return       events related to missed messages in the folder
     *
     */
    Collection<? extends BaseImapEvent> generateForMissedMessages(ImapFolder folder);
}
