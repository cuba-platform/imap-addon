package com.haulmont.addon.imap.execution;

import com.haulmont.addon.imap.core.FolderKey;
import com.haulmont.addon.imap.core.ImapConsumer;
import com.haulmont.addon.imap.core.ImapFunction;
import com.sun.mail.imap.IMAPFolder;

public class ImmediateTask<T> {
    public final FolderKey folder;
    public final ImapFunction<IMAPFolder, T> action;
    public final String description;

    public ImmediateTask(FolderKey folder, ImapFunction<IMAPFolder, T> action, String actionDescription) {
        this.folder = folder;
        this.action = action;
        this.description = String.format("Immediate task <%s> for folder < %s >",
                actionDescription != null ? actionDescription : "Unspecified action", folder);
    }

    public static ImmediateTask<Void> noResultTask(FolderKey folder,
                                                   ImapConsumer<IMAPFolder> action,
                                                   String actionDescription) {
        return new ImmediateTask<>(
                folder,
                imapFolder -> { action.accept(imapFolder); return null; },
                actionDescription
        );
    }
}
