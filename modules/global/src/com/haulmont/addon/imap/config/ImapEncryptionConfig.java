package com.haulmont.addon.imap.config;

import com.haulmont.cuba.core.config.Config;
import com.haulmont.cuba.core.config.Property;
import com.haulmont.cuba.core.config.Source;
import com.haulmont.cuba.core.config.SourceType;

@Source(type = SourceType.APP)
public interface ImapEncryptionConfig extends Config {

    @Property("cuba.email.imap.encryption.key")
    String getEncryptionKey();

    @Property("cuba.email.imap.encryption.iv")
    String getEncryptionIv();
}
