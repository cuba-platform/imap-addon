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

package com.haulmont.addon.imap.core;

import com.haulmont.addon.imap.core.ext.ThreadExtension;
import com.haulmont.addon.imap.dto.ImapFolderDto;
import com.haulmont.addon.imap.entity.ImapFolder;
import com.haulmont.addon.imap.entity.ImapMailBox;
import com.haulmont.addon.imap.entity.ImapMessage;
import com.haulmont.cuba.core.global.Metadata;
import com.haulmont.cuba.core.global.TimeSource;
import com.sun.mail.imap.IMAPFolder;
import com.sun.mail.imap.IMAPMessage;
import com.sun.mail.imap.IMAPStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.mail.*;
import javax.mail.internet.MimeUtility;
import javax.mail.search.ComparisonTerm;
import javax.mail.search.IntegerComparisonTerm;
import javax.mail.search.SearchTerm;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

@Component("imap_ImapOperations")
public class ImapOperations {
    private final static Logger log = LoggerFactory.getLogger(ImapOperations.class);

    private static final String REFERENCES_HEADER = "References";
    private static final String IN_REPLY_TO_HEADER = "In-Reply-To";
    private static final String SUBJECT_HEADER = "Subject";
    public static final String MESSAGE_ID_HEADER = "Message-ID";

    private final ImapHelper imapHelper;
    private final Metadata metadata;
    private final TimeSource timeSource;

    @Autowired
    public ImapOperations(ImapHelper imapHelper, Metadata metadata, TimeSource timeSource) {
        this.imapHelper = imapHelper;
        this.metadata = metadata;
        this.timeSource = timeSource;
    }

    public List<ImapFolderDto> fetchFolders(IMAPStore store) throws MessagingException {
        List<ImapFolderDto> result = new ArrayList<>();
        Folder defaultFolder = store.getDefaultFolder();

        IMAPFolder[] rootFolders = (IMAPFolder[]) defaultFolder.list();
        for (IMAPFolder folder : rootFolders) {
            result.add(map(folder));
        }

        return result;
    }

    public boolean supportsCustomFlag(IMAPStore store) throws MessagingException {
        Folder defaultFolder = store.getDefaultFolder();

        IMAPFolder[] rootFolders = (IMAPFolder[]) defaultFolder.list();
        if (rootFolders != null && rootFolders.length > 0) {
            IMAPFolder rootFolder = rootFolders[0];
            if (!rootFolder.isOpen()) {
                rootFolder.open(Folder.READ_WRITE);
            }

            return rootFolder.getPermanentFlags().contains(Flags.Flag.USER);
        }

        return false;
    }

    private ImapFolderDto map(IMAPFolder folder) throws MessagingException {
        List<ImapFolderDto> subFolders = new ArrayList<>();

        if (ImapHelper.canHoldFolders(folder)) {
            for (Folder childFolder : folder.list()) {
                subFolders.add(map((IMAPFolder) childFolder));
            }
        }
        ImapFolderDto result = metadata.create(ImapFolderDto.class);
        result.setName(folder.getName());
        result.setFullName(folder.getFullName());
        result.setCanHoldMessages(ImapHelper.canHoldMessages(folder));
        result.setChildren(subFolders);
        for (ImapFolderDto f : result.getChildren()) {
            f.setParent(result);
        }
        return result;
    }

    public List<IMAPMessage> search(IMAPFolder folder, SearchTerm searchTerm, ImapMailBox mailBox) throws MessagingException {
        log.debug("search messages in {} with {}", folder.getFullName(), searchTerm);

        Message[] messages = folder.search(searchTerm);
        return fetch(folder, mailBox, messages);
    }

    public List<IMAPMessage> search(IMAPFolder folder, Integer lastMessageNumber, ImapMailBox mailBox) throws MessagingException {
        log.debug("search messages in {} with number greater {}", folder.getFullName(), lastMessageNumber);

        Message[] messages = lastMessageNumber != null
                ? folder.search(newer(lastMessageNumber))
                : folder.getMessages();

        return fetch(folder, mailBox, messages);
    }

    private SearchTerm newer(int lastMessageNumber) {
        return new IntegerComparisonTerm(ComparisonTerm.GT, lastMessageNumber) {
            @Override
            public boolean match(Message msg) {
                int msgno;

                try {
                    msgno = msg.getMessageNumber();
                } catch (Exception e) {
                    return false;
                }

                return super.match(msgno);
            }
        };
    }

