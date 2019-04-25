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
import com.haulmont.addon.imap.core.ImapHelper;
import com.haulmont.addon.imap.core.ImapOperations;
import com.haulmont.addon.imap.dao.ImapDao;
import com.haulmont.addon.imap.dao.ImapMessageSyncDao;
import com.haulmont.addon.imap.entity.ImapFolder;
import com.haulmont.addon.imap.entity.ImapMailBox;
import com.haulmont.addon.imap.entity.ImapMessage;
import com.haulmont.cuba.core.Persistence;
import com.haulmont.cuba.core.global.Metadata;
import com.haulmont.cuba.core.global.TimeSource;
import com.haulmont.cuba.security.app.Authentication;
import com.sun.mail.imap.IMAPFolder;
import com.sun.mail.imap.IMAPMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import javax.inject.Inject;
import javax.mail.MessagingException;
import java.util.List;

@Component(ImapFlaglessSynchronizer.NAME)
public class ImapFlaglessSynchronizer extends ImapSynchronizer {
    private final static Logger log = LoggerFactory.getLogger(ImapSynchronizer.class);

    public static final String NAME = "imap_FlaglessSynchronizer";

    @SuppressWarnings("CdiInjectionPointsInspection")
    @Inject
    public ImapFlaglessSynchronizer(ImapHelper imapHelper,
                                    ImapOperations imapOperations,
                                    @SuppressWarnings("SpringJavaInjectionPointsAutowiringInspection") ImapConfig imapConfig,
                                    Authentication authentication,
                                    Persistence persistence,
                                    ImapDao dao,
                                    ImapMessageSyncDao messageSyncDao,
                                    Metadata metadata,
                                    TimeSource timeSource) {

        super(imapHelper, imapOperations, imapConfig, authentication, persistence, dao, messageSyncDao, metadata, timeSource);
    }

    protected void handleNewMessages(List<ImapMessage> checkAnswers,
                                     List<ImapMessage> missedMessages,
                                     ImapFolder cubaFolder,
                                     IMAPFolder imapFolder) throws MessagingException {
        ImapMailBox mailBox = cubaFolder.getMailBox();

        Integer lastMessageNumber = dao.findLastMessageNumber(cubaFolder.getId());
        if (lastMessageNumber != null) {
            lastMessageNumber = lastMessageNumber - missedMessages.size();
        }

        List<IMAPMessage> imapMessages = imapOperations.search(imapFolder, lastMessageNumber, mailBox);
        if (!imapMessages.isEmpty()) {
            for (IMAPMessage imapMessage : imapMessages) {
                log.debug("[{}]insert message with uid {} to db after changing flags on server",
                        cubaFolder, imapFolder.getUID(imapMessage));
                ImapMessage cubaMessage = insertNewMessage(imapMessage, cubaFolder);
                if (cubaMessage != null && cubaMessage.getReferenceId() != null) {
                    checkAnswers.add(cubaMessage);
                }
            }
        }
    }
}
