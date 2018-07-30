package com.haulmont.addon.imap.entity;

import com.haulmont.chile.core.datatypes.impl.EnumClass;

import javax.annotation.Nullable;


public enum ImapSyncStatus implements EnumClass<String> {

    IN_SYNC("IN_SYNC"),
    ADDED("ADDED"),
    REMAIN("REMAIN"),
    MISSED("MISSED"),
    MOVED("MOVED"),
    REMOVED("REMOVED"),
    ;

    private String id;

    ImapSyncStatus(String value) {
        this.id = value;
    }

    public String getId() {
        return id;
    }

    @Nullable
    public static ImapSyncStatus fromId(String id) {
        for (ImapSyncStatus at : ImapSyncStatus.values()) {
            if (at.getId().equals(id)) {
                return at;
            }
        }
        return null;
    }
}