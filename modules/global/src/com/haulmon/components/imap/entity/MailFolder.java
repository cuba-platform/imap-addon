package com.haulmon.components.imap.entity;

import javax.persistence.*;

import com.haulmont.cuba.core.global.DeletePolicy;

import com.haulmont.cuba.core.entity.StandardEntity;
import com.haulmont.chile.core.annotations.NamePattern;
import com.haulmont.cuba.core.entity.annotation.OnDeleteInverse;

@NamePattern("%s|name")
@Table(name = "MAILCOMPONENT_MAIL_FOLDER")
@Entity(name = "mailcomponent$MailFolder")
public class MailFolder extends StandardEntity {
    private static final long serialVersionUID = -5878471272097557535L;

    @Column(name = "NAME", nullable = false)
    protected String name;

    @Column(name = "LISTEN_NEW_EMAIL", nullable = false)
    protected Boolean listenNewEmail = true;

    @Column(name = "LISTEN_EMAIL_SEEN", nullable = false)
    protected Boolean listenEmailSeen = false;

    @Column(name = "LISTEN_NEW_ANSWER", nullable = false)
    protected Boolean listenNewAnswer = false;

    @Column(name = "LISTEN_EMAIL_MOVE", nullable = false)
    protected Boolean listenEmailMove = false;

    @Column(name = "LISTEN_FLAGS_UPDATE", nullable = false)
    protected Boolean listenFlagsUpdate = false;

    @Column(name = "LISTEN_EMAIL_REMOVE", nullable = false)
    protected Boolean listenEmailRemove = false;

    @Column(name = "LISTEN_NEW_THREAD", nullable = false)
    protected Boolean listenNewThread = false;

    @OnDeleteInverse(DeletePolicy.CASCADE)
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "MAIL_BOX_ID")
    protected MailBox mailBox;

    public void setListenNewEmail(Boolean listenNewEmail) {
        this.listenNewEmail = listenNewEmail;
    }

    public Boolean getListenNewEmail() {
        return listenNewEmail;
    }

    public void setListenEmailSeen(Boolean listenEmailSeen) {
        this.listenEmailSeen = listenEmailSeen;
    }

    public Boolean getListenEmailSeen() {
        return listenEmailSeen;
    }

    public void setListenNewAnswer(Boolean listenNewAnswer) {
        this.listenNewAnswer = listenNewAnswer;
    }

    public Boolean getListenNewAnswer() {
        return listenNewAnswer;
    }

    public void setListenEmailMove(Boolean listenEmailMove) {
        this.listenEmailMove = listenEmailMove;
    }

    public Boolean getListenEmailMove() {
        return listenEmailMove;
    }

    public void setListenFlagsUpdate(Boolean listenFlagsUpdate) {
        this.listenFlagsUpdate = listenFlagsUpdate;
    }

    public Boolean getListenFlagsUpdate() {
        return listenFlagsUpdate;
    }

    public void setListenEmailRemove(Boolean listenEmailRemove) {
        this.listenEmailRemove = listenEmailRemove;
    }

    public Boolean getListenEmailRemove() {
        return listenEmailRemove;
    }

    public void setListenNewThread(Boolean listenNewThread) {
        this.listenNewThread = listenNewThread;
    }

    public Boolean getListenNewThread() {
        return listenNewThread;
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


}