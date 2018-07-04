package com.haulmont.addon.imap.execution;

import com.haulmont.addon.imap.core.ImapConsumer;
import com.haulmont.addon.imap.core.MailboxKey;
import com.haulmont.addon.imap.core.ImapFunction;
import com.haulmont.addon.imap.entity.ImapMailBox;
import com.sun.mail.imap.IMAPStore;

public class GlobalMailboxTask<T> {
    public final ImapMailBox mailbox;
    public final ImapFunction<IMAPStore, T> action;
    public final String description;

    public GlobalMailboxTask(ImapMailBox mailbox, ImapFunction<IMAPStore, T> action, String actionDescription) {
        this.mailbox = mailbox;
        this.action = action;
        this.description = String.format("Global task <%s> for mailbox < %s >",
                actionDescription != null ? actionDescription : "Unspecified action", new MailboxKey(mailbox));
    }

    public static GlobalMailboxTask<Void> noResultTask(ImapMailBox mailbox,
                                                       ImapConsumer<IMAPStore> action,
                                                       String actionDescription) {
        return new GlobalMailboxTask<>(
                mailbox,
                imapStore -> { action.accept(imapStore); return null; },
                actionDescription
        );
    }
}
