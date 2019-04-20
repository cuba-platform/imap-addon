/*
 * Copyright (c) 2008-2019 Haulmont.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.haulmont.addon.imap.core;

import com.haulmont.addon.imap.config.ImapConfig;
import com.haulmont.addon.imap.crypto.Encryptor;
import com.haulmont.addon.imap.entity.ImapMailBox;
import com.haulmont.addon.imap.entity.ImapProxy;
import com.haulmont.addon.imap.entity.ImapSecureMode;
import com.haulmont.cuba.core.global.FileLoader;
import com.haulmont.cuba.core.global.FileStorageException;
import com.sun.mail.imap.IMAPStore;
import com.sun.mail.util.MailSSLSocketFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.mail.MessagingException;
import javax.mail.Session;
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
import java.util.Arrays;
import java.util.Objects;
import java.util.Properties;

@Component
public class ImapStoreBuilder {

    private final static Logger log = LoggerFactory.getLogger(ImapStoreBuilder.class);

    private final FileLoader fileLoader;
    private final ImapConfig config;
    private final Encryptor encryptor;

    @Autowired
    public ImapStoreBuilder(FileLoader fileLoader,
                            @SuppressWarnings("SpringJavaInjectionPointsAutowiringInspection") ImapConfig config,
                            Encryptor encryptor) {
        this.fileLoader = fileLoader;
        this.config = config;
        this.encryptor = encryptor;
    }

    IMAPStore buildStore(ImapMailBox mailBox, String password, boolean decryptPassword) throws MessagingException {
        String protocol = mailBox.getSecureMode() == ImapSecureMode.TLS ? "imaps" : "imap";

        Properties props = new Properties(System.getProperties());
        props.setProperty("mail.store.protocol", protocol);
        String timeout = "" + config.getTimeoutSeconds() * 1000;
        props.setProperty("mail." + protocol + ".connectiontimeout", timeout);
        props.setProperty("mail." + protocol + ".timeout", timeout);
        if (mailBox.getSecureMode() == ImapSecureMode.STARTTLS) {
            props.setProperty("mail.imap.starttls.enable", "true");
        }
        props.setProperty("mail.debug", "" + config.getDebug());

        if (mailBox.getSecureMode() != null) {
            MailSSLSocketFactory socketFactory = getMailSSLSocketFactory(mailBox);
            props.put("mail." + protocol + ".ssl.socketFactory", socketFactory);
        }

        ImapProxy proxy = mailBox.getProxy();
        if (proxy != null) {
            String proxyType = Boolean.TRUE.equals(proxy.getWebProxy()) ? "proxy" : "socks";
            props.put("mail." + protocol + "." + proxyType + ".host", proxy.getHost());
            props.put("mail." + protocol + "." + proxyType + ".port", proxy.getPort());
        }

        Session session = Session.getInstance(props, null);
        session.setDebug(config.getDebug());

        IMAPStore store = (IMAPStore) session.getStore(protocol);
        String passwordToConnect = decryptPassword ? decryptedPassword(mailBox, password) : password;
        store.connect(mailBox.getHost(), mailBox.getPort(), mailBox.getAuthentication().getUsername(), passwordToConnect);

        return store;
    }

    private String decryptedPassword(ImapMailBox mailBox, String persistedPassword) {
        String password = mailBox.getAuthentication().getPassword();
        if (Objects.equals(password, persistedPassword)) {
            password = encryptor.getPlainPassword(mailBox);
        }
        return password;
    }

    private MailSSLSocketFactory getMailSSLSocketFactory(ImapMailBox box) throws MessagingException {
        MailSSLSocketFactory socketFactory;
        try {
            socketFactory = new MailSSLSocketFactory();
            if (config.getTrustAllCertificates()) {
                log.debug("Configure factory to trust all certificates");
                socketFactory.setTrustAllHosts(true);
            } else {
                socketFactory.setTrustAllHosts(false);
                if (box.getRootCertificate() != null) {
                    log.debug("Configure factory to trust only known certificates and certificated from file#{}", box.getRootCertificate().getId());
                    try ( InputStream rootCert = fileLoader.openStream(box.getRootCertificate()) ) {
                        socketFactory.setTrustManagers(new TrustManager[] {new UnifiedTrustManager(rootCert) });
                    } catch (FileStorageException | GeneralSecurityException | IOException e) {
                        throw new RuntimeException("SSL error", e);
                    }
                }
            }
        } catch (GeneralSecurityException e) {
            throw new MessagingException("SSL Socket factory exception", e);
        }
        return socketFactory;
    }

    private static class UnifiedTrustManager implements X509TrustManager {
        private X509TrustManager defaultTrustManager;
        private X509TrustManager localTrustManager;

        UnifiedTrustManager(InputStream rootCertStream) {
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
                log.warn("Can't build SSL Trust Manager", e);
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
