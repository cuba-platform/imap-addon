package com.haulmont.addon.imap.entity;

import com.haulmont.chile.core.annotations.Composition;
import com.haulmont.chile.core.annotations.MetaProperty;
import com.haulmont.chile.core.annotations.NamePattern;
import com.haulmont.cuba.core.entity.StandardEntity;
import com.haulmont.cuba.core.entity.annotation.OnDelete;
import com.haulmont.cuba.core.entity.annotation.OnDeleteInverse;
import com.haulmont.cuba.core.global.DeletePolicy;

import javax.persistence.*;
import java.util.Collections;
import java.util.List;
import com.haulmont.cuba.core.entity.annotation.Listeners;

@Listeners("imap_FolderSelectionListener")
@NamePattern("%s|name")
@Table(name = "IMAP_FOLDER")
@Entity(name = "imap$Folder")
public class ImapFolder extends StandardEntity {
    private static final long serialVersionUID = -5878471272097557535L;

    @Column(name = "NAME", nullable = false)
    protected String name;

    @OnDelete(DeletePolicy.CASCADE)
    @OneToMany(mappedBy = "folder")
    @Composition
    private List<ImapFolderEvent> events;

    @OnDeleteInverse(DeletePolicy.CASCADE)
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "MAIL_BOX_ID")
    private ImapMailBox mailBox;

    @Column(name = "SELECTED", nullable = false)
    private Boolean selected = false;

    @Column(name = "SELECTABLE", nullable = false)
    private Boolean selectable = false;

    @Column(name = "DISABLED")
    private Boolean disabled;

    @OnDeleteInverse(DeletePolicy.CASCADE)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "PARENT_FOLDER_ID")
    private ImapFolder parent;

    @Transient
    @MetaProperty
    private Boolean unregistered = false;

    public ImapMailBox getMailBox() {
        return mailBox;
    }

    public void setMailBox(ImapMailBox mailBox) {
        this.mailBox = mailBox;
    }


    public List<ImapFolderEvent> getEvents() {
        return events;
    }

    public void setEvents(List<ImapFolderEvent> events) {
        this.events = events;
    }


    public void setName(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }

    public ImapFolderEvent getEvent(ImapEventType eventType) {
        List<ImapFolderEvent> safeEvents = events != null ? events : Collections.emptyList();

        return safeEvents.stream().filter(e -> e.getEvent() == eventType).findFirst().orElse(null);
    }

    public boolean hasEvent(ImapEventType eventType) {
        return getEvent(eventType) != null;
    }


    public Boolean getSelected() {
        return selected;
    }

    public void setSelected(Boolean selected) {
        this.selected = selected;
    }

    public Boolean getSelectable() {
        return selectable;
    }

    public void setSelectable(Boolean selectable) {
        this.selectable = selectable;
    }

    public Boolean getDisabled() {
        return disabled;
    }

    public void setDisabled(Boolean disabled) {
        this.disabled = disabled;
    }

    public ImapFolder getParent() {
        return parent;
    }

    public void setParent(ImapFolder parent) {
        this.parent = parent;
    }

    public Boolean getUnregistered() {
        return unregistered;
    }

    public void setUnregistered(Boolean unregistered) {
        this.unregistered = unregistered;
    }

    @MetaProperty
    public String getEventsInfo() {
        int i = 0;
        StringBuilder builder = new StringBuilder();
        for (ImapFolderEvent event : events) {
            ImapEventType eventType = event.getEvent();
            if (i > 0) {
                builder.append(", ");
            }
            builder.append(eventType);
            i++;
        }

        return builder.toString();
    }

}