package com.haulmont.addon.imap.entity;

import com.haulmont.chile.core.datatypes.impl.EnumClass;

import javax.annotation.Nullable;


public enum ImapAuthenticationMethod implements EnumClass<String> {

    SIMPLE("simple"),
    SASL("sasl");

    private final String id;

    ImapAuthenticationMethod(String value) {
        this.id = value;
    }

    public String getId() {
        return id;
    }

    @Nullable
    public static ImapAuthenticationMethod fromId(String id) {
        for (ImapAuthenticationMethod at : ImapAuthenticationMethod.values()) {
            if (at.getId().equals(id)) {
                return at;
            }
        }
        return null;
    }
}