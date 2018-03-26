package com.haulmont.addon.imap.core;

import java.util.Objects;

public class MessageKey {

    private FolderKey folderKey;

    private long msgUid;

    public MessageKey() {
    }

    public MessageKey(FolderKey folderKey, long msgUid) {
        this.folderKey = folderKey;
        this.msgUid = msgUid;
    }

    public FolderKey getFolderKey() {
        return folderKey;
    }

    public void setFolderKey(FolderKey folderKey) {
        this.folderKey = folderKey;
    }

    public long getMsgUid() {
        return msgUid;
    }

    public void setMsgUid(long msgUid) {
        this.msgUid = msgUid;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        MessageKey that = (MessageKey) o;
        return msgUid == that.msgUid &&
                Objects.equals(folderKey, that.folderKey);
    }

    @Override
    public int hashCode() {

        return Objects.hash(folderKey, msgUid);
    }

    @Override
    public String toString() {
        return folderKey + "#" + msgUid;
    }
}
