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
import com.haulmont.cuba.core.entity.BaseUuidEntity;
import com.haulmont.cuba.core.entity.Creatable;
import com.haulmont.cuba.core.entity.Updatable;
import com.haulmont.cuba.core.entity.Versioned;
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

@NamePattern("%s with %s|message,status")
@Table(name = "IMAP_MESSAGE_SYNC")
@Entity(name = "imap$MessageSync")
public class ImapMessageSync extends BaseUuidEntity implements Versioned, Updatable, Creatable {
    private static final long serialVersionUID = 4840104340467502496L;

    private final static Logger log = LoggerFactory.getLogger(ImapMessage.class);

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @NotNull
    @OnDeleteInverse(DeletePolicy.CASCADE)
    @OneToOne(fetch = FetchType.LAZY, mappedBy = "", optional = false)
    @JoinColumn(name = "MESSAGE_ID", unique = true)
    private ImapMessage message;

    @Lob
    @Column(name = "FLAGS")
    private String flags;

    @OnDeleteInverse(DeletePolicy.CASCADE)
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "FOLDER_ID")
    private ImapFolder folder;

    @NotNull
    @Column(name = "STATUS", nullable = false)
    private String status;

    @OnDeleteInverse(DeletePolicy.CASCADE)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "OLD_FOLDER_ID")
    private ImapFolder oldFolder;

    @Column(name = "UPDATE_TS")
    protected Date updateTs;

    @Column(name = "UPDATED_BY", length = 50)
    protected String updatedBy;

    @Column(name = "CREATE_TS")
    protected Date createTs;

    @Column(name = "CREATED_BY", length = 50)
    protected String createdBy;

    @Version
    @Column(name = "VERSION", nullable = false)
    protected Integer version;

    @Transient
    private List<ImapFlag> internalFlags = Collections.emptyList();

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
                this.flags = OBJECT_MAPPER.writeValueAsString(internalFlags);
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
            this.internalFlags = OBJECT_MAPPER.readValue(this.flags, new TypeReference<List<ImapFlag>>() {});
        } catch (IOException e) {
            throw new RuntimeException("Can't parse flags from string " + flags, e);
        }

        Flags flags = new Flags();
        for (ImapFlag internalFlag : this.internalFlags) {
            flags.add(internalFlag.imapFlags());
        }
        return flags;
    }

    @Override
    public void setUpdateTs(Date updateTs) {
        this.updateTs = updateTs;
    }

    @Override
    public Date getUpdateTs() {
        return updateTs;
    }

    @Override
    public void setUpdatedBy(String updatedBy) {
        this.updatedBy = updatedBy;
    }

    @Override
    public String getUpdatedBy() {
        return updatedBy;
    }

    @Override
    public void setCreateTs(Date createTs) {
        this.createTs = createTs;
    }

    @Override
    public Date getCreateTs() {
        return createTs;
    }

    @Override
    public void setCreatedBy(String createdBy) {
        this.createdBy = createdBy;
    }

    @Override
    public String getCreatedBy() {
        return createdBy;
    }

    @Override
    public void setVersion(Integer version) {
        this.version = version;
    }

    @Override
    public Integer getVersion() {
        return version;
    }


    public void setMessage(ImapMessage message) {
        this.message = message;
    }

    public ImapMessage getMessage() {
        return message;
    }

    public void setStatus(ImapSyncStatus status) {
        this.status = status == null ? null : status.getId();
    }

    public ImapSyncStatus getStatus() {
        return status == null ? null : ImapSyncStatus.fromId(status);
    }

    public ImapFolder getFolder() {
        return folder;
    }

    public void setFolder(ImapFolder folder) {
        this.folder = folder;
    }

    public ImapFolder getOldFolder() {
        return oldFolder;
    }

    public void setOldFolder(ImapFolder oldFolder) {
        this.oldFolder = oldFolder;
    }
}