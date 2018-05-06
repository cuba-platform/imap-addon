package com.haulmont.addon.imap.core;

import java.util.Objects;

class FolderKey {

    private final MailboxKey mailboxKey;

    private final String folderFullName;

    FolderKey(MailboxKey mailboxKey, String folderFullName) {
        this.mailboxKey = mailboxKey;
        this.folderFullName = folderFullName;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        FolderKey folderKey = (FolderKey) o;
        return Objects.equals(mailboxKey, folderKey.mailboxKey) &&
                Objects.equals(folderFullName, folderKey.folderFullName);
    }

    @Override
    public int hashCode() {

        return Objects.hash(mailboxKey, folderFullName);
    }

    @Override
    public String toString() {
        return mailboxKey +
                "[" + folderFullName + "]";
    }
}
