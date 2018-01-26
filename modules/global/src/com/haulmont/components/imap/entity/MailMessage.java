package com.haulmont.components.imap.entity;

import javax.persistence.Entity;
import javax.persistence.Table;
import com.haulmont.cuba.core.entity.annotation.OnDeleteInverse;
import com.haulmont.cuba.core.global.DeletePolicy;
import java.util.Date;
import javax.persistence.Column;
import javax.persistence.FetchType;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import com.haulmont.cuba.core.entity.BaseUuidEntity;
import com.haulmont.cuba.core.entity.SoftDelete;
import com.haulmont.cuba.core.entity.Creatable;
import com.haulmont.chile.core.annotations.NamePattern;
import com.haulmont.cuba.core.entity.StandardEntity;
import javax.persistence.Temporal;
import javax.persistence.TemporalType;

@NamePattern("%s|messageUid")
@Table(name = "MAILCOMPONENT_MAIL_MESSAGE")
@Entity(name = "mailcomponent$MailMessage")
public class MailMessage extends StandardEntity {
    private static final long serialVersionUID = 1529635256109331665L;


    @Column(name = "SEEN")
    protected Boolean seen;




    @Temporal(TemporalType.TIMESTAMP)
    @Column(name = "SEEN_TIME")
    protected Date seenTime;

    @Column(name = "MESSAGE_UID", nullable = false)
    protected Long messageUid;

    @Column(name = "FOLDER_NAME", nullable = false)
    protected String folderName;

    @OnDeleteInverse(DeletePolicy.CASCADE)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "MAIL_BOX_ID")
    protected MailBox mailBox;

    public void setSeenTime(Date seenTime) {
        this.seenTime = seenTime;
    }

    public Date getSeenTime() {
        return seenTime;
    }


    public void setSeen(Boolean seen) {
        this.seen = seen;
    }

    public Boolean getSeen() {
        return seen;
    }












    public void setMessageUid(Long messageUid) {
        this.messageUid = messageUid;
    }

    public Long getMessageUid() {
        return messageUid;
    }

    public void setFolderName(String folderName) {
        this.folderName = folderName;
    }

    public String getFolderName() {
        return folderName;
    }

    public void setMailBox(MailBox mailBox) {
        this.mailBox = mailBox;
    }

    public MailBox getMailBox() {
        return mailBox;
    }


}