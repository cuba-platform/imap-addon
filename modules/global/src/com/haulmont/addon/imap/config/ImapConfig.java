package com.haulmont.addon.imap.config;

import com.haulmont.cuba.core.config.*;
import com.haulmont.cuba.core.config.defaults.DefaultBoolean;
import com.haulmont.cuba.core.config.defaults.DefaultInt;

@Source(type = SourceType.DATABASE)
public interface ImapConfig extends Config {

    @Property("cuba.email.imap.server.trustAllCertificates")
    @DefaultBoolean(false)
    boolean getTrustAllCertificates();
    void setTrustAllCertificates(boolean value);

    @Property("cuba.email.imap.updateBatchSize")
    @DefaultInt(100)
    int getUpdateBatchSize();
    void setUpdateBatchSize(int value);

    @Property("cuba.email.imap.clearCustomFlags")
    @DefaultBoolean(false)
    boolean getClearCustomFlags();
    void setClearCustomFlags(boolean value);

}
