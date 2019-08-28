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

package com.haulmont.addon.imap.sync.events;

import com.haulmont.addon.imap.api.ImapFlag;
import com.haulmont.addon.imap.config.ImapConfig;
import com.haulmont.addon.imap.core.ImapOperations;
import com.haulmont.addon.imap.dao.ImapMessageSyncDao;
import com.haulmont.addon.imap.entity.*;
import com.haulmont.addon.imap.events.*;
import com.haulmont.cuba.core.EntityManager;
import com.haulmont.cuba.core.Persistence;
import com.haulmont.cuba.core.Query;
import com.haulmont.cuba.core.Transaction;
import com.haulmont.cuba.core.global.TimeSource;
import com.haulmont.cuba.core.sys.EntityFetcher;
import com.haulmont.cuba.security.app.Authentication;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import javax.inject.Inject;
import javax.mail.Flags;
import java.util.*;
import java.util.stream.Collectors;

@Component(ImapStandardEventsGenerator.NAME)
public class ImapStandardEventsGenerator extends ImapEventsBatchedGenerator {

    private final static Logger log = LoggerFactory.getLogger(ImapStandardEventsGenerator.class);

    static final String NAME = "imap_StandardEventsGenerator";

    @Inject
    private ImapMessageSyncDao messageSyncDao;

    @Inject
    private Authentication authentication;

    @Inject
    private Persistence persistence;

    @Inject
    private TimeSource timeSource;

    @Inject
    private EntityFetcher entityFetcher;

    @Inject
    private ImapConfig imapConfig;

    @Inject
    private ImapOperations imapOperations;

    @Override
    public void init(ImapMailBox imapMailBox) {
        batchSize = imapConfig.getEventsBatchSize();
    }

    @Override
    public void shutdown(ImapMailBox imapMailBox) {

    }

    @Override
    public Collection<? extends BaseImapEvent> generateForNewMessages(ImapFolder cubaFolder, int batchSize) {
        authentication.begin();
        try {
            Collection<ImapMessage> newMessages = messageSyncDao.findMessagesWithSyncStatus(
                    cubaFolder.getId(), ImapSyncStatus.ADDED, batchSize);

            Collection<BaseImapEvent> newMessageEvents = newMessages.stream()
                    .map(NewEmailImapEvent::new)
                    .collect(Collectors.toList());

            messageSyncDao.removeMessagesSyncs(newMessages.stream().map(ImapMessage::getId).collect(Collectors.toList()));

            return newMessageEvents;
        } catch (Exception e) {
            log.error("New messages events for " + cubaFolder.getName() + " failure", e);
            return Collections.emptyList();
        } finally {
            authentication.end();
        }
    }

    @Override
    public Collection<? extends BaseImapEvent> generateForChangedMessages(ImapFolder cubaFolder, int batchSize) {
        authentication.begin();
        int i = 0;
        try {
            Collection<BaseImapEvent> updateMessageEvents = new ArrayList<>(batchSize);
            while (i < batchSize) {
                Collection<ImapMessageSync> remainMessageSyncs = messageSyncDao.findMessagesSyncs(
                        cubaFolder.getId(), ImapSyncStatus.REMAIN, batchSize);

                if (remainMessageSyncs.isEmpty()) {
                    break;
                }

                for (ImapMessageSync messageSync : remainMessageSyncs) {
                    List<BaseImapEvent> events = generateUpdateEvents(messageSync);
                    if (!events.isEmpty()) {
                        updateMessageEvents.addAll(events);
                        i++;
                    }
                }

                messageSyncDao.removeMessagesSyncs(remainMessageSyncs.stream()
                        .map(ms -> ms.getMessage().getId())
                        .distinct()
                        .collect(Collectors.toList()));
            }

            return updateMessageEvents;
        } catch (Exception e) {
            log.error("Changed messages events for " + cubaFolder.getName() + " failure", e);
            return Collections.emptyList();
        } finally {
            authentication.end();
        }

    }

