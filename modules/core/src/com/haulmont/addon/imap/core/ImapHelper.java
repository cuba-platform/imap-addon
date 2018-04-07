package com.haulmont.addon.imap.core;

import com.haulmont.addon.imap.config.ImapConfig;
import com.haulmont.addon.imap.entity.*;
import com.haulmont.addon.imap.exception.ImapException;
import com.haulmont.addon.imap.security.Encryptor;
import com.haulmont.bali.datastruct.Pair;
import com.haulmont.cuba.core.EntityManager;
import com.haulmont.cuba.core.Persistence;
import com.haulmont.cuba.core.Transaction;
import com.haulmont.cuba.core.global.FileLoader;
import com.haulmont.cuba.core.global.FileStorageException;
import com.sun.mail.imap.IMAPFolder;
import com.sun.mail.imap.IMAPMessage;
import com.sun.mail.imap.IMAPStore;
import com.sun.mail.util.MailSSLSocketFactory;
import org.apache.commons.io.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;

import javax.inject.Inject;
import javax.mail.*;
import javax.mail.internet.MimeUtility;
import javax.mail.search.SearchTerm;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;
import java.io.IOException;
import java.io.InputStream;
import java.io.Serializable;
import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;
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
import java.util.concurrent.ConcurrentMap;

@SuppressWarnings({"SpringJavaInjectionPointsAutowiringInspection", "SpringJavaAutowiredFieldsWarningInspection", "CdiInjectionPointsInspection"})
@Component
public class ImapHelper {

    private final static Logger log = LoggerFactory.getLogger(ImapHelper.class);

    private static final String REFERENCES_HEADER = "References";
    private static final String IN_REPLY_TO_HEADER = "In-Reply-To";
    private static final String SUBJECT_HEADER = "Subject";
    public static final String MESSAGE_ID_HEADER = "Message-ID";

    private final ConcurrentMap<MailboxKey, Object> mailBoxLocks = new ConcurrentHashMap<>();
    private final Map<MailboxKey, Pair<ConnectionsParams, IMAPStore>> stores = new HashMap<>();
    private final ConcurrentMap<FolderKey, Object> folderLocks = new ConcurrentHashMap<>();
    private final Map<FolderKey, IMAPFolder> folders = new HashMap<>();

    @Inject
    private FileLoader fileLoader;

    @Inject
    private ImapConfig config;

    @Inject
    private Encryptor encryptor;

    @Inject
    private Persistence persistence;

    public IMAPStore getStore(ImapMailBox box) throws MessagingException {
        return getStore(box, false);
    }

    public IMAPStore getStore(ImapMailBox box, boolean forceConnect) throws MessagingException {
        log.debug("Accessing imap store for {}", box);

        MailboxKey key = mailboxKey(box);
        mailBoxLocks.putIfAbsent(key, new Object());
        Object lock = mailBoxLocks.get(key);
        //noinspection SynchronizationOnLocalVariableOrMethodParameter
        synchronized (lock) {
            Pair<ConnectionsParams, IMAPStore> mailboxStore = stores.get(key);
            String persistedPassword = getPersistedPassword(box);
            if (mailboxStore == null || connectionParamsDiffer(mailboxStore.getFirst(), box, persistedPassword)) {
                buildAndCacheStore(key, box, persistedPassword);
            }

            IMAPStore store = stores.get(key).getSecond();

            if (forceConnect) { //todo: try to prevent excessive connection using some status flags of IMAP
                store.close();
            }
            if (!store.isConnected()) {
                store.connect();
            }
            return store;
        }

    }

    private boolean connectionParamsDiffer(ConnectionsParams oldParams, ImapMailBox newConfig, String persistedPassword) {
        Assert.isTrue(newConfig != null, "New mailbox config shouldn't be null");
        if (oldParams == null) {
            return true;
        }
        if (oldParams.secureMode != newConfig.getSecureMode()) {
            return true;
        }

        if (proxyParamsDiffer(oldParams, newConfig)) {
            return true;
        }

        return !Objects.equals(oldParams.encryptedPassword, enryptedPassword(newConfig, persistedPassword));
    }

    private boolean proxyParamsDiffer(ConnectionsParams oldParams, ImapMailBox newConfig) {
        ImapProxy newProxy = newConfig.getProxy();
        if (!oldParams.useProxy && newProxy == null) {
            return false;
        }
        if (oldParams.useProxy == (newProxy == null)) {
            return true;
        }
        return !Objects.equals(oldParams.webProxy, newProxy.getWebProxy()) ||
                !Objects.equals(oldParams.proxyHost, newProxy.getHost()) ||
                !Objects.equals(oldParams.proxyPort, newProxy.getPort());

    }

