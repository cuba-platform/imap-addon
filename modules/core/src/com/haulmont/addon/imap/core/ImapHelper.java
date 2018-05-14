package com.haulmont.addon.imap.core;

import com.haulmont.addon.imap.core.ext.ThreadExtension;
import com.haulmont.addon.imap.dao.ImapDao;
import com.haulmont.addon.imap.entity.*;
import com.haulmont.addon.imap.exception.ImapException;
import com.haulmont.addon.imap.crypto.Encryptor;
import com.haulmont.bali.datastruct.Pair;
import com.sun.mail.imap.IMAPFolder;
import com.sun.mail.imap.IMAPStore;
import org.apache.commons.io.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;

import javax.annotation.PreDestroy;
import javax.inject.Inject;
import javax.mail.*;
import java.io.IOException;
import java.io.InputStream;
import java.io.Serializable;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

@Component("imap_ImapHelper")
public class ImapHelper {

    private final static Logger log = LoggerFactory.getLogger(ImapHelper.class);

    private final ConcurrentMap<MailboxKey, ReadWriteLock> mailBoxLocks = new ConcurrentHashMap<>();
    private final Map<MailboxKey, Pair<ConnectionsParams, IMAPStore>> stores = new HashMap<>();

    private final ConcurrentMap<UUID, IMAPStore> listenerStores = new ConcurrentHashMap<>();
    private final ConcurrentMap<UUID, IMAPFolder> listenerFolders = new ConcurrentHashMap<>();

    private final Encryptor encryptor;
    private final ImapStoreBuilder imapStoreBuilder;
    private final ImapDao dao;

    static {
//        System.setProperty("mail.imap.parse.debug", "true");
//        System.setProperty("mail.mime.decodefilename", "true");
    }

    @Inject
    public ImapHelper(Encryptor encryptor,
                      @SuppressWarnings("CdiInjectionPointsInspection") ImapStoreBuilder imapStoreBuilder,
                      ImapDao dao) {
        this.encryptor = encryptor;
        this.imapStoreBuilder = imapStoreBuilder;
        this.dao = dao;
    }

    @PreDestroy
    public void closeStores() {
        for (Map.Entry<MailboxKey, Pair<ConnectionsParams, IMAPStore>> entry : stores.entrySet()) {
            try {
                entry.getValue().getSecond().close();
            } catch (Exception e) {
                log.warn("Failed to close store for " + entry.getKey(), e);
            }
        }

        for (Map.Entry<UUID, IMAPStore> entry : listenerStores.entrySet()) {
            try {
                entry.getValue().close();
            } catch (Exception e) {
                log.warn("Failed to close listener store for " + entry.getKey(), e);
            }
        }
    }

    public IMAPStore getStore(ImapMailBox box) throws MessagingException {
        log.debug("Accessing imap store for {}", box);

        MailboxKey key = new MailboxKey(box);
        mailBoxLocks.putIfAbsent(key, new ReentrantReadWriteLock());
        //noinspection SynchronizationOnLocalVariableOrMethodParameter
        Lock readLock = mailBoxLocks.get(key).readLock();
        readLock.lock();
        boolean unlockRead = true;
        try {
            Pair<ConnectionsParams, IMAPStore> mailboxStore = stores.get(key);
            String persistedPassword = dao.getPersistedPassword(box);
            if (mailboxStore == null || connectionParamsDiffer(mailboxStore.getFirst(), box, persistedPassword)) {
                readLock.unlock();
                unlockRead = false;
                Lock writeLock = mailBoxLocks.get(key).writeLock();
                writeLock.lock();
                try {
                    buildAndCacheStore(key, box, persistedPassword);
                } finally {
                    writeLock.unlock();
                }
            }
            return stores.get(key).getSecond();
        } finally {
            if (unlockRead) {
                readLock.unlock();
            }
        }
    }

    public IMAPStore getExclusiveStore(ImapMailBox mailBox) throws MessagingException {
        return imapStoreBuilder.buildStore(mailBox, dao.getPersistedPassword(mailBox), false);
    }

