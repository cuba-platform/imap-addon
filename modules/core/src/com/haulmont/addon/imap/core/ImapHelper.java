package com.haulmont.addon.imap.core;

import com.haulmont.addon.imap.config.ImapConfig;
import com.haulmont.addon.imap.entity.*;
import com.haulmont.addon.imap.security.Encryptor;
import com.haulmont.bali.datastruct.Pair;
import com.haulmont.cuba.core.EntityManager;
import com.haulmont.cuba.core.Persistence;
import com.haulmont.cuba.core.Transaction;
import com.haulmont.cuba.core.global.FileLoader;
import com.haulmont.cuba.core.global.FileStorageException;
import com.sun.mail.iap.Argument;
import com.sun.mail.iap.Response;
import com.sun.mail.imap.IMAPFolder;
import com.sun.mail.imap.protocol.*;
import com.sun.mail.util.MailSSLSocketFactory;
import org.apache.commons.io.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;

import javax.inject.Inject;
import javax.mail.*;
import javax.mail.internet.InternetHeaders;
import javax.mail.internet.MimeUtility;
import javax.mail.search.SearchException;
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

@Component
public class ImapHelper {

    private final static Logger LOG = LoggerFactory.getLogger(ImapHelper.class);

    private static final String REFERENCES_HEADER = "References";
    private static final String IN_REPLY_TO_HEADER = "In-Reply-To";
    private static final String SUBJECT_HEADER = "Subject";
    private static final String MESSAGE_ID_HEADER = "Message-ID";

    private final ConcurrentMap<MailboxKey, Object> mailBoxLocks = new ConcurrentHashMap<>();
    private final Map<MailboxKey, Pair<ConnectionsParams, Store>> stores = new HashMap<>();
    private final ConcurrentMap<FolderKey, Object> folderLocks = new ConcurrentHashMap<>();
    private final ConcurrentMap<MessageKey, Object> msgLocks = new ConcurrentHashMap<>();

    @Inject
    private FileLoader fileLoader;

    @Inject
    private ImapConfig config;

    @Inject
    private Encryptor encryptor;

    @Inject
    private Persistence persistence;

