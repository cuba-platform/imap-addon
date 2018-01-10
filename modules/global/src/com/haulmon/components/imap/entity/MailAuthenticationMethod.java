package com.haulmon.components.imap.entity;

import com.haulmont.chile.core.datatypes.impl.EnumClass;

import javax.annotation.Nullable;


public enum MailAuthenticationMethod implements EnumClass<String> {

    SIMPLE("simple"),
    SASL("sasl");

    private String id;

    MailAuthenticationMethod(String value) {
        this.id = value;
    }

    public String getId() {
        return id;
    }

    @Nullable
    public static MailAuthenticationMethod fromId(String id) {
        for (MailAuthenticationMethod at : MailAuthenticationMethod.values()) {
            if (at.getId().equals(id)) {
                return at;
            }
        }
        return null;
    }
}