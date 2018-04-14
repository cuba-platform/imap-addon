package com.haulmont.addon.imap.sync;

import org.springframework.context.ApplicationEvent;

public class ImapFolderSyncEvent extends ApplicationEvent {

    private final ImapFolderSyncAction action;

    public ImapFolderSyncEvent(ImapFolderSyncAction action) {
        super(action);
        this.action = action;
    }

    ImapFolderSyncAction getAction() {
        return action;
    }
}
