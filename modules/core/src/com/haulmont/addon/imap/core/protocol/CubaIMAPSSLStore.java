package com.haulmont.addon.imap.core.protocol;

import javax.mail.Session;
import javax.mail.URLName;

public class CubaIMAPSSLStore extends CubaIMAPStore {
    public CubaIMAPSSLStore(Session session, URLName url) {
        super(session, url, "imaps", true);
    }
}
