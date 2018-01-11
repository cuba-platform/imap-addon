package com.haulmon.components.imap.entity;

import javax.persistence.*;

import com.haulmont.cuba.core.global.DeletePolicy;

import com.haulmont.cuba.core.entity.StandardEntity;
import com.haulmont.chile.core.annotations.NamePattern;
import com.haulmont.cuba.core.entity.annotation.OnDeleteInverse;

import java.util.Collections;
import java.util.List;

@NamePattern("%s|name")
@Table(name = "MAILCOMPONENT_MAIL_FOLDER")
@Entity(name = "mailcomponent$MailFolder")
public class MailFolder extends StandardEntity {
    private static final long serialVersionUID = -5878471272097557535L;

    @Column(name = "NAME", nullable = false)
    protected String name;

    @JoinTable(name = "MAILCOMPONENT_MAIL_FOLDER_MAIL_EVENT_TYPE_LINK",
        joinColumns = @JoinColumn(name = "MAIL_FOLDER_ID"),
        inverseJoinColumns = @JoinColumn(name = "MAIL_EVENT_TYPE_ID"))
    @ManyToMany(cascade = { CascadeType.MERGE, CascadeType.PERSIST })
    protected List<MailEventType> events;

    @OnDeleteInverse(DeletePolicy.CASCADE)
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "MAIL_BOX_ID")
    protected MailBox mailBox;

    public void setEvents(List<MailEventType> events) {
        this.events = events;
    }

    public List<MailEventType> getEvents() {
        return events;
    }

    public void setMailBox(MailBox mailBox) {
        this.mailBox = mailBox;
    }

    public MailBox getMailBox() {
        return mailBox;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }

    public boolean hasEvent(String eventType) {
        List<MailEventType> safeEvents = events != null ? events : Collections.<MailEventType>emptyList();

        return safeEvents.stream().anyMatch(e -> e.getName().equals(eventType));
    }

}