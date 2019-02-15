package com.haulmont.addon.imap.config;

import com.haulmont.cuba.core.config.Config;
import com.haulmont.cuba.core.config.Property;
import com.haulmont.cuba.core.config.Source;
import com.haulmont.cuba.core.config.SourceType;
import com.haulmont.cuba.core.config.defaults.DefaultBoolean;
import com.haulmont.cuba.core.config.defaults.DefaultInt;

@SuppressWarnings("unused")
@Source(type = SourceType.DATABASE)
public interface ImapConfig extends Config {

    @Property("imap.server.trustAllCertificates")
    @DefaultBoolean(false)
    boolean getTrustAllCertificates();
    void setTrustAllCertificates(boolean value);

    @Property("imap.updateBatchSize")
    @DefaultInt(100)
    int getUpdateBatchSize();
    void setUpdateBatchSize(int value);

    @Property("imap.clearCustomFlags")
    @DefaultBoolean(false)
    boolean getClearCustomFlags();
    void setClearCustomFlags(boolean value);

    @Property("imap.debug")
    @DefaultBoolean(false)
    boolean getDebug();
    void setDebug(boolean value);

    @Property("imap.timeoutSeconds")
    @DefaultInt(5)
    int getTimeoutSeconds();
    void setTimeoutSeconds(int value);

    @Property("imap.syncTimeout")
    @DefaultInt(5)
    int getSyncTimeout();
    void setSyncTimeout(int value);

}
