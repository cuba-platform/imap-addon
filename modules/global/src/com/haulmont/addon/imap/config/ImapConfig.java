package com.haulmont.addon.imap.config;

import com.haulmont.cuba.core.config.*;
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

    @Property("imap.fetchMessagesMaxParallelism")
    @DefaultInt(10)
    int getFetchMessagesMaxParallelism();
    void setFetchMessagesMaxParallelism(int value);

    @Property("imap.debug")
    @DefaultBoolean(false)
    boolean getDebug();
    void setDebug(boolean value);

}
