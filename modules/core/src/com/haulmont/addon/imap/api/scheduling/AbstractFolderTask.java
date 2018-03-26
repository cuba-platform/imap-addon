package com.haulmont.addon.imap.api.scheduling;

import com.haulmont.addon.imap.entity.ImapFolder;
import com.haulmont.addon.imap.entity.ImapMailBox;
import com.haulmont.addon.imap.events.BaseImapEvent;
import com.sun.mail.imap.IMAPFolder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.concurrent.RecursiveAction;

public abstract class AbstractFolderTask extends RecursiveAction {
    protected final static Logger LOG = LoggerFactory.getLogger(ImapScheduling.class);

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


