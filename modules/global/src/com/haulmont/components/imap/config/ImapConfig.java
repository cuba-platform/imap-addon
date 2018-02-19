package com.haulmont.components.imap.config;

import com.haulmont.cuba.core.config.*;
import com.haulmont.cuba.core.config.defaults.DefaultBoolean;
import com.haulmont.cuba.core.config.defaults.DefaultInt;
import com.haulmont.cuba.core.config.defaults.DefaultInteger;
import com.haulmont.cuba.core.config.defaults.DefaultString;
import com.haulmont.cuba.core.entity.FileDescriptor;

@Source(type = SourceType.DATABASE)
public interface ImapConfig extends Config {

    @Property("cuba.email.imap.server.trustAllCertificates")
    @DefaultBoolean(false)
    boolean getTrusAllCertificates();
    void setTrusAllCertificates(boolean value);

    @Property("cuba.email.imap.server.updateBatchSize")
    @DefaultInt(100)
    int getUpdateBatchSize();
    void setUpdateBatchSize(int value);

}
