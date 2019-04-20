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

package com.haulmont.addon.imap.entity.listener;

import com.haulmont.addon.imap.entity.ImapMailBox;
import com.haulmont.addon.imap.crypto.Encryptor;
import com.haulmont.addon.imap.sync.ImapMailboxSyncActivationEvent;
import com.haulmont.cuba.core.EntityManager;
import com.haulmont.cuba.core.PersistenceTools;
import com.haulmont.cuba.core.global.Events;
import com.haulmont.cuba.core.listener.*;
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
                                            AfterUpdateEntityListener<ImapMailBox>,
                                            BeforeDeleteEntityListener<ImapMailBox> {

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
        events.publish(new ImapMailboxSyncActivationEvent(entity, ImapMailboxSyncActivationEvent.Type.DEACTIVATE));
    }

    private void setEncryptedPassword(ImapMailBox entity) {
        log.debug("Encrypt password for {}", entity);
        String encryptedPassword = encryptor.getEncryptedPassword(entity);
        entity.getAuthentication().setPassword(encryptedPassword);
    }

    @Override
    public void onBeforeDelete(ImapMailBox entity, EntityManager entityManager) {
        events.publish(new ImapMailboxSyncActivationEvent(entity, ImapMailboxSyncActivationEvent.Type.DEACTIVATE));
    }

    @Override
    public void onAfterInsert(ImapMailBox entity, Connection connection) {
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronizationAdapter(){
            public void afterCommit() {
                events.publish(new ImapMailboxSyncActivationEvent(entity, ImapMailboxSyncActivationEvent.Type.ACTIVATE));
            }
        });
    }

    @Override
    public void onAfterUpdate(ImapMailBox entity, Connection connection) {
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronizationAdapter(){
            public void afterCommit() {
                events.publish(new ImapMailboxSyncActivationEvent(entity, ImapMailboxSyncActivationEvent.Type.ACTIVATE));
            }
        });
    }
}
