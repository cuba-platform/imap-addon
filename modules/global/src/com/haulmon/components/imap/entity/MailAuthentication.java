package com.haulmon.components.imap.entity;

import javax.persistence.Entity;
import javax.persistence.Table;
import com.haulmont.cuba.core.entity.StandardEntity;
import javax.persistence.MappedSuperclass;

@MappedSuperclass
public class MailAuthentication extends StandardEntity {
    private static final long serialVersionUID = 6348499927175437459L;

}