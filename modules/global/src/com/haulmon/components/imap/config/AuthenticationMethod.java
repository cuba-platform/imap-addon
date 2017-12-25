package com.haulmon.components.imap.config;

import com.haulmont.chile.core.datatypes.impl.EnumClass;

public enum AuthenticationMethod implements EnumClass<Integer> {
    SIMPLE_PASSWORD(10),
    OAUTH2(20)/*,
    SASL(30)*/;

    private Integer id;

    AuthenticationMethod(Integer id) {
        this.id = id;
    }

    @Override
    public Integer getId() {
        return id;
    }

    public static AuthenticationMethod fromId(Integer id) {
        for (AuthenticationMethod mechanism : AuthenticationMethod.values()) {
            if (mechanism.getId().equals(id))
                return mechanism;
        }
        return null;
    }
}
