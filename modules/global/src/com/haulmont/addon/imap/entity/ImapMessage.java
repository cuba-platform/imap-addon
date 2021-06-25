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

package com.haulmont.addon.imap.entity;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.haulmont.addon.imap.api.ImapFlag;
import com.haulmont.chile.core.annotations.NamePattern;
import com.haulmont.cuba.core.entity.StandardEntity;
import com.haulmont.cuba.core.entity.annotation.OnDeleteInverse;
import com.haulmont.cuba.core.global.DeletePolicy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.mail.Flags;
import javax.persistence.*;
import javax.validation.constraints.NotNull;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;

@NamePattern("%s (#%d)|caption, msgNum")
@Table(name = "IMAP_MESSAGE")
@Entity(name = "imap$Message")
public class ImapMessage extends StandardEntity {

    private static final long serialVersionUID = -295396787486211720L;

    private final static Logger log = LoggerFactory.getLogger(ImapMessage.class);

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @OnDeleteInverse(DeletePolicy.CASCADE)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "FOLDER_ID")
    private ImapFolder folder;

    @Lob
    @Column(name = "FLAGS")
    private String flags;

    @NotNull
    @Column(name = "IS_ATL", nullable = false)
    private Boolean attachmentsLoaded = false;

    @NotNull
    @Column(name = "MSG_UID", nullable = false)
    private Long msgUid;

    @NotNull
    @Column(name = "MSG_NUM", nullable = false)
    private Integer msgNum;

    @Column(name = "THREAD_ID")
    private Long threadId;

    @Lob
    @Column(name = "REFERENCE_ID")
    private String referenceId;

    @Lob
    @Column(name = "MESSAGE_ID")
    private String messageId;

    @Lob
    @NotNull
    @Column(name = "CAPTION", nullable = false)
    private String caption;

    @Temporal(TemporalType.TIMESTAMP)
    @Column(name = "RECEIVED_DATE")
    protected Date receivedDate;

    @Transient
    private List<ImapFlag> internalFlags = Collections.emptyList();

    public Date getReceivedDate() {
        return receivedDate;
    }

    public void setReceivedDate(Date receivedDate) {
        this.receivedDate = receivedDate;
    }

    public String getFlags() {
        return flags;
    }

    void setFlags(String flags) {
        this.flags = flags;
    }

    public void setImapFlags(Flags flags) {
        Flags.Flag[] systemFlags = flags.getSystemFlags();
        String[] userFlags = flags.getUserFlags();
        List<ImapFlag> internalFlags = new ArrayList<>(systemFlags.length + userFlags.length);
        for (Flags.Flag systemFlag : systemFlags) {
            internalFlags.add(new ImapFlag(ImapFlag.SystemFlag.valueOf(systemFlag)));
        }
        for (String userFlag : userFlags) {
            internalFlags.add(new ImapFlag(userFlag));
        }
        try {
            if (!internalFlags.equals(this.internalFlags)) {
                log.debug("Convert imap flags {} to raw string", internalFlags);
                setFlags(OBJECT_MAPPER.writeValueAsString(internalFlags));
                this.internalFlags = internalFlags;
            }
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Can't convert flags " + internalFlags, e);
        }
    }

    public Flags getImapFlags() {
        try {
            log.debug("Parse imap flags from raw string {}", flags);
            if (flags == null) {
                return new Flags();
            }
            this.internalFlags = OBJECT_MAPPER.readValue(this.flags, new TypeReference<List<ImapFlag>>() {
            });
        } catch (IOException e) {
            throw new RuntimeException("Can't parse flags from string " + flags, e);
        }

        Flags flags = new Flags();
        for (ImapFlag internalFlag : this.internalFlags) {
            flags.add(internalFlag.imapFlags());
        }
        return flags;
    }

    public void setMessageId(String messageId) {
        this.messageId = messageId;
    }

    public String getMessageId() {
        return messageId;
    }

    public void setAttachmentsLoaded(Boolean attachmentsLoaded) {
        this.attachmentsLoaded = attachmentsLoaded;
    }

    public Boolean getAttachmentsLoaded() {
        return attachmentsLoaded;
    }

    public ImapFolder getFolder() {
        return folder;
    }

    public void setFolder(ImapFolder folder) {
        this.folder = folder;
    }

    public String getReferenceId() {
        return referenceId;
    }

    public void setReferenceId(String referenceId) {
        this.referenceId = referenceId;
    }

    public void setThreadId(Long threadId) {
        this.threadId = threadId;
    }

    public Long getThreadId() {
        return threadId;
    }

    public void setCaption(String caption) {
        this.caption = caption;
    }

    public String getCaption() {
        return caption;
    }

    public void setMsgUid(Long msgUid) {
        this.msgUid = msgUid;
    }

    public Long getMsgUid() {
        return msgUid;
    }

    public Integer getMsgNum() {
        return msgNum;
    }

    public void setMsgNum(Integer msgNum) {
        this.msgNum = msgNum;
    }
}