package com.haulmont.addon.imap.core;

import com.haulmont.addon.imap.config.ImapConfig;
import com.haulmont.addon.imap.core.ext.ThreadExtension;
import com.haulmont.addon.imap.dao.ImapDao;
import com.haulmont.addon.imap.entity.*;
import com.haulmont.addon.imap.events.BaseImapEvent;
import com.haulmont.addon.imap.events.EmailAnsweredImapEvent;
import com.haulmont.addon.imap.exception.ImapException;
import com.haulmont.addon.imap.crypto.Encryptor;
import com.haulmont.bali.datastruct.Pair;
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
import java.util.stream.Collectors;

@Component("imap_ImapHelper")
public class ImapHelper {

    private final static Logger log = LoggerFactory.getLogger(ImapHelper.class);

    private static final String REFERENCES_HEADER = "References";
    private static final String IN_REPLY_TO_HEADER = "In-Reply-To";
    private static final String SUBJECT_HEADER = "Subject";
    public static final String MESSAGE_ID_HEADER = "Message-ID";

    private final ConcurrentMap<MailboxKey, Object> mailBoxLocks = new ConcurrentHashMap<>();
    private final Map<MailboxKey, Pair<ConnectionsParams, IMAPStore>> stores = new HashMap<>();
    private final Map<MailboxKey, Collection<IMAPFolder>> usedFolders = new HashMap<>();
    private final Map<FolderKey, IMAPFolder> folders = new HashMap<>();

    private final FileLoader fileLoader;
    private final ImapConfig config;
    private final Encryptor encryptor;
    private final ImapDao dao;

    @SuppressWarnings("CdiInjectionPointsInspection")
    @Inject
    public ImapHelper(FileLoader fileLoader, ImapConfig config, Encryptor encryptor, ImapDao dao) {
        this.fileLoader = fileLoader;
        this.config = config;
        this.encryptor = encryptor;
        this.dao = dao;
    }

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
            String persistedPassword = dao.getPersistedPassword(box);
            if (mailboxStore == null || connectionParamsDiffer(mailboxStore.getFirst(), box, persistedPassword)) {
                buildAndCacheStore(key, box, persistedPassword);
            }

            IMAPStore store = stores.get(key).getSecond();

