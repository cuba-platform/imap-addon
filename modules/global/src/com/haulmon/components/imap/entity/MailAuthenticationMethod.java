package com.haulmon.components.imap.entity;

import com.haulmont.chile.core.datatypes.impl.EnumClass;

import javax.annotation.Nullable;


public enum MailAuthenticationMethod implements EnumClass<Integer> {

    SIMPLE(10),
    SASL(20);

    private Integer id;

    MailAuthenticationMethod(Integer value) {
        this.id = value;
    }

    public Integer getId() {
        return id;
    }

    @Nullable
    public static MailAuthenticationMethod fromId(Integer id) {
        for (MailAuthenticationMethod at : MailAuthenticationMethod.values()) {
            if (at.getId().equals(id)) {
                return at;
            }
        }
        return null;
    }
}