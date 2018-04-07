package com.haulmont.addon.imap.api.scheduling;

import com.haulmont.addon.imap.entity.ImapFolder;
import com.haulmont.addon.imap.entity.ImapMailBox;
import com.haulmont.addon.imap.events.BaseImapEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

public abstract class AbstractFolderTask implements Runnable {
    protected final static Logger log = LoggerFactory.getLogger(ImapScheduling.class);

    final ImapFolder cubaFolder;
    final ImapMailBox mailBox;
    final ImapScheduling scheduling;

    AbstractFolderTask(ImapMailBox mailBox, ImapFolder cubaFolder, ImapScheduling scheduling) {
        this.cubaFolder = cubaFolder;
        this.mailBox = mailBox;
        this.scheduling = scheduling;
    }

    @Override
    public void run() {
        scheduling.fireEvents(cubaFolder, makeEvents());
    }

    abstract List<? extends BaseImapEvent> makeEvents();
}