            if (forceConnect) {
                store.close();
                store.connect();
                Collection<IMAPFolder> imapFolders = usedFolders.get(key);
                if (imapFolders != null) {
                    for (IMAPFolder folder : imapFolders) {
                        if (folder.isOpen()) {
                            folder.close(false);
                        }
                        if (!folder.isOpen()) {
                            folder.open(Folder.READ_WRITE);
                        }
                    }
                }
            } else if (!store.isConnected()) {
                store.connect();
            }
            return store;
        }
    }

    public IMAPFolder getExclusiveFolder(ImapFolder cubaFolder) throws MessagingException {
        ImapMailBox mailBox = cubaFolder.getMailBox();

        IMAPStore store = makeStore(mailBox, dao.getPersistedPassword(mailBox), false);

        IMAPFolder folder = (IMAPFolder) store.getFolder(cubaFolder.getName());
        folder.open(Folder.READ_WRITE);

        return folder;
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
        IMAPStore store = makeStore(box, persistedPassword, true);

        ConnectionsParams connectionsParams = new ConnectionsParams(box, enryptedPassword(box, persistedPassword));
        stores.put(key, new Pair<>(connectionsParams, store));
    }

    private IMAPStore makeStore(ImapMailBox mailBox, String persistedPassword, boolean useTimeout) throws MessagingException {
        String protocol = mailBox.getSecureMode() == ImapSecureMode.TLS ? "imaps" : "imap";

        Properties props = new Properties(System.getProperties());
        props.setProperty("mail.store.protocol", protocol);
        if (useTimeout) {
            props.setProperty("mail." + protocol + ".connectiontimeout", "5000");
            props.setProperty("mail." + protocol + ".timeout", "5000");
        }
        if (mailBox.getSecureMode() == ImapSecureMode.STARTTLS) {
            props.setProperty("mail.imap.starttls.enable", "true");
        }
//        props.setProperty("mail.debug", "true");

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

        IMAPStore store = (IMAPStore) session.getStore(protocol);
        String passwordToConnect = decryptedPassword(mailBox, persistedPassword);
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

    private String enryptedPassword(ImapMailBox mailBox, String persistedPassword) {
        String password = mailBox.getAuthentication().getPassword();
        if (!Objects.equals(password, persistedPassword)) {
            password = encryptor.getEncryptedPassword(mailBox);
        }
        return password;
    }

    public <T> T doWithFolder(ImapMailBox mailBox, String folderFullName, Task<IMAPFolder, T> task) {
        log.debug("perform '{}' for {} of mailbox {}", task.getDescription(), folderFullName, mailBox);
        MailboxKey mailboxKey = mailboxKey(mailBox);
        FolderKey key = new FolderKey(mailboxKey, folderFullName);
        try {
            Store store = getStore(mailBox);
            synchronized (mailBoxLocks.get(mailboxKey)) {
                log.trace("[{}->{}]lock acquired for '{}'", mailBox, folderFullName, task.getDescription());
                IMAPFolder folder = folders.get(key);
                usedFolders.putIfAbsent(mailboxKey, new ArrayList<>());
                if (folder == null) {
                    folder = (IMAPFolder) store.getFolder(folderFullName);
                    folders.put(key, folder);

                    usedFolders.get(mailboxKey).add(folder);
                }
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
        }
    }

    public Flags cubaFlags(ImapMailBox mailBox) {
        Flags cubaFlags = new Flags();
        cubaFlags.add(mailBox.getCubaFlag());
        return cubaFlags;
    }

    private MailboxKey mailboxKey(ImapMailBox mailBox) {
        return new MailboxKey(mailBox.getHost(), mailBox.getPort(), mailBox.getAuthentication().getUsername());
    }

    public List<IMAPMessage> search(IMAPFolder folder, SearchTerm searchTerm, ImapMailBox mailBox) throws MessagingException {
        log.debug("search messages in {} with {}", folder.getFullName(), searchTerm) ;

        Message[] messages = folder.search(searchTerm);
        return fetch(folder, mailBox, messages);
    }

    public List<IMAPMessage> searchMessageIds(IMAPFolder folder, SearchTerm searchTerm) throws MessagingException {
        log.debug("search messages in {} with {}", folder.getFullName(), searchTerm) ;

        Message[] messages = folder.search(searchTerm);
        FetchProfile fetchProfile = new FetchProfile();
        fetchProfile.add(MESSAGE_ID_HEADER);
        return fetch(folder, fetchProfile, messages);
    }

    public List<IMAPMessage> fetchUids(IMAPFolder folder, Message[] messages) throws MessagingException {
        FetchProfile fetchProfile = new FetchProfile();
        fetchProfile.add(UIDFolder.FetchProfileItem.UID);
        return fetch(folder, fetchProfile, messages);
    }

    public void setAnsweredFlag(ImapMailBox mailBox, Collection<BaseImapEvent> imapEvents) {
        Map<ImapFolder, List<ImapMessage>> answeredMessagesByFolder = imapEvents.stream()
                .filter(event -> event instanceof EmailAnsweredImapEvent)
                .map(BaseImapEvent::getMessage)
                .collect(Collectors.groupingBy(ImapMessage::getFolder));
        for (Map.Entry<ImapFolder, List<ImapMessage>> folderReplies : answeredMessagesByFolder.entrySet()) {
            doWithFolder(
                    mailBox,
                    folderReplies.getKey().getName(),
                    new Task<>("set answered flag", false, folder -> {
                        long[] uids = folderReplies.getValue().stream().mapToLong(ImapMessage::getMsgUid).toArray();
                        Message[] messages = folder.getMessagesByUID(uids);
                        folder.setFlags(
                                messages,
                                new Flags(Flags.Flag.ANSWERED),
                                true
                        );
                        return null;
                    })
            );
        }
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
        return fetch(folder, headerProfile(mailBox), messages);
    }

    private List<IMAPMessage> fetch(IMAPFolder folder, FetchProfile fetchProfile, Message[] messages) throws MessagingException {
        Message[] nonNullMessages = Arrays.stream(messages).filter(Objects::nonNull).toArray(Message[]::new);
        folder.fetch(nonNullMessages, fetchProfile);
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