    private void buildAndCacheStore(MailboxKey key, ImapMailBox box, String persistedPassword) throws MessagingException {
        String protocol = box.getSecureMode() == ImapSecureMode.TLS ? "imaps" : "imap";

        Properties props = new Properties(System.getProperties());
        props.setProperty("mail.store.protocol", protocol);
        props.setProperty("mail." + protocol + ".connectiontimeout", "5000");
        props.setProperty("mail." + protocol + ".timeout", "5000");
        if (box.getSecureMode() == ImapSecureMode.STARTTLS) {
            props.setProperty("mail.imap.starttls.enable", "true");
        }
//        props.setProperty("mail.debug", "true");

        if (box.getSecureMode() != null) {
            MailSSLSocketFactory socketFactory = getMailSSLSocketFactory(box);
            props.put("mail." + protocol + ".ssl.socketFactory", socketFactory);
        }

        ImapProxy proxy = box.getProxy();
        if (proxy != null) {
            String proxyType = Boolean.TRUE.equals(proxy.getWebProxy()) ? "proxy" : "socks";
            props.put("mail." + protocol + "." + proxyType + ".host", proxy.getHost());
            props.put("mail." + protocol + "." + proxyType + ".port", proxy.getPort());
        }

        Session session = Session.getInstance(props, null);

        IMAPStore store = (IMAPStore) session.getStore(protocol);
        String passwordToConnect = decryptedPassword(box, persistedPassword);
        store.connect(box.getHost(), box.getPort(), box.getAuthentication().getUsername(), passwordToConnect);

        ConnectionsParams connectionsParams = new ConnectionsParams(box, enryptedPassword(box, persistedPassword));
        stores.put(key, new Pair<>(connectionsParams, store));
    }

    private String decryptedPassword(ImapMailBox mailBox, String persistedPassword) {
        String password = mailBox.getAuthentication().getPassword();
        if (Objects.equals(password, persistedPassword)) {
            password = encryptor.getPlainPassword(mailBox);
        }
        return password;
    }

    private String enryptedPassword(ImapMailBox mailBox, String persistedPassword) {
        String password = mailBox.getAuthentication().getPassword();
        if (!Objects.equals(password, persistedPassword)) {
            password = encryptor.getEncryptedPassword(mailBox);
        }
        return password;
    }

    private String getPersistedPassword(ImapMailBox mailBox) {
        try (Transaction ignored = persistence.createTransaction()) {
            EntityManager em = persistence.getEntityManager();
            ImapSimpleAuthentication persisted = em.find(ImapSimpleAuthentication.class, mailBox.getAuthentication().getId());
            return persisted != null ? persisted.getPassword() : null;
        }
    }

    public <T> T doWithFolder(ImapMailBox mailBox, String folderFullName, FolderTask<T> task) {
        log.debug("perform '{}' for {} of mailbox {}", task.getDescription(), folderFullName, mailBox);
        FolderKey key = new FolderKey(mailboxKey(mailBox), folderFullName);
        folderLocks.putIfAbsent(key, new Object());
        Object lock = folderLocks.get(key);
        IMAPFolder folder = null;
        try {
            Store store = getStore(mailBox);
            //noinspection SynchronizationOnLocalVariableOrMethodParameter
            synchronized (lock) {
                log.trace("[{}->{}]lock acquired for '{}'", mailBox, folderFullName, task.getDescription());
                folders.putIfAbsent(key, (IMAPFolder) store.getFolder(folderFullName));
                 folder = folders.get(key);
                if (!folder.isOpen()) {
                    folder.open(Folder.READ_WRITE);
                }
                T result = task.getAction().apply(folder);
                return task.isHasResult() ? result : null;
            }
        } catch (MessagingException e) {
            throw new ImapException(
                    String.format("error performing task '%s' for folder with key '%s'", task.getDescription(), key),
                    e
            );
        } finally {
            if (task.isCloseFolder() && folder != null && folder.isOpen()) {
                try {
                    folder.close(false);
                } catch (MessagingException e) {
                    log.warn("Can't close folder {} for mailBox {}:{}", folderFullName, mailBox.getHost(), mailBox.getPort());
                }
            }
        }
    }

    private MailboxKey mailboxKey(ImapMailBox mailBox) {
        return new MailboxKey(mailBox.getHost(), mailBox.getPort(), mailBox.getAuthentication().getUsername());
    }

    public List<IMAPMessage> search(IMAPFolder folder, SearchTerm searchTerm, ImapMailBox mailBox) throws MessagingException {
        log.debug("search messages in {} with {}", folder.getFullName(), searchTerm) ;

        Message[] messages = folder.search(searchTerm);
        return fetch(folder, mailBox, messages);
    }

    public List<IMAPMessage> getAllByUids(IMAPFolder folder, long[] uids, ImapMailBox mailBox) throws MessagingException {
        if (log.isDebugEnabled()) {
            log.debug("get messages by uids {} in {}", Arrays.toString(uids), folder.getFullName());
        }

        Message[] messages = folder.getMessagesByUID(uids);
        return fetch(folder, mailBox, messages);
    }

