package com.haulmont.components.imap.core;

import com.haulmont.bali.datastruct.Pair;
import com.haulmont.components.imap.config.ImapConfig;
import com.haulmont.components.imap.entity.MailBox;
import com.haulmont.components.imap.entity.MailSecureMode;
import com.haulmont.cuba.core.global.FileLoader;
import com.haulmont.cuba.core.global.FileStorageException;
import com.sun.mail.iap.Argument;
import com.sun.mail.iap.Response;
import com.sun.mail.imap.IMAPFolder;
import com.sun.mail.imap.protocol.*;
import com.sun.mail.util.MailSSLSocketFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import javax.inject.Inject;
import javax.mail.*;
import javax.mail.search.SearchException;
import javax.mail.search.SearchTerm;
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
import java.util.concurrent.ConcurrentMap;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
public class ImapHelper {

    private final static Logger log = LoggerFactory.getLogger(ImapHelper.class);

    private volatile Map<MailBox, Store> boxesStores = new ConcurrentHashMap<>();

    private volatile ConcurrentMap<String, Object> folderLocks = new ConcurrentHashMap<>();

    private static final Object lock = new Object();

    @Inject
    private FileLoader fileLoader;

    @Inject
    private ImapConfig config;

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

    public <T> T doWithFolder(MailBox mailBox, IMAPFolder folder, FolderTask<T> task) {
        String key = String.format("%s:%d[%s]", mailBox.getHost(), mailBox.getPort(), folder.getFullName());
        folderLocks.putIfAbsent(key, new Object());
        Object lock = folderLocks.get(key);
        synchronized (lock) {
            try {
                if (!folder.isOpen()) {
                    folder.open(Folder.READ_WRITE);
                }
                T result = task.action.apply(folder);
                return task.isHasResult() ? result : null;
            } catch (MessagingException e) {
                throw new RuntimeException(
                        String.format("error performing task '%s' for folder '%s' in mailbox '%s:%d'",
                                task.getDescription(), folder.getFullName(), mailBox.getHost(), mailBox.getPort()
                        ), e
                );
            } finally {
                if (task.isCloseFolder()) {
                    try {
                        folder.close(false);
                    } catch (MessagingException e) {
                        log.warn("Can't close folder {} for mailBox {}:{}", folder.getFullName(), mailBox.getHost(), mailBox.getPort());
                    }
                }
            }

        }
    }

    public List<Pair<Long, Flags>> searchUidAndFlags(IMAPFolder folder, SearchTerm searchTerm) throws MessagingException {
        long[] uids = (long[]) folder.doCommand(uidSearchCommand(searchTerm));
        if (uids != null && uids.length > 0) {
            return (List<Pair<Long, Flags>>) folder.doCommand(uidFetchWithFlagsCommand(uids));
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
                            while ((num = ir.readLong()) != -1)
                                v.add(num);
                            r[i] = null;
                        }
                    }

                    // Copy the vector into 'matches'
                    int vsize = v.size();
                    matches = new long[vsize];
                    for (int i = 0; i < vsize; i++)
                        matches[i] = v.get(i);
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

    public IMAPFolder.ProtocolCommand uidFetchWithFlagsCommand(long[] uids) {
        return protocol -> {

            String uidsString = Arrays.stream(uids).mapToObj(String::valueOf).collect(Collectors.joining(","));
            Response[] responses = protocol.command(String.format("UID FETCH %s (UID FLAGS)", uidsString), null);

            List<Pair<Long, Flags>> result = new ArrayList<>();
            for (Response response : responses) {
                if (response == null || !(response instanceof FetchResponse))
                    continue;

                FetchResponse fr = (FetchResponse) response;
                if (fr.getItemCount() >= 2) {
                    Flags flags = null;
                    Long uid = null;
                    for (int i = 0; i < fr.getItemCount(); i++) {
                        Item item = fr.getItem(i);
                        if (item instanceof Flags) {
                            flags = (Flags) item;
                        } else if (item instanceof UID) {
                            uid = ((UID) item).uid;
                        }
                    }
                    if (flags != null && uid != null) {
                        result.add(new Pair<>(uid, flags));
                    }
                }
            }

            protocol.notifyResponseHandlers(responses);
            protocol.handleResult(responses[responses.length-1]);

            return result;

        };
    }

    protected MailSSLSocketFactory getMailSSLSocketFactory(MailBox box) throws MessagingException {
        MailSSLSocketFactory socketFactory = null;
        try {
            socketFactory = new MailSSLSocketFactory();
            if (config.getTrusAllCertificates()) {
                socketFactory.setTrustAllHosts(true);
            } else if (box.getRootCertificate() != null) {
                try ( InputStream rootCert = fileLoader.openStream(box.getRootCertificate()) ) {
                    socketFactory.setTrustManagers(new TrustManager[] {new UnifiedTrustManager(rootCert) });
                } catch (FileStorageException | GeneralSecurityException | IOException e) {
                    throw new RuntimeException("SSL error", e);
                }
            }
        } catch (GeneralSecurityException e) {
            throw new MessagingException("SSL Socket factory exception", e);
        }
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

    public static class FolderTask<T> {
        private String description;
        private MessageFunction<IMAPFolder, T> action;
        private boolean hasResult;
        private boolean closeFolder;

        public FolderTask(String description, boolean hasResult, boolean closeFolder, MessageFunction<IMAPFolder, T> action) {
            this.description = description;
            this.action = action;
            this.hasResult = hasResult;
            this.closeFolder = closeFolder;
        }

        public String getDescription() {
            return description;
        }

        public void setDescription(String description) {
            this.description = description;
        }

        public MessageFunction<IMAPFolder, T> getAction() {
            return action;
        }

        public void setAction(MessageFunction<IMAPFolder, T> action) {
            this.action = action;
        }

        public boolean isHasResult() {
            return hasResult;
        }

        public void setHasResult(boolean hasResult) {
            this.hasResult = hasResult;
        }

        public boolean isCloseFolder() {
            return closeFolder;
        }

        public void setCloseFolder(boolean closeFolder) {
            this.closeFolder = closeFolder;
        }
    }

    @FunctionalInterface
    public interface MessageFunction<INPUT, OUTPUT> {
        OUTPUT apply(INPUT input) throws MessagingException;
    }
}
