package com.haulmon.components.imap.entity;

import com.haulmont.chile.core.datatypes.impl.EnumClass;

import javax.annotation.Nullable;


public enum MailSecureMode implements EnumClass<String> {

    STARTTLS("starttls"),
    TLS("tls");

    private String id;

    MailSecureMode(String value) {
        this.id = value;
    }

    public String getId() {
        return id;
    }

    @Nullable
    public static MailSecureMode fromId(String id) {
        for (MailSecureMode at : MailSecureMode.values()) {
            if (at.getId().equals(id)) {
                return at;
            }
        }
        return null;
    }
}