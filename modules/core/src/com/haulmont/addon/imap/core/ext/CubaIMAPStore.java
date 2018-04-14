package com.haulmont.addon.imap.core.ext;

import com.sun.mail.iap.ProtocolException;
import com.sun.mail.imap.IMAPStore;
import com.sun.mail.imap.protocol.IMAPProtocol;

import javax.mail.Session;
import javax.mail.URLName;
import java.io.IOException;

public class CubaIMAPStore extends IMAPStore {
    public CubaIMAPStore(Session session, URLName url) {
        super(session, url);
    }

    protected CubaIMAPStore(Session session, URLName url,
                            String name, boolean isSSL) {
        super(session, url, name, isSSL);
    }

    @Override
    protected IMAPProtocol newIMAPProtocol(String host, int port) throws IOException, ProtocolException {
        return new ThreadExtension.CubaIMAPProtocol(name, host, port,
                session.getProperties(),
                isSSL,
                logger
        );
    }
}
