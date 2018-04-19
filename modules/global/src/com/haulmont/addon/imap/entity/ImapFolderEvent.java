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
    protected ImapFolder folder;

    @Column(name = "EVENT", nullable = false)
    @NotNull
    protected String event;

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