package com.haulmont.addon.imap.core;

import com.haulmont.addon.imap.core.ext.ThreadExtension;
import com.haulmont.addon.imap.dao.ImapDao;
import com.haulmont.addon.imap.entity.*;
import com.sun.mail.imap.IMAPStore;
import org.apache.commons.io.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import javax.inject.Inject;
import javax.mail.*;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;

@Component("imap_ImapHelper")
public class ImapHelper {

    private final static Logger log = LoggerFactory.getLogger(ImapHelper.class);


    private final ConcurrentMap<UUID, Boolean> supportThreading = new ConcurrentHashMap<>();

    private final ImapStoreBuilder imapStoreBuilder;
    private final ImapDao dao;

    static {
        System.setProperty("mail.imap.parse.debug", "true");
//        System.setProperty("mail.mime.decodefilename", "true");
    }

    @Inject
    public ImapHelper(@SuppressWarnings("CdiInjectionPointsInspection") ImapStoreBuilder imapStoreBuilder,
                      ImapDao dao) {
        this.imapStoreBuilder = imapStoreBuilder;
        this.dao = dao;
    }

    public IMAPStore store(MailboxKey mailboxKey) throws MessagingException {
        ImapMailBox mailBox = dao.findMailBox(mailboxKey.id);
        return buildStore(mailBox);
    }

    public IMAPStore store(MailboxKey mailboxKey, String password) throws MessagingException {
        ImapMailBox mailBox = dao.findMailBox(mailboxKey.id);
        return buildStore(mailBox, password);
    }

    public IMAPStore getStore(ImapMailBox box) throws MessagingException {
        log.debug("Accessing imap store for {}", box);

        String persistedPassword = dao.getPersistedPassword(box);
        return Objects.equals(box.getAuthentication().getPassword(), persistedPassword)
                ? buildStore(box) : buildStore(box, box.getAuthentication().getPassword());
    }

    public Flags cubaFlags(ImapMailBox mailBox) {
        Flags cubaFlags = new Flags();
        cubaFlags.add(mailBox.getCubaFlag());
        return cubaFlags;
    }

    @SuppressWarnings("SameParameterValue")
    boolean supportsThreading(ImapMailBox mailBox) {
        return Boolean.TRUE.equals(supportThreading.get(mailBox.getId()));
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

    private IMAPStore buildStore(ImapMailBox box) throws MessagingException {
        IMAPStore store = imapStoreBuilder.buildStore(box, box.getAuthentication().getPassword(), true);

        supportThreading.put(box.getId(), store.hasCapability(ThreadExtension.CAPABILITY_NAME));
        return store;
    }

    private IMAPStore buildStore(ImapMailBox box, String password) throws MessagingException {
        IMAPStore store = imapStoreBuilder.buildStore(box, password, false);

        supportThreading.put(box.getId(), store.hasCapability(ThreadExtension.CAPABILITY_NAME));
        return store;
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

}
