package com.haulmont.addon.imap.entity.listener;

import com.haulmont.addon.imap.entity.ImapFolder;
import com.haulmont.addon.imap.entity.ImapMailBox;
import com.haulmont.addon.imap.crypto.Encryptor;
import com.haulmont.addon.imap.sync.ImapMailboxSyncActivationEvent;
import com.haulmont.cuba.core.EntityManager;
import com.haulmont.cuba.core.PersistenceTools;
import com.haulmont.cuba.core.global.Events;
import com.haulmont.cuba.core.listener.AfterDeleteEntityListener;
import com.haulmont.cuba.core.listener.AfterInsertEntityListener;
import com.haulmont.cuba.core.listener.BeforeInsertEntityListener;
import com.haulmont.cuba.core.listener.BeforeUpdateEntityListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronizationAdapter;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import javax.inject.Inject;
import java.sql.Connection;

@Component("imap_MailboxListener")
public class ImapMailboxListener implements BeforeInsertEntityListener<ImapMailBox>,
                                            BeforeUpdateEntityListener<ImapMailBox>,
                                            AfterInsertEntityListener<ImapMailBox>,
                                            AfterDeleteEntityListener<ImapMailBox> {

    private final static Logger log = LoggerFactory.getLogger(ImapMailboxListener.class);

    private final Encryptor encryptor;
    private final PersistenceTools persistenceTools;
    private final Events events;

    @SuppressWarnings("CdiInjectionPointsInspection")
    @Inject
    public ImapMailboxListener(Encryptor encryptor, PersistenceTools persistenceTools, Events events) {
        this.encryptor = encryptor;
        this.persistenceTools = persistenceTools;
        this.events = events;
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

    @Override
    public void onAfterDelete(ImapMailBox entity, Connection connection) {
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronizationAdapter(){
            public void afterCommit() {
                events.publish(new ImapMailboxSyncActivationEvent(entity, ImapMailboxSyncActivationEvent.Type.DEACTIVATE));
            }
        });
    }

    @Override
    public void onAfterInsert(ImapMailBox entity, Connection connection) {
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronizationAdapter(){
            public void afterCommit() {
                events.publish(new ImapMailboxSyncActivationEvent(entity, ImapMailboxSyncActivationEvent.Type.ACTIVATE));
            }
        });
    }
}