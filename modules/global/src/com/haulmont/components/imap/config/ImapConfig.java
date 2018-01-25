package com.haulmont.components.imap.config;

import com.haulmont.cuba.core.config.*;
import com.haulmont.cuba.core.config.defaults.DefaultInt;
import com.haulmont.cuba.core.config.defaults.DefaultInteger;
import com.haulmont.cuba.core.config.defaults.DefaultString;
import com.haulmont.cuba.core.entity.FileDescriptor;

@Source(type = SourceType.APP)
public interface ImapConfig extends Config {

    @Property("cuba.email.imap.server.mailBoxProcessingTimeoutSec")
    @DefaultInt(30)
    int getMailBoxProcessingTimeoutSec();
    void setMailBoxProcessingTimeoutSec(int timeout);
}
