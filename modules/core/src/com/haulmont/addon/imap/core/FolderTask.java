package com.haulmont.addon.imap.core;

import com.sun.mail.imap.IMAPFolder;

public class FolderTask<T> extends Task<IMAPFolder, T> {
    private boolean closeFolder;

    public FolderTask(String description, boolean hasResult, boolean closeFolder, MessageFunction<IMAPFolder, T> action) {
        super(description, hasResult, action);
        this.closeFolder = closeFolder;
    }

    public boolean isCloseFolder() {
        return closeFolder;
    }

    public void setCloseFolder(boolean closeFolder) {
        this.closeFolder = closeFolder;
    }
}