    public IMAPFolder getListenerFolder(ImapFolder cubaFolder) {
        return listenerFolders.computeIfAbsent(cubaFolder.getId(), ignore -> {
            ImapMailBox mailBox = cubaFolder.getMailBox();
            IMAPStore store = listenerStores.computeIfAbsent(mailBox.getUuid(), ignore_ -> {
                try {
                    return imapStoreBuilder.buildStore(mailBox, dao.getPersistedPassword(mailBox), false);
                } catch (MessagingException e) {
                    throw new ImapException(e);
                }
            });

            try {

                IMAPFolder folder = (IMAPFolder) store.getFolder(cubaFolder.getName());
                folder.open(Folder.READ_WRITE);

                return folder;
            } catch (MessagingException e) {
                throw new ImapException(e);
            }
        });
    }

    public <T> T doWithFolder(ImapMailBox mailBox, String folderFullName, Task<IMAPFolder, T> task) {
        return doWithFolder(mailBox, folderFullName, false, task);
    }

    public <T> T doWithFolder(ImapMailBox mailBox, String folderFullName, boolean readOnly, Task<IMAPFolder, T> task) {
        log.debug("perform '{}' for {} of mailbox {}", task.getDescription(), folderFullName, mailBox);
        MailboxKey mailboxKey = new MailboxKey(mailBox);
        FolderKey key = new FolderKey(mailboxKey, folderFullName);
        Store store = null;
        try {
            store  = getExclusiveStore(mailBox);
            IMAPFolder folder = (IMAPFolder) store.getFolder(key.getFolderFullName());
            if (canHoldMessages(folder) && !folder.isOpen()) {
                folder.open(readOnly ? Folder.READ_ONLY : Folder.READ_WRITE);
            }
            T result = task.getAction().apply(folder);
            return task.isHasResult() ? result : null;

        } catch (MessagingException e) {
            throw new ImapException(
                    String.format("error performing task '%s' for folder with key '%s'", task.getDescription(), key),
                    e
            );
        } finally {
            if (store != null) {
                try {
                    store.close();
                } catch (MessagingException e) {
                    log.warn("Failed to close store with folder {} for task {}", key, task.getDescription());
                }
            }
        }
    }

    public Flags cubaFlags(ImapMailBox mailBox) {
        Flags cubaFlags = new Flags();
        cubaFlags.add(mailBox.getCubaFlag());
        return cubaFlags;
    }

    @SuppressWarnings("SameParameterValue")
    boolean supportsCapability(ImapMailBox mailBox, String capabilityName) {
        return Optional.ofNullable(
                stores.get(new MailboxKey(mailBox))
        ).map(Pair::getFirst)
                .map(params -> params.supportedCapabilities.contains(capabilityName))
                .orElse(false);
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

        return !Objects.equals(oldParams.encryptedPassword, encryptedPassword(newConfig, persistedPassword));
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
        IMAPStore store = imapStoreBuilder.buildStore(box, persistedPassword, true);

        ConnectionsParams connectionsParams = new ConnectionsParams(box, encryptedPassword(box, persistedPassword));
        if (store.hasCapability(ThreadExtension.CAPABILITY_NAME)) {
            connectionsParams.supportedCapabilities.add(ThreadExtension.CAPABILITY_NAME);
        }
        stores.put(key, new Pair<>(connectionsParams, store));
    }

    private String encryptedPassword(ImapMailBox mailBox, String persistedPassword) {
        String password = mailBox.getAuthentication().getPassword();
        if (!Objects.equals(password, persistedPassword)) {
            password = encryptor.getEncryptedPassword(mailBox);
        }
        return password;
    }

    public static boolean canHoldMessages(Folder folder) throws MessagingException {
        return (folder.getType() & Folder.HOLDS_MESSAGES) != 0;
    }

    public static class Body {
        private final String text;
        private final boolean html;

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
        private final ImapSecureMode secureMode;
        private final boolean useProxy;

        private final boolean webProxy;
        private final String proxyHost;
        private final Integer proxyPort;

        private final String encryptedPassword;
        private final Set<String> supportedCapabilities = new HashSet<>();

        ConnectionsParams(ImapMailBox mailBox, String encryptedPassword) {
            this.secureMode = mailBox.getSecureMode();
            ImapProxy proxy = mailBox.getProxy();
            this.useProxy = proxy != null;
            if (proxy != null) {
                this.webProxy = Boolean.TRUE.equals(proxy.getWebProxy());
                this.proxyHost = proxy.getHost();
                this.proxyPort = proxy.getPort();
            } else {
                this.webProxy = false;
                this.proxyHost = null;
                this.proxyPort = null;
            }

            this.encryptedPassword = encryptedPassword;
        }
    }
}
