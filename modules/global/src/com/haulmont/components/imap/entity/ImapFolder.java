package com.haulmont.components.imap.entity;

import javax.persistence.*;

import com.haulmont.chile.core.annotations.Composition;
import com.haulmont.cuba.core.global.DeletePolicy;

import com.haulmont.cuba.core.entity.StandardEntity;
import com.haulmont.chile.core.annotations.NamePattern;
import com.haulmont.cuba.core.entity.annotation.OnDeleteInverse;

import java.util.Collections;
import java.util.List;
import com.haulmont.cuba.core.entity.annotation.OnDelete;

@NamePattern("%s|name")
@Table(name = "MAILCOMPONENT_IMAP_FOLDER")
@Entity(name = "mailcomponent$ImapFolder")
public class ImapFolder extends StandardEntity {
    private static final long serialVersionUID = -5878471272097557535L;

    @Column(name = "NAME", nullable = false)
    protected String name;

    @OnDelete(DeletePolicy.CASCADE)
    @OneToMany(mappedBy = "folder")
    @Composition
    protected List<ImapFolderEvent> events;

    @OnDeleteInverse(DeletePolicy.CASCADE)
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "MAIL_BOX_ID")
    protected ImapMailBox mailBox;

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

}