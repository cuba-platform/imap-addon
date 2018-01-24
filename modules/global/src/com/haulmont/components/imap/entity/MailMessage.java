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

@NamePattern("%s|messageUid")
@Table(name = "MAILCOMPONENT_MAIL_MESSAGE")
@Entity(name = "mailcomponent$MailMessage")
public class MailMessage extends BaseUuidEntity implements SoftDelete, Creatable {
    private static final long serialVersionUID = 1529635256109331665L;

    @Column(name = "CREATE_TS")
    protected Date createTs;

    @Column(name = "SEEN")
    protected Boolean seen;

    @Column(name = "CREATED_BY", length = 50)
    protected String createdBy;

    @Column(name = "DELETE_TS")
    protected Date deleteTs;

    @Column(name = "DELETED_BY", length = 50)
    protected String deletedBy;

    @Column(name = "MESSAGE_UID", nullable = false)
    protected Long messageUid;

    @Column(name = "FOLDER_NAME", nullable = false)
    protected String folderName;

    @OnDeleteInverse(DeletePolicy.CASCADE)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "MAIL_BOX_ID")
    protected MailBox mailBox;

    public void setSeen(Boolean seen) {
        this.seen = seen;
    }

    public Boolean getSeen() {
        return seen;
    }


    @Override
    public Boolean isDeleted() {
        return deleteTs != null;
    }


    public void setCreateTs(Date createTs) {
        this.createTs = createTs;
    }

    public Date getCreateTs() {
        return createTs;
    }

    public void setCreatedBy(String createdBy) {
        this.createdBy = createdBy;
    }

    public String getCreatedBy() {
        return createdBy;
    }

    @Override
    public void setDeleteTs(Date deleteTs) {
        this.deleteTs = deleteTs;
    }

    @Override
    public Date getDeleteTs() {
        return deleteTs;
    }

    @Override
    public void setDeletedBy(String deletedBy) {
        this.deletedBy = deletedBy;
    }

    @Override
    public String getDeletedBy() {
        return deletedBy;
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