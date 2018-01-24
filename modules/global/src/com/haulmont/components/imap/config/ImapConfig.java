package com.haulmont.components.imap.config;

import com.haulmont.cuba.core.config.*;
import com.haulmont.cuba.core.config.defaults.DefaultInteger;
import com.haulmont.cuba.core.config.defaults.DefaultString;
import com.haulmont.cuba.core.entity.FileDescriptor;

@Source(type = SourceType.DATABASE)
public interface ImapConfig extends Config {

    @Property("cuba.email.imap.server.hostname")
    String getHostname();

    void setHostname(String hostname);

    @Property("cuba.email.imap.server.port")
    @DefaultString("993")
    String getPort();

    void setPort(String port);

    @Property("cuba.email.imap.server.secureConnectionType")
    @EnumStore(EnumStoreMode.ID)
    SecureConnectionType getSecureConnectionType();

    void setSecureConnectionType(SecureConnectionType secureConnectionType);

    @Property("cuba.email.imap.server.certificateAuthority")
    FileDescriptor getServerCertificateAuthority();

    void setServerCertificateAuthority(FileDescriptor serverCertificateAuthority);

    @Property("cuba.email.imap.server.authenticationMethod")
    @EnumStore(EnumStoreMode.ID)
    @DefaultInteger(10)
    AuthenticationMethod getAuthenticationMethod();

    void setAuthenticationMethod(AuthenticationMethod authenticationMethod);

    @Property("cuba.email.imap.client.certificate")
    FileDescriptor getClientCertificate();

    void setClientCertificate(FileDescriptor clientCertificate);
}
