package com.haulmont.addon.imap.sync;


public interface ImapScheduler {

    String NAME = "imap_ImapScheduler";

    /**
     * IMAP message boxes synchronization
     *
     */
    void syncImap();
}
