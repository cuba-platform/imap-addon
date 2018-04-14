package com.haulmont.addon.imap.sync;

import java.util.Objects;
import java.util.UUID;

public class ImapFolderSyncAction {
    private final UUID folderId;
    private final Type type;

    public ImapFolderSyncAction(UUID folderId, Type type) {
        this.folderId = folderId;
        this.type = type;
    }

    UUID getFolderId() {
        return folderId;
    }

    Type getType() {
        return type;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ImapFolderSyncAction that = (ImapFolderSyncAction) o;
        return Objects.equals(folderId, that.folderId) &&
                type == that.type;
    }

    @Override
    public int hashCode() {

        return Objects.hash(folderId, type);
    }

    public enum Type {
        NEW, CHANGED, MISSED
    }
}
