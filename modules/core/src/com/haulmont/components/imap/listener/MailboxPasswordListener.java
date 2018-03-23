package com.haulmont.components.imap.listener;

import com.haulmont.components.imap.security.Encryptor;
import com.haulmont.cuba.core.PersistenceTools;
import org.springframework.stereotype.Component;
import com.haulmont.cuba.core.listener.BeforeInsertEntityListener;
import com.haulmont.cuba.core.EntityManager;
import com.haulmont.components.imap.entity.ImapMailBox;
import com.haulmont.cuba.core.listener.BeforeUpdateEntityListener;

import javax.inject.Inject;

@Component("imapcomponent_MailboxPasswordListener")
public class MailboxPasswordListener implements BeforeInsertEntityListener<ImapMailBox>, BeforeUpdateEntityListener<ImapMailBox> {

    @Inject
    private Encryptor encryptor;

    @Inject
    private PersistenceTools persistenceTools;

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
        String encryptedPassword = encryptor.getEncryptedPassword(entity);
        entity.getAuthentication().setPassword(encryptedPassword);
    }


}