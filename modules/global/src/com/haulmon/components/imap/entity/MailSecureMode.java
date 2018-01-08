package com.haulmon.components.imap.entity;

import com.haulmont.chile.core.datatypes.impl.EnumClass;

import javax.annotation.Nullable;


public enum MailSecureMode implements EnumClass<Integer> {

    STARTTLS(10),
    TLS(20);

    private Integer id;

    MailSecureMode(Integer value) {
        this.id = value;
    }

    public Integer getId() {
        return id;
    }

    @Nullable
    public static MailSecureMode fromId(Integer id) {
        for (MailSecureMode at : MailSecureMode.values()) {
            if (at.getId().equals(id)) {
                return at;
            }
        }
        return null;
    }
}