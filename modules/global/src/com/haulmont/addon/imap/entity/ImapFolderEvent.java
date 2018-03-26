package com.haulmont.addon.imap.entity;

import com.haulmont.chile.core.annotations.NamePattern;
import com.haulmont.cuba.core.entity.StandardEntity;
import com.haulmont.cuba.core.entity.annotation.OnDeleteInverse;
import com.haulmont.cuba.core.global.DeletePolicy;

import javax.persistence.*;
import javax.validation.constraints.NotNull;

@NamePattern("%s|event")
@Table(name = "IMAPCOMPONENT_IMAP_FOLDER_EVENT")
@Entity(name = "imapcomponent$ImapFolderEvent")
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


    @Column(name = "BEAN_NAME")
    protected String beanName;

    @Column(name = "METHOD_NAME")
    protected String methodName;

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


    public String getBeanName() {
        return beanName;
    }

    public void setBeanName(String beanName) {
        this.beanName = beanName;
    }

    public String getMethodName() {
        return methodName;
    }

    public void setMethodName(String methodName) {
        this.methodName = methodName;
    }
}