package com.haulmon.components.imap.entity;

import com.haulmont.chile.core.datatypes.impl.EnumClass;

import javax.annotation.Nullable;


public enum MailEventType implements EnumClass<Integer> {

    NEW_EMAIL(10),
    EMAIL_SEEN(20),
    NEW_ANSWER(30),
    EMAIL_MOVED(40),
    FLAGS_UPDATED(50),
    EMAIL_DELETED(60),
    NEW_THREAD(70);

    private Integer id;

    MailEventType(Integer value) {
        this.id = value;
    }

    public Integer getId() {
        return id;
    }

    @Nullable
    public static MailEventType fromId(Integer id) {
        for (MailEventType at : MailEventType.values()) {
            if (at.getId().equals(id)) {
                return at;
            }
        }
        return null;
    }
}