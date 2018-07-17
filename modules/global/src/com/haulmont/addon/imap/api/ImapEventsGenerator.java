package com.haulmont.addon.imap.api;

import com.haulmont.addon.imap.entity.ImapFolder;
import com.haulmont.addon.imap.entity.ImapMailBox;
import com.haulmont.addon.imap.events.BaseImapEvent;
import com.haulmont.addon.imap.events.NewEmailImapEvent;

import java.util.Collection;

/**
 * Extension point for IMAP events. Bean implementing this interface can be specified in IMAP mailbox configuration.
 * Such beans can be useful for using IMAP extensions and custom communication mechanisms specific
 * for particular mailboxes
 */
public interface ImapEventsGenerator {

    /**
     * Perform bootstrap logic for mailbox synchronization, e.g. attach listeners or schedule background tasks
     *
     * @param mailBox IMAP mailbox
     */
    void init(ImapMailBox mailBox);
    /**
     * Release resources used for synchronization, e.g. detach listeners or cancel scheduled background tasks
     *
     * @param mailBox IMAP mailbox
     */
    void shutdown(ImapMailBox mailBox);
    /**
     * Emit events for new messages in mailbox folder accumulated since the previous call of this method for the folder
     *
     * @param folder IMAP mailbox folder
     * @return       events related to new messages in the folder,
     * can emit not only instances of {@link NewEmailImapEvent}
     *
     */
    Collection<? extends BaseImapEvent> generateForNewMessages(ImapFolder folder);
    /**
     * Emit events for modified messages in mailbox folder accumulated since the previous call of this method
     * for the folder
     *
     * @param folder IMAP mailbox folder
     * @return       events related to modified messages in the folder
     *
     */
    Collection<? extends BaseImapEvent> generateForChangedMessages(ImapFolder folder);
    /**
     * Emit events for missed (moved to other folder or deleted) messages in mailbox folder accumulated since the
     * previous call of this method for the folder
     *
     * @param folder IMAP mailbox folder
     * @return       events related to missed messages in the folder
     *
     */
    Collection<? extends BaseImapEvent> generateForMissedMessages(ImapFolder folder);
}
