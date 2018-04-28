package com.haulmont.addon.imap.sync.listener;

import com.haulmont.addon.imap.entity.ImapFolder;
import org.springframework.context.ApplicationEvent;

public class ImapFolderSyncEvent extends ApplicationEvent {

    private final ImapFolder folder;
    private final Type type;

    public ImapFolderSyncEvent(ImapFolder folder, Type type) {
        super(folder);
        this.folder = folder;
        this.type = type;
    }

    public ImapFolder getFolder() {
        return folder;
    }

    public Type getType() {
        return type;
    }

    public enum Type {
        ADDED, REMOVED
    }
}
