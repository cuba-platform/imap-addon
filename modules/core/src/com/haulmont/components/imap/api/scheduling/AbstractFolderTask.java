package com.haulmont.components.imap.api.scheduling;

import com.haulmont.components.imap.entity.ImapFolder;
import com.haulmont.components.imap.entity.ImapMailBox;
import com.haulmont.components.imap.events.BaseImapEvent;
import com.sun.mail.imap.IMAPFolder;

import java.util.List;
import java.util.concurrent.RecursiveAction;

public abstract class AbstractFolderTask extends RecursiveAction {
    final ImapFolder cubaFolder;
    final ImapMailBox mailBox;
    final IMAPFolder folder;
    final ImapScheduling scheduling;

    public AbstractFolderTask(ImapMailBox mailBox, ImapFolder cubaFolder, IMAPFolder folder, ImapScheduling scheduling) {
        this.cubaFolder = cubaFolder;
        this.mailBox = mailBox;
        this.folder = folder;
        this.scheduling = scheduling;
    }

    @Override
    protected void compute() {
        scheduling.fireEvents(cubaFolder, makeEvents());
    }

    abstract List<? extends BaseImapEvent> makeEvents();
}


