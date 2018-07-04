package com.haulmont.addon.imap.execution;

import com.haulmont.addon.imap.core.FolderKey;
import com.haulmont.addon.imap.core.ImapConsumer;
import com.haulmont.addon.imap.core.ImapFunction;
import com.sun.mail.imap.IMAPFolder;

import java.util.function.Consumer;

public class DelayableTask<T> {
    public final FolderKey folder;
    public final ImapFunction<IMAPFolder, T> action;
    public final Consumer<T> callback;
    public final String description;

    public DelayableTask(FolderKey folder,
                         ImapFunction<IMAPFolder, T> action,
                         Consumer<T> callback,
                         String actionDescription) {

        this.folder = folder;
        this.action = action;
        this.callback = callback;
        this.description = String.format("Delayable task <%s> for folder < %s >",
                actionDescription != null ? actionDescription : "Unspecified action", folder);
    }

    public static DelayableTask<Void> noResultTask(FolderKey folder,
                                                   ImapConsumer<IMAPFolder> action,
                                                   String actionDescription) {
        return new DelayableTask<>(
                folder,
                imapFolder -> { action.accept(imapFolder); return null; },
                ignore -> {},
                actionDescription
        );
    }
}
