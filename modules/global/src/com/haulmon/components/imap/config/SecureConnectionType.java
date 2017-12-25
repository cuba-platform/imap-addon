package com.haulmon.components.imap.config;

import com.haulmont.chile.core.datatypes.impl.EnumClass;

public enum SecureConnectionType implements EnumClass<Integer> {
    START_TLS(10),
    TLS(20);

    private Integer id;

    SecureConnectionType(Integer id) {
        this.id = id;
    }

    @Override
    public Integer getId() {
        return id;
    }

    public static SecureConnectionType fromId(Integer id) {
        for (SecureConnectionType type : SecureConnectionType.values()) {
            if (type.getId().equals(id))
                return type;
        }
        return null;
    }
}
