package com.haulmon.components.imap.service;

import com.haulmon.components.imap.entity.MailBox;
import com.haulmon.components.imap.entity.MailSecureMode;
import com.sun.mail.util.MailSSLSocketFactory;
import org.springframework.stereotype.Service;

import javax.mail.Folder;
import javax.mail.MessagingException;
import javax.mail.Session;
import javax.mail.Store;
import java.security.GeneralSecurityException;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;
import java.util.stream.Collectors;

@Service(ImapService.NAME)
public class ImapServiceBean implements ImapService {

    @Override
    public void testConnection(MailBox box) throws MessagingException {
        getStore(box);
    }

    @Override
    public List<String> fetchFolders(MailBox box) throws MessagingException {
        Store store = getStore(box);

        Folder defaultFolder = store.getDefaultFolder();

        return Arrays.stream(defaultFolder.list()).map(Folder::getName).collect(Collectors.toList());
    }

    private Store getStore(MailBox box) throws MessagingException {

        String protocol = box.getSecureMode() == MailSecureMode.TLS ? "imaps" : "imap";

        Properties props = System.getProperties();
        props.setProperty("mail.store.protocol", protocol);

        MailSSLSocketFactory socketFactory = getMailSSLSocketFactory(box);
        props.put("mail.imaps.ssl.socketFactory", socketFactory);

        Session session = Session.getDefaultInstance(props, null);

        Store store = session.getStore(protocol);
        store.connect(box.getHost(),box.getAuthentication().getUsername(), box.getAuthentication().getPassword());

        return store;
    }

    private MailSSLSocketFactory getMailSSLSocketFactory(MailBox box) throws MessagingException {
        MailSSLSocketFactory socketFactory= null;
        try {
            socketFactory = new MailSSLSocketFactory();
        } catch (GeneralSecurityException e) {
            throw new MessagingException("SSL Socket factory exception", e);
        }
        socketFactory.setTrustAllHosts(true);
        return socketFactory;
    }
}