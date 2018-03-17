package com.haulmont.components.imap.core;

import java.util.Objects;

public class FolderKey {

    private MailboxKey mailboxKey;

    private String folderFullName;

    public FolderKey() {
    }

    public FolderKey(MailboxKey mailboxKey, String folderFullName) {
        this.mailboxKey = mailboxKey;
        this.folderFullName = folderFullName;
    }

    public MailboxKey getMailboxKey() {
        return mailboxKey;
    }

    public void setMailboxKey(MailboxKey mailboxKey) {
        this.mailboxKey = mailboxKey;
    }

    public String getFolderFullName() {
        return folderFullName;
    }

    public void setFolderFullName(String folderFullName) {
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