    public Store getStore(ImapMailBox box) throws MessagingException {
        LOG.debug("Accessing imap store for {}", box);

        MailboxKey key = mailboxKey(box);
        mailBoxLocks.putIfAbsent(key, new Object());
        Object lock = mailBoxLocks.get(key);
        synchronized (lock) {
            Pair<ConnectionsParams, Store> mailboxStore = stores.get(key);
            String persistedPassword = getPersistedPassword(box);
            if (mailboxStore == null || connectionParamsDiffer(mailboxStore.getFirst(), box, persistedPassword)) {
                buildAndCacheStore(key, box, persistedPassword);
            }

            Store store = stores.get(key).getSecond();
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

        Store store = session.getStore(protocol);
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
        try (Transaction tx = persistence.createTransaction()) {
            EntityManager em = persistence.getEntityManager();
            ImapSimpleAuthentication persisted = em.find(ImapSimpleAuthentication.class, mailBox.getAuthentication().getId());
            return persisted != null ? persisted.getPassword() : null;
        }
    }

    public <T> T doWithFolder(ImapMailBox mailBox, IMAPFolder folder, FolderTask<T> task) {
        LOG.debug("perform '{}' for {} of mailbox {}", task.getDescription(), folder.getFullName(), mailBox);
        FolderKey key = new FolderKey(mailboxKey(mailBox), folder.getFullName());
        folderLocks.putIfAbsent(key, new Object());
        Object lock = folderLocks.get(key);
        synchronized (lock) {
            LOG.trace("[{}->{}]lock acquired for '{}'", mailBox, folder.getFullName(), task.getDescription());
            try {
                if (!folder.isOpen()) {
                    folder.open(Folder.READ_WRITE);
                }
                T result = task.getAction().apply(folder);
                return task.isHasResult() ? result : null;
            } catch (MessagingException e) {
                throw new RuntimeException(
                        String.format("error performing task '%s' for folder with key '%s'", task.getDescription(), key),
                        e
                );
            } finally {
                if (task.isCloseFolder() && folder.isOpen()) {
                    try {
                        folder.close(false);
                    } catch (MessagingException e) {
                        LOG.warn("Can't close folder {} for mailBox {}:{}", folder.getFullName(), mailBox.getHost(), mailBox.getPort());
                    }
                }
            }

        }
    }

    public <T> T doWithMsg(ImapMessage message, IMAPFolder imapFolder, Task<ImapMessage, T> task) {
        LOG.debug("perform message task '{}' for {} of folder {}", task.getDescription(), message, imapFolder.getFullName());
        ImapFolder folder = message.getFolder();
        ImapMailBox mailBox = folder.getMailBox();
        MessageKey key = new MessageKey(
                new FolderKey(mailboxKey(mailBox), folder.getName()),
                message.getMsgUid()
        );
        msgLocks.putIfAbsent(key, new Object());
        Object lock = msgLocks.get(key);
        synchronized (lock) {
            LOG.trace("[{}->{}]lock acquired for '{}'", imapFolder.getFullName(), message, task.getDescription());
            try {
                if (!imapFolder.isOpen()) {
                    imapFolder.open(Folder.READ_WRITE);
                }
                T result = task.getAction().apply(message);
                return task.isHasResult() ? result : null;
            } catch (MessagingException e) {
                throw new RuntimeException(
                        String.format("error performing task '%s' for msg with key '%s'", task.getDescription(), key), e
                );
            }

        }
    }

    private MailboxKey mailboxKey(ImapMailBox mailBox) {
        return new MailboxKey(mailBox.getHost(), mailBox.getPort(), mailBox.getAuthentication().getUsername());
    }

    public List<MsgHeader> search(IMAPFolder folder, SearchTerm searchTerm) throws MessagingException {
        LOG.debug("search messages in {} with {}", folder.getFullName(), searchTerm) ;
        return getAllByUids( folder, (long[]) folder.doCommand(uidSearchCommand(searchTerm)) );
    }

    public List<MsgHeader> getAllByUids(IMAPFolder folder, long[] uids) throws MessagingException {
        if (LOG.isDebugEnabled()) {
            LOG.debug("get messages by uids {} in {}", Arrays.toString(uids), folder.getFullName());
        }
        if (uids != null && uids.length > 0) {
            return (List<MsgHeader>) folder.doCommand(uidFetchWithFlagsCommand(uids));
        }
        return Collections.emptyList();
    }

    private IMAPFolder.ProtocolCommand uidSearchCommand(SearchTerm searchTerm) {
        return protocol -> {
            try {
                Argument args = new SearchSequence().generateSequence(searchTerm, null);
                args.writeAtom("ALL");

                Response[] r = protocol.command("UID SEARCH", args);

                Response response = r[r.length-1];
                long[] matches = null;

                // Grab all SEARCH responses
                if (response.isOK()) { // command succesful
                    List<Long> v = new ArrayList<>();
                    long num;
                    for (int i = 0, len = r.length; i < len; i++) {
                        if (!(r[i] instanceof IMAPResponse))
                            continue;

                        IMAPResponse ir = (IMAPResponse)r[i];
                        // There *will* be one SEARCH response.
                        if (ir.keyEquals("SEARCH")) {
                            while ((num = ir.readLong()) != -1) {
                                v.add(num);
                            }
                            r[i] = null;
                        }
                    }

                    // Copy the vector into 'matches'
                    int vsize = v.size();
                    matches = new long[vsize];
                    for (int i = 0; i < vsize; i++) {
                        matches[i] = v.get(i);
                    }
                }

                // dispatch remaining untagged responses
                protocol.notifyResponseHandlers(r);
                protocol.handleResult(response);
                return matches;
            } catch (IOException | SearchException e) {
                throw new RuntimeException("can't perform search with term " + searchTerm, e);
            }

        };
    }

    private IMAPFolder.ProtocolCommand uidFetchWithFlagsCommand(long[] uids) {
        return protocol -> {
            try {
                String uidsString = Arrays.stream(uids).mapToObj(String::valueOf).collect(Collectors.joining(","));
                boolean hasThreadingExtension = protocol.hasCapability("X-GM-EXT-1");
                String fetchUnits = "UID FLAGS " + buildMsgHeadersUnit(protocol);
                if (hasThreadingExtension) {
                    fetchUnits += " " + ThreadExtension.ITEM_NAME;
                }

                Response[] responses = protocol.command(String.format("UID FETCH %s (%s)", uidsString, fetchUnits), null);

                List<MsgHeader> result = new ArrayList<>();
                for (Response response : responses) {
                    if (response == null || !(response instanceof FetchResponse))
                        continue;

                    FetchResponse fr = (FetchResponse) response;
                    if (fr.getItemCount() >= 2) {
                        Flags flags = null;
                        Long uid = null;
                        Long threadId = null;
                        String referenceId = null;
                        String subject = null;
                        String messageId = null;

                        for (int i = 0; i < fr.getItemCount(); i++) {
                            Item item = fr.getItem(i);
                            LOG.trace("Processing item#{}: {}", i, item.getClass().getName());
                            if (item instanceof Flags) {
                                flags = (Flags) item;
                            } else if (item instanceof UID) {
                                uid = ((UID) item).uid;
                            } else if (item instanceof ThreadExtension.X_GM_THRID) {
                                threadId = ((ThreadExtension.X_GM_THRID) item).x_gm_thrid;
                            } else if (item instanceof RFC822DATA || item instanceof BODY) {
                                InputStream headerStream;
                                boolean isHeader;
                                if (item instanceof RFC822DATA) { // IMAP4
                                    headerStream =
                                            ((RFC822DATA) item).getByteArrayInputStream();
                                    isHeader = ((RFC822DATA) item).isHeader();
                                } else {    // IMAP4rev1
                                    headerStream =
                                            ((BODY) item).getByteArrayInputStream();
                                    isHeader = ((BODY) item).isHeader();
                                }
                                if (isHeader) {
                                    InternetHeaders h = new InternetHeaders();
                                    h.load(headerStream);
                                    String refHeader = h.getHeader(REFERENCES_HEADER, null);
                                    if (refHeader == null) {
                                        refHeader = h.getHeader(IN_REPLY_TO_HEADER, null);
                                    } else {
                                        refHeader = refHeader.split("\\s+")[0];
                                    }
                                    if (refHeader != null && refHeader.length() > 0) {
                                        referenceId = refHeader;
                                    }

                                    String subjectHeader = h.getHeader(SUBJECT_HEADER, null);
                                    if (subjectHeader != null && subjectHeader.length() > 0) {
                                        subject = decode(subjectHeader);
                                    } else {
                                        subject = "(No Subject)";
                                    }

                                    messageId = h.getHeader(MESSAGE_ID_HEADER, null);

                                }
                            }
                        }
                        if (flags != null && uid != null) {
                            result.add(new MsgHeader(uid, flags, subject, messageId, referenceId, threadId));
                        }
                    }
                }

                protocol.notifyResponseHandlers(responses);
                protocol.handleResult(responses[responses.length - 1]);

                return result;
            } catch (MessagingException e) {
                throw new RuntimeException("can't perform fetch", e);
            }
        };
    }

    private String decode(String val) {
        try {
            return MimeUtility.decodeText(MimeUtility.unfold(val));
        } catch (UnsupportedEncodingException ex) {
            return val;
        }
    }

    private String buildMsgHeadersUnit(IMAPProtocol protocol) {
        return
                (protocol.isREV1() ? "BODY.PEEK[HEADER.FIELDS (" : "RFC822.HEADER.LINES (") +
                REFERENCES_HEADER + " " + IN_REPLY_TO_HEADER + " " + SUBJECT_HEADER + " " + MESSAGE_ID_HEADER +
                (protocol.isREV1() ? ")]" : ")");
    }

    protected MailSSLSocketFactory getMailSSLSocketFactory(ImapMailBox box) throws MessagingException {
        MailSSLSocketFactory socketFactory = null;
        try {
            socketFactory = new MailSSLSocketFactory();
            if (config.getTrustAllCertificates()) {
                LOG.debug("Configure factory to trust all certificates");
                socketFactory.setTrustAllHosts(true);
            } else {
                socketFactory.setTrustAllHosts(false);
                if (box.getRootCertificate() != null) {
                    LOG.debug("Configure factory to trust only known certificates and certificated from file#{}", box.getRootCertificate().getId());
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

    public static class Body {
        private String text;
        private boolean html;

        public Body(String text, boolean html) {
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