    public String getRefId(IMAPMessage message) throws MessagingException {
        String refHeader = message.getHeader(REFERENCES_HEADER, null);
        if (refHeader == null) {
            refHeader = message.getHeader(IN_REPLY_TO_HEADER, null);
        } else {
            refHeader = refHeader.split("\\s+")[0];
        }
        if (refHeader != null && refHeader.length() > 0) {
            return refHeader;
        }

        return null;
    }

    public Long getThreadId(IMAPMessage message) throws MessagingException {
        Object threadItem = message.getItem(ThreadExtension.FETCH_ITEM);
        return threadItem instanceof ThreadExtension.X_GM_THRID ? ((ThreadExtension.X_GM_THRID) threadItem).x_gm_thrid : null;
    }

    public String getSubject(IMAPMessage message) throws MessagingException {
        String subject = message.getHeader(SUBJECT_HEADER, null);
        if (subject != null && subject.length() > 0) {
            return decode(subject);
        } else {
            return "(No Subject)";
        }
    }

    private List<IMAPMessage> fetch(IMAPFolder folder, ImapMailBox mailBox, Message[] messages) throws MessagingException {
        Message[] nonNullMessages = Arrays.stream(messages).filter(Objects::nonNull).toArray(Message[]::new);
        folder.fetch(nonNullMessages, headerProfile(mailBox));
        List<IMAPMessage> result = new ArrayList<>(nonNullMessages.length);
        for (Message message : nonNullMessages) {
            result.add((IMAPMessage) message);
        }
        return result;
    }

    private FetchProfile headerProfile(ImapMailBox mailBox) throws MessagingException {
        FetchProfile profile = new FetchProfile();
        profile.add(FetchProfile.Item.FLAGS);
        profile.add(UIDFolder.FetchProfileItem.UID);
        profile.add(REFERENCES_HEADER);
        profile.add(IN_REPLY_TO_HEADER);
        profile.add(SUBJECT_HEADER);
        profile.add(MESSAGE_ID_HEADER);

        if (getStore(mailBox).hasCapability(ThreadExtension.CAPABILITY_NAME)) {
            profile.add(ThreadExtension.FetchProfileItem.X_GM_THRID);
        }

        return profile;
    }

    private String decode(String val) {
        try {
            return MimeUtility.decodeText(MimeUtility.unfold(val));
        } catch (UnsupportedEncodingException ex) {
            return val;
        }
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

    public Body getText(Part p) throws
            MessagingException, IOException {
        if (p.isMimeType("text/*")) {
            Object content = p.getContent();
            String body = content instanceof InputStream
                    ? IOUtils.toString((InputStream) p.getContent(), StandardCharsets.UTF_8)
                    : content.toString();
            return new Body(body, p.isMimeType("text/html"));
        }

        if (p.isMimeType("multipart/alternative")) {
            Object content = p.getContent();
            if (content instanceof InputStream) {
                return new Body(IOUtils.toString((InputStream) p.getContent(), StandardCharsets.UTF_8), false);
            } else if (content instanceof Multipart) {
                Multipart mp = (Multipart) content;
                Body body = null;
                for (int i = 0; i < mp.getCount(); i++) {
                    Part bp = mp.getBodyPart(i);
                    if (bp.isMimeType("text/plain")) {
                        if (body == null) {
                            body = getText(bp);
                        }
                    } else if (bp.isMimeType("text/html")) {
                        Body b = getText(bp);
                        if (b != null) {
                            return b;
                        }
                    } else {
                        return getText(bp);
                    }
                }
                return body;
            }
        } else if (p.isMimeType("multipart/*")) {
            Object content = p.getContent();
            if (content instanceof InputStream) {
                return new Body(IOUtils.toString((InputStream) p.getContent(), StandardCharsets.UTF_8), false);
            } else if (content instanceof Multipart) {
                Multipart mp = (Multipart) content;
                for (int i = 0; i < mp.getCount(); i++) {
                    Body s = getText(mp.getBodyPart(i));
                    if (s != null) {
                        return s;
                    }
                }
            }
        }

        return null;
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

    public static class Body {
        private String text;
        private boolean html;

        Body(String text, boolean html) {
            this.text = text;
            this.html = html;
        }

        public String getText() {
            return text;
        }

        public boolean isHtml() {
            return html;
        }
    }

    private static class ConnectionsParams implements Serializable {
        private ImapSecureMode secureMode;
        private boolean useProxy;

        private boolean webProxy;
        private String proxyHost;
        private Integer proxyPort;

        private String encryptedPassword;

        ConnectionsParams(ImapMailBox mailBox, String encryptedPassword) {
            this.secureMode = mailBox.getSecureMode();
            ImapProxy proxy = mailBox.getProxy();
            this.useProxy = proxy != null;
            if (proxy != null) {
                this.webProxy = Boolean.TRUE.equals(proxy.getWebProxy());
                this.proxyHost = proxy.getHost();
                this.proxyPort = proxy.getPort();
            }

            this.encryptedPassword = encryptedPassword;
        }
    }

}