    private List<BaseImapEvent> generateUpdateEvents(ImapMessageSync messageSync) {
        Flags newFlags = messageSync.getImapFlags();
        ImapMessage msg = messageSync.getMessage();
        Flags oldFlags = msg.getImapFlags();

        List<BaseImapEvent> modificationEvents = new ArrayList<>(3);
        if (!Objects.equals(newFlags, oldFlags)) {
            log.trace("Update message {}. Old flags: {}, new flags: {}", msg, oldFlags, newFlags);

            HashMap<ImapFlag, Boolean> changedFlagsWithNewValue = new HashMap<>();
            if (isSeen(newFlags, oldFlags)) {
                modificationEvents.add(new EmailSeenImapEvent(msg));
            }

            if (isAnswered(newFlags, oldFlags)) {
                modificationEvents.add(new EmailAnsweredImapEvent(msg));
            }

            for (String userFlag : oldFlags.getUserFlags()) {
                if (!newFlags.contains(userFlag)) {
                    changedFlagsWithNewValue.put(new ImapFlag(userFlag), false);
                }
            }

            for (Flags.Flag systemFlag : oldFlags.getSystemFlags()) {
                if (!newFlags.contains(systemFlag)) {
                    changedFlagsWithNewValue.put(new ImapFlag(ImapFlag.SystemFlag.valueOf(systemFlag)), false);
                }
            }

            for (String userFlag : newFlags.getUserFlags()) {
                if (!oldFlags.contains(userFlag)) {
                    changedFlagsWithNewValue.put(new ImapFlag(userFlag), true);
                }
            }

            for (Flags.Flag systemFlag : newFlags.getSystemFlags()) {
                if (!oldFlags.contains(systemFlag)) {
                    changedFlagsWithNewValue.put(new ImapFlag(ImapFlag.SystemFlag.valueOf(systemFlag)), true);
                }
            }

            modificationEvents.add(new EmailFlagChangedImapEvent(msg, changedFlagsWithNewValue));
            msg.setImapFlags(newFlags);
            msg.setUpdateTs(timeSource.currentTimestamp());
//            msg.setThreadId();
            authentication.begin();
            try (Transaction tx = persistence.createTransaction()) {
                EntityManager em = persistence.getEntityManager();
                em.merge(msg);

                tx.commit();
            } finally {
                authentication.end();
            }

        }
        return modificationEvents;
    }

    private boolean isSeen(Flags newFlags, Flags oldFlags) {
        return !oldFlags.contains(Flags.Flag.SEEN)
                && newFlags.contains(Flags.Flag.SEEN);
    }

    private boolean isAnswered(Flags newFlags, Flags oldFlags) {
        return !oldFlags.contains(Flags.Flag.ANSWERED)
                && newFlags.contains(Flags.Flag.ANSWERED);
    }

    @Override
    @Transactional
    public Collection<? extends BaseImapEvent> generateForMissedMessages(ImapFolder cubaFolder, int batchSize) {
        authentication.begin();
        try {
            try (Transaction tx = persistence.createTransaction()) {
                EntityManager em = persistence.getEntityManager();
                Collection<ImapMessage> removed = messageSyncDao.findMessagesWithSyncStatus(
                        cubaFolder.getId(), ImapSyncStatus.REMOVED, batchSize);
                Collection<ImapMessageSync> moved = messageSyncDao.findMessagesSyncs(
                        cubaFolder.getId(), ImapSyncStatus.MOVED, batchSize);

                Collection<BaseImapEvent> missedMessageEvents = new ArrayList<>(removed.size() + moved.size());
                List<Integer> missedMessageNums = new ArrayList<>(removed.size() + moved.size());
                for (ImapMessage imapMessage : removed) {
                    missedMessageEvents.add(new EmailDeletedImapEvent(imapMessage));
                    missedMessageNums.add(imapMessage.getMsgNum());
                    em.remove(imapMessage);
                }
                for (ImapMessageSync imapMessageSync : moved) {
                    ImapMessage message = imapMessageSync.getMessage();
                    ImapFolder oldFolder = imapMessageSync.getOldFolder();

                    imapMessageSync.setStatus(ImapSyncStatus.REMAIN);
                    em.merge(imapMessageSync);
                    missedMessageEvents.add(new EmailMovedImapEvent(message, oldFolder));
                    missedMessageNums.add(message.getMsgNum());
                }

                recalculateMessageNumbers(cubaFolder, missedMessageNums);
                tx.commit();
                return missedMessageEvents;

            }
        } catch (Exception e) {
            log.error("Missed messages events for " + cubaFolder.getName() + " failure", e);
            return Collections.emptyList();
        } finally {
            authentication.end();
        }
    }

    private void recalculateMessageNumbers(ImapFolder cubaFolder, List<Integer> messageNumbers) {
        messageNumbers.sort(Comparator.naturalOrder());
        authentication.begin();
        try (Transaction tx = persistence.createTransaction()) {
            EntityManager em = persistence.getEntityManager();
            for (int i = 0; i < messageNumbers.size(); i++) {
                String queryString = "update imap$Message m set m.msgNum = m.msgNum-" + (i + 1) +
                        " where m.folder.id = :mailFolderId and m.msgNum > :msgNum";
                if (i < messageNumbers.size() - 1) {
                    queryString += " and m.msgNum < :topMsgNum";
                }
                Query query = em.createQuery(queryString)
                        .setParameter("mailFolderId", cubaFolder.getId())
                        .setParameter("msgNum", messageNumbers.get(i));
                if (i < messageNumbers.size() - 1) {
                    query.setParameter("topMsgNum", messageNumbers.get(i + 1));
                }
                query.executeUpdate();
            }
            tx.commit();
        } finally {
            authentication.end();
        }
    }
}
