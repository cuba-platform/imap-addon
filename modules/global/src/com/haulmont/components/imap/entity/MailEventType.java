package com.haulmont.components.imap.entity;

import javax.persistence.Entity;
import javax.persistence.Table;
import javax.persistence.Column;
import com.haulmont.chile.core.annotations.NamePattern;
import com.haulmont.cuba.core.entity.BaseUuidEntity;
import javax.validation.constraints.NotNull;

@Table(name = "MAILCOMPONENT_MAIL_EVENT_TYPE")
@Entity(name = "mailcomponent$MailEventType")
public class MailEventType extends BaseUuidEntity {
    private static final long serialVersionUID = -251458017118383296L;
    @NotNull
    @Column(name = "EVENT_TYPE", nullable = false, unique = true)
    protected String eventType;

    public void setEventType(PredefinedEventType eventType) {
        this.eventType = eventType == null ? null : eventType.getId();
    }

    public PredefinedEventType getEventType() {
        return eventType == null ? null : PredefinedEventType.fromId(eventType);
    }



}