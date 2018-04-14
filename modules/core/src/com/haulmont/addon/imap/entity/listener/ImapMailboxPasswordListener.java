package com.haulmont.addon.imap.entity.listener;

import com.haulmont.addon.imap.entity.ImapMailBox;
import com.haulmont.addon.imap.crypto.Encryptor;
import com.haulmont.cuba.core.EntityManager;
import com.haulmont.cuba.core.PersistenceTools;
import com.haulmont.cuba.core.listener.BeforeInsertEntityListener;
import com.haulmont.cuba.core.listener.BeforeUpdateEntityListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import javax.inject.Inject;

@Component("imapcomponent_MailboxPasswordListener")
public class ImapMailboxPasswordListener implements BeforeInsertEntityListener<ImapMailBox>,
                                                    BeforeUpdateEntityListener<ImapMailBox> {

    private final static Logger log = LoggerFactory.getLogger(ImapMailboxPasswordListener.class);

    private final Encryptor encryptor;

    private final PersistenceTools persistenceTools;

    @SuppressWarnings("CdiInjectionPointsInspection")
    @Inject
    public ImapMailboxPasswordListener(Encryptor encryptor, PersistenceTools persistenceTools) {
        this.encryptor = encryptor;
        this.persistenceTools = persistenceTools;
    }

    @Override
    public void onBeforeInsert(ImapMailBox entity, EntityManager entityManager) {
        setEncryptedPassword(entity);
    }

    @Override
    public void onBeforeUpdate(ImapMailBox entity, EntityManager entityManager) {
        if (persistenceTools.isDirty(entity.getAuthentication(), "password")) {
            setEncryptedPassword(entity);
        }
    }

    private void setEncryptedPassword(ImapMailBox entity) {
        log.debug("Encrypt password for {}", entity);
        String encryptedPassword = encryptor.getEncryptedPassword(entity);
        entity.getAuthentication().setPassword(encryptedPassword);
    }


}