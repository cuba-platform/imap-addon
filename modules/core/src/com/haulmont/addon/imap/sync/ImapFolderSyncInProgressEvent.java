package com.haulmont.addon.imap.sync;

import org.springframework.context.ApplicationEvent;

public class ImapFolderSyncInProgressEvent extends ApplicationEvent {

    private final ImapFolderSyncAction action;

    public ImapFolderSyncInProgressEvent(ImapFolderSyncAction action) {
        super(action);
        this.action = action;
    }

    ImapFolderSyncAction getAction() {
        return action;
    }
}
