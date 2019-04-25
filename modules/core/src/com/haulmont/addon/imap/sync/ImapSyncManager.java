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

package com.haulmont.addon.imap.sync;

import com.haulmont.addon.imap.config.ImapConfig;
import com.haulmont.addon.imap.dao.ImapDao;
import com.haulmont.addon.imap.entity.ImapFolder;
import com.haulmont.addon.imap.entity.ImapMailBox;
import com.haulmont.addon.imap.sync.events.ImapEvents;
import com.haulmont.cuba.core.sys.AppContext;
import com.haulmont.cuba.security.app.Authentication;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.stereotype.Component;

import javax.annotation.Nonnull;
import javax.annotation.PostConstruct;
import javax.inject.Inject;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

@Component("imap_SyncManager")
public class ImapSyncManager implements AppContext.Listener, Ordered {

    private final static Logger log = LoggerFactory.getLogger(ImapSyncManager.class);

    private final ImapDao dao;
    private final ImapEvents imapEvents;
    private final Authentication authentication;

    @Inject
    private ImapConfig imapConfig;

    private final ExecutorService executor = Executors.newCachedThreadPool(new ThreadFactory() {
        private final AtomicInteger threadNumber = new AtomicInteger(1);

        @Override
        public Thread newThread(@Nonnull Runnable r) {
            Thread thread = new Thread(
                    r, "ImapMailBoxSync-" + threadNumber.getAndIncrement()
            );
            thread.setDaemon(true);
            return thread;
        }
    });

    private final ConcurrentMap<UUID, ScheduledExecutorService> syncRefreshers = new ConcurrentHashMap<>();

    @SuppressWarnings("CdiInjectionPointsInspection")
    @Inject
    public ImapSyncManager(ImapDao dao,
                           ImapEvents imapEvents,
                           Authentication authentication) {

        this.dao = dao;
        this.imapEvents = imapEvents;
        this.authentication = authentication;
    }

    @PostConstruct
    public void listenContext() {
        AppContext.addListener(this);
    }

    @Override
    public void applicationStarted() {
        authentication.begin();
        try {
            for (ImapMailBox mailBox : dao.findMailBoxes()) {
                log.debug("{}: synchronizing", mailBox);
                CompletableFuture.runAsync(() -> imapEvents.init(mailBox), executor);
            }
        } finally {
            authentication.end();
        }
    }

    @Override
    public void applicationStopped() {
        try {
            executor.shutdownNow();
            executor.awaitTermination(1, TimeUnit.SECONDS);
        } catch (Exception e) {
            log.warn("Exception while shutting down executor", e);
        }

        for (Map.Entry<UUID, ScheduledExecutorService> scheduledExecutorServiceEntry : syncRefreshers.entrySet()) {
            ScheduledExecutorService scheduledExecutorService = scheduledExecutorServiceEntry.getValue();
            try {
                scheduledExecutorService.shutdownNow();
                scheduledExecutorService.awaitTermination(1, TimeUnit.SECONDS);
            } catch (Exception e) {
                log.warn("Exception while shutting down scheduled executor for mailBox#" + scheduledExecutorServiceEntry.getKey(), e);
            }
        }

        authentication.begin();
        try {
            for (ImapMailBox mailBox : dao.findMailBoxes()) {
                try {
                    imapEvents.shutdown(mailBox);
                } catch (Exception e) {
                    log.warn("Exception while shutting down imapEvents for mailbox " + mailBox, e);
                }
            }
        } finally {
            authentication.end();
        }
    }

    @Override
    public int getOrder() {
        return LOWEST_PLATFORM_PRECEDENCE;
    }

    public void runEventsEmitter(UUID mailboxId) {
        ImapMailBox mailBox = dao.findMailBox(mailboxId);
        if (mailBox == null) {
            return;
        }
        for (ImapFolder cubaFolder : mailBox.getProcessableFolders()) {
            imapEvents.handleNewMessages(cubaFolder);
            imapEvents.handleMissedMessages(cubaFolder);
            imapEvents.handleChangedMessages(cubaFolder);
        }
    }

}
