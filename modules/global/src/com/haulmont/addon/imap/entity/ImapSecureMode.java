package com.haulmont.addon.imap.entity;

import com.haulmont.chile.core.datatypes.impl.EnumClass;

import javax.annotation.Nullable;


public enum ImapSecureMode implements EnumClass<String> {

    STARTTLS("starttls"),
    TLS("tls");

    private String id;

    ImapSecureMode(String value) {
        this.id = value;
    }

    public String getId() {
        return id;
    }

    @Nullable
    public static ImapSecureMode fromId(String id) {
        for (ImapSecureMode at : ImapSecureMode.values()) {
            if (at.getId().equals(id)) {
                return at;
            }
        }
        return null;
    }
}