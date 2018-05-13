package com.haulmont.addon.imap.core.protocol;

import com.sun.mail.imap.IMAPFolder;
import com.sun.mail.imap.IMAPStore;
import com.sun.mail.imap.protocol.ListInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.mail.Folder;
import javax.mail.FolderClosedException;

public class CubaIMAPFolder extends IMAPFolder {
    private final static Logger log = LoggerFactory.getLogger(CubaIMAPFolder.class);

    public CubaIMAPFolder(String fullName, char separator, IMAPStore store, Boolean isNamespace) {
        super(fullName, separator, store, isNamespace);
    }

    public CubaIMAPFolder(ListInfo li, IMAPStore store) {
        super(li, store);
    }

    @Override
    protected void checkOpened() throws FolderClosedException {
        try {
            super.checkOpened();
        } catch (Exception e) {
            log.warn("IMAP folder {} is closed, try to reopen it", this.fullName);
            try {
                if ((type & Folder.HOLDS_MESSAGES) != 0) {
                    open(READ_WRITE);
                }
            } catch (Exception ignore) {
                throw e;
            }
        }
    }
}
