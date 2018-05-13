package com.haulmont.addon.imap.core.protocol;

import com.haulmont.addon.imap.core.ext.ThreadExtension;
import com.sun.mail.iap.ProtocolException;
import com.sun.mail.imap.protocol.FetchItem;
import com.sun.mail.imap.protocol.IMAPProtocol;
import com.sun.mail.util.MailLogger;

import java.io.IOException;
import java.util.Properties;

public class CubaIMAPProtocol extends IMAPProtocol {

    public CubaIMAPProtocol(String name, String host, int port, Properties props,
                            boolean isSSL, MailLogger logger) throws IOException, ProtocolException {
        super(name, host, port, props, isSSL, logger);
        if (hasCapability("ENABLE")) {
            enable("UTF8=ACCEPT");
        }
    }
    @Override
    public FetchItem[] getFetchItems() {
        return new FetchItem[] { ThreadExtension.FETCH_ITEM };
    }
}
