package com.haulmont.components.imap.core;

import com.haulmont.components.imap.entity.MailBox;
import com.haulmont.components.imap.entity.MailSecureMode;
import com.haulmont.cuba.core.global.FileLoader;
import com.sun.mail.imap.IMAPFolder;
import com.sun.mail.util.MailSSLSocketFactory;
import org.springframework.stereotype.Component;

import javax.inject.Inject;
import javax.mail.Folder;
import javax.mail.MessagingException;
import javax.mail.Session;
import javax.mail.Store;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;
import java.io.IOException;
import java.io.InputStream;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class ImapHelper {

    private volatile Map<MailBox, Store> boxesStores = new ConcurrentHashMap<>();

    private static final Object lock = new Object();

    @Inject
    private FileLoader fileLoader;

    public Store getStore(MailBox box) throws MessagingException {
        /*Store store = boxesStores.get(box);
        if (store != null) {
            return store;
        }
        synchronized (lock) { //todo: should be more fine-grained scoped to mailbox
            store = boxesStores.get(box);
            if (store != null) {
                return store;
            }

            String protocol = box.getSecureMode() == MailSecureMode.TLS ? "imaps" : "imap";

            Properties props = System.getProperties();
            props.setProperty("mail.store.protocol", protocol);

            MailSSLSocketFactory socketFactory = getMailSSLSocketFactory(box);
            props.put("mail.imaps.ssl.socketFactory", socketFactory);

            Session session = Session.getDefaultInstance(props, null);

            store = session.getStore(protocol);
            store.connect(box.getHost(), box.getAuthentication().getUsername(), box.getAuthentication().getPassword());
            boxesStores.put(box, store);
        }

        if (!store.isConnected()) {
            *//*if (this.logger.isDebugEnabled()) {
                this.logger.debug("connecting to store [" + MailTransportUtils.toPasswordProtectedString(this.url) + "]");
            }*//*
            store.connect();
        }

        return store;*/
        String protocol = box.getSecureMode() == MailSecureMode.TLS ? "imaps" : "imap";

        Properties props = System.getProperties();
        props.setProperty("mail.store.protocol", protocol);

        MailSSLSocketFactory socketFactory = getMailSSLSocketFactory(box);
        props.put("mail.imaps.ssl.socketFactory", socketFactory);

        Session session = Session.getDefaultInstance(props, null);

        Store store = session.getStore(protocol);
        store.connect(box.getHost(), box.getAuthentication().getUsername(), box.getAuthentication().getPassword());

        return store;
    }

    protected MailSSLSocketFactory getMailSSLSocketFactory(MailBox box) throws MessagingException {
        MailSSLSocketFactory socketFactory = null;
        try {
            socketFactory = new MailSSLSocketFactory();
            socketFactory.setTrustAllHosts(true);
        } catch (GeneralSecurityException e) {
            throw new MessagingException("SSL Socket factory exception", e);
        }
        /*if (box.getRootCertificate() != null) {
            try ( InputStream rootCert = fileLoader.openStream(box.getRootCertificate()) ) {
                socketFactory.setTrustManagers(new TrustManager[] {new UnifiedTrustManager(rootCert) });
            } catch (FileStorageException | GeneralSecurityException | IOException e) {
                throw new RuntimeException("SSL error", e);
            }
        }*/
        return socketFactory;
    }

    public boolean canHoldFolders(IMAPFolder folder) throws MessagingException {
        return (folder.getType() & Folder.HOLDS_FOLDERS) != 0;
    }

    public boolean canHoldMessages(IMAPFolder folder) throws MessagingException {
        return (folder.getType() & Folder.HOLDS_MESSAGES) != 0;
    }

    private static class UnifiedTrustManager implements X509TrustManager {
        private X509TrustManager defaultTrustManager;
        private X509TrustManager localTrustManager;

        public UnifiedTrustManager(InputStream rootCertStream) {
            try {
                this.defaultTrustManager = createTrustManager(null);

                CertificateFactory cf = CertificateFactory.getInstance("X.509");
                Certificate ca = cf.generateCertificate(rootCertStream);

                // Create a KeyStore containing our trusted CAs
                String keyStoreType = KeyStore.getDefaultType();
                KeyStore keyStore = KeyStore.getInstance(keyStoreType);
                keyStore.load(null, null);
                keyStore.setCertificateEntry("rootCA", ca);

                this.localTrustManager = createTrustManager(keyStore);
            } catch (NoSuchAlgorithmException | CertificateException | KeyStoreException | IOException e) {
                e.printStackTrace(); //todo:
            }
        }

        private X509TrustManager createTrustManager(KeyStore store) throws NoSuchAlgorithmException, KeyStoreException {
            String tmfAlgorithm = TrustManagerFactory.getDefaultAlgorithm();
            TrustManagerFactory tmf = TrustManagerFactory.getInstance(tmfAlgorithm);
            tmf.init(store);
            TrustManager[] trustManagers = tmf.getTrustManagers();
            return (X509TrustManager) trustManagers[0];
        }

        public void checkServerTrusted(X509Certificate[] chain, String authType) throws CertificateException {
            try {
                defaultTrustManager.checkServerTrusted(chain, authType);
            } catch (CertificateException ce) {
                localTrustManager.checkServerTrusted(chain, authType);
            }
        }

        @Override
        public void checkClientTrusted(X509Certificate[] chain, String authType) throws CertificateException {
            try {
                defaultTrustManager.checkClientTrusted(chain, authType);
            } catch (CertificateException ce) {
                localTrustManager.checkClientTrusted(chain, authType);
            }
        }

        @Override
        public X509Certificate[] getAcceptedIssuers() {
            X509Certificate[] first = defaultTrustManager.getAcceptedIssuers();
            X509Certificate[] second = localTrustManager.getAcceptedIssuers();
            X509Certificate[] result = Arrays.copyOf(first, first.length + second.length);
            System.arraycopy(second, 0, result, first.length, second.length);
            return result;
        }
    }
}
