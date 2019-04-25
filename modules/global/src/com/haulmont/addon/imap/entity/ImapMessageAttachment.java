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

import javax.persistence.Entity;
import javax.persistence.Table;
import com.haulmont.cuba.core.entity.StandardEntity;
import javax.persistence.Column;
import javax.persistence.FetchType;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.validation.constraints.NotNull;
import com.haulmont.chile.core.annotations.NamePattern;
import java.util.Date;
import javax.persistence.Temporal;
import javax.persistence.TemporalType;

@NamePattern("#%s: %s|orderNumber,name")
@Table(name = "IMAP_MESSAGE_ATTACHMENT")
@Entity(name = "imap$MessageAttachment")
public class ImapMessageAttachment extends StandardEntity {
    private static final long serialVersionUID = -1046407519479636529L;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "IMAP_MESSAGE_ID")
    private ImapMessage imapMessage;

    @Temporal(TemporalType.TIME)
    @NotNull
    @Column(name = "CREATED_TS", nullable = false)
    private Date createdTs;

    @NotNull
    @Column(name = "ORDER_NUMBER", nullable = false)
    private Integer orderNumber;

    @NotNull
    @Column(name = "NAME", nullable = false)
    private String name;

    @NotNull
    @Column(name = "FILE_SIZE", nullable = false)
    private Long fileSize;

    public void setImapMessage(ImapMessage imapMessage) {
        this.imapMessage = imapMessage;
    }

    public ImapMessage getImapMessage() {
        return imapMessage;
    }

    public void setCreatedTs(Date createdTs) {
        this.createdTs = createdTs;
    }

    public Date getCreatedTs() {
        return createdTs;
    }

    public void setOrderNumber(Integer orderNumber) {
        this.orderNumber = orderNumber;
    }

    public Integer getOrderNumber() {
        return orderNumber;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }

    public void setFileSize(Long fileSize) {
        this.fileSize = fileSize;
    }

    public Long getFileSize() {
        return fileSize;
    }

}