    public List<IMAPMessage> searchMessageIds(IMAPFolder folder, SearchTerm searchTerm) throws MessagingException {
        log.debug("search messages in {} with {}", folder.getFullName(), searchTerm);

        Message[] messages = folder.search(searchTerm);
        FetchProfile fetchProfile = new FetchProfile();
        fetchProfile.add(MESSAGE_ID_HEADER);
        return fetch(folder, fetchProfile, messages);
    }

    public ImapMessage map(ImapMessage cubaMessage, IMAPMessage msg, ImapFolder cubaFolder) throws MessagingException {
        long uid = ((IMAPFolder) msg.getFolder()).getUID(msg);
        Flags flags = new Flags(msg.getFlags());
        cubaMessage.setMsgUid(uid);
        cubaMessage.setFolder(cubaFolder);
        cubaMessage.setUpdateTs(timeSource.currentTimestamp());
        cubaMessage.setImapFlags(flags);
        cubaMessage.setReceivedDate(msg.getReceivedDate());
        cubaMessage.setCaption(getSubject(msg));
        cubaMessage.setMessageId(msg.getHeader(ImapOperations.MESSAGE_ID_HEADER, null));
        cubaMessage.setReferenceId(getRefId(msg));
        cubaMessage.setThreadId(getThreadId(msg, cubaFolder.getMailBox()));
        cubaMessage.setMsgNum(msg.getMessageNumber());

        return cubaMessage;
    }

    private String getRefId(IMAPMessage message) throws MessagingException {
        String refHeader = message.getHeader(REFERENCES_HEADER, null);
        if (refHeader == null) {
            refHeader = message.getHeader(IN_REPLY_TO_HEADER, null);
        } else {
            refHeader = refHeader.split("\\s+")[0];
        }
        if (refHeader != null && refHeader.length() > 0) {
            return refHeader;
        }

        return null;
    }

    private Long getThreadId(IMAPMessage message, ImapMailBox mailBox) throws MessagingException {
        if (!imapHelper.supportsThreading(mailBox)) {
            return null;
        }
        Object threadItem = message.getItem(ThreadExtension.FETCH_ITEM);
        return threadItem instanceof ThreadExtension.X_GM_THRID ? ((ThreadExtension.X_GM_THRID) threadItem).x_gm_thrid : null;
    }

    private String getSubject(IMAPMessage message) throws MessagingException {
        String subject = message.getHeader(SUBJECT_HEADER, null);
        if (subject != null && subject.length() > 0) {
            return decode(subject);
        } else {
            return "(No Subject)";
        }
    }

    private List<IMAPMessage> fetch(IMAPFolder folder, ImapMailBox mailBox, Message[] messages) throws MessagingException {
        return fetch(folder, headerProfile(mailBox), messages);
    }

    private List<IMAPMessage> fetch(IMAPFolder folder, FetchProfile fetchProfile, Message[] messages) throws MessagingException {
        Message[] nonNullMessages = Arrays.stream(messages).filter(Objects::nonNull).toArray(Message[]::new);
        folder.fetch(nonNullMessages, fetchProfile);
        List<IMAPMessage> result = new ArrayList<>(nonNullMessages.length);
        for (Message message : nonNullMessages) {
            result.add((IMAPMessage) message);
        }
        return result;
    }

    private FetchProfile headerProfile(ImapMailBox mailBox) {
        FetchProfile profile = new FetchProfile();
        profile.add(FetchProfile.Item.FLAGS);
        profile.add(UIDFolder.FetchProfileItem.UID);
        profile.add(REFERENCES_HEADER);
        profile.add(IN_REPLY_TO_HEADER);
        profile.add(SUBJECT_HEADER);
        profile.add(MESSAGE_ID_HEADER);

        if (imapHelper.supportsThreading(mailBox)) {
            profile.add(ThreadExtension.FetchProfileItem.X_GM_THRID);
        }

        return profile;
    }

    private String decode(String val) {
        try {
            return MimeUtility.decodeText(MimeUtility.unfold(val));
        } catch (UnsupportedEncodingException ex) {
            return val;
        }
    }
}
