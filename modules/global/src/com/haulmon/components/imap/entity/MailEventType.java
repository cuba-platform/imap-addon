package com.haulmon.components.imap.entity;

import javax.persistence.Entity;
import javax.persistence.Table;
import javax.persistence.Column;
import com.haulmont.cuba.core.entity.StandardEntity;
import com.haulmont.chile.core.annotations.NamePattern;
import com.haulmont.cuba.core.entity.BaseUuidEntity;

@NamePattern("%s|description")
@Table(name = "MAILCOMPONENT_MAIL_EVENT_TYPE")
@Entity(name = "mailcomponent$MailEventType")
public class MailEventType extends BaseUuidEntity {
    private static final long serialVersionUID = -251458017118383296L;

    @Column(name = "NAME", nullable = false, unique = true)
    protected String name;

    @Column(name = "DESCRIPTION", nullable = false)
    protected String description;

    public void setName(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }


}