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

import com.haulmont.chile.core.annotations.Composition;
import com.haulmont.chile.core.annotations.NamePattern;
import com.haulmont.cuba.core.entity.StandardEntity;
import com.haulmont.cuba.core.entity.annotation.OnDelete;
import com.haulmont.cuba.core.entity.annotation.OnDeleteInverse;
import com.haulmont.cuba.core.global.DeletePolicy;

import javax.persistence.*;
import javax.validation.constraints.NotNull;
import java.util.List;

@NamePattern("%s|event")
@Table(name = "IMAP_FOLDER_EVENT")
@Entity(name = "imap$FolderEvent")
public class ImapFolderEvent extends StandardEntity {
    private static final long serialVersionUID = 8743170352789661514L;

    @NotNull
    @OnDeleteInverse(DeletePolicy.CASCADE)
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "FOLDER_ID")
    private ImapFolder folder;

    @Column(name = "EVENT", nullable = false)
    @NotNull
    private String event;

    @OnDelete(DeletePolicy.CASCADE)
    @OneToMany(mappedBy = "event")
    @Composition
    @OrderColumn(name = "HANDLING_ORDER")
    private List<ImapEventHandler> eventHandlers;

    public ImapFolder getFolder() {
        return folder;
    }

    public void setFolder(ImapFolder folder) {
        this.folder = folder;
    }

    public ImapEventType getEvent() {
        return event == null ? null : ImapEventType.fromId(event);
    }

    public void setEvent(ImapEventType event) {
        this.event = event == null ? null : event.getId();
    }

    public List<ImapEventHandler> getEventHandlers() {
        return eventHandlers;
    }

    public void setEventHandlers(List<ImapEventHandler> eventHandlers) {
        this.eventHandlers = eventHandlers;
    }
}