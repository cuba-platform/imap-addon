package com.haulmont.components.imap.entity;

import javax.persistence.Entity;
import javax.persistence.Table;
import com.haulmont.cuba.core.entity.annotation.OnDeleteInverse;
import com.haulmont.cuba.core.global.DeletePolicy;
import javax.persistence.Column;
import javax.persistence.FetchType;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.validation.constraints.NotNull;
import com.haulmont.cuba.core.entity.StandardEntity;
import com.haulmont.cuba.core.entity.BaseUuidEntity;
import com.haulmont.chile.core.annotations.NamePattern;
import java.util.Date;
import javax.persistence.Temporal;
import javax.persistence.TemporalType;

@NamePattern("/%s from |msgUid")
@Table(name = "MAILCOMPONENT_IMAP_MESSAGE_REF")
@Entity(name = "mailcomponent$ImapMessageRef")
public class ImapMessageRef extends BaseUuidEntity {
    private static final long serialVersionUID = -295396787486211720L;

    @OnDeleteInverse(DeletePolicy.CASCADE)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "FOLDER_ID")
    protected MailFolder folder;

    @NotNull
    @Column(name = "MSG_UID", nullable = false)
    protected Long msgUid;

    @Column(name = "THREAD_ID")
    protected Long threadId;

    @Column(name = "REFERENCE_ID")
    protected String referenceId;

    @NotNull
    @Column(name = "CAPTION", nullable = false)
    protected String caption;

    @NotNull
    @Column(name = "IS_DELETED", nullable = false)
    protected Boolean deleted = false;

    @NotNull
    @Column(name = "IS_FLAGGED", nullable = false)
    protected Boolean flagged = false;

    @NotNull
    @Column(name = "IS_ANSWERED", nullable = false)
    protected Boolean answered = false;

    @NotNull
    @Column(name = "IS_SEEN", nullable = false)
    protected Boolean seen = false;

    @NotNull
    @Temporal(TemporalType.TIME)
    @Column(name = "UPDATED_TS", nullable = false)
    protected Date updatedTs;

    public String getReferenceId() {
        return referenceId;
    }

    public void setReferenceId(String referenceId) {
        this.referenceId = referenceId;
    }


    public void setThreadId(Long threadId) {
        this.threadId = threadId;
    }

    public Long getThreadId() {
        return threadId;
    }

    public void setCaption(String caption) {
        this.caption = caption;
    }

    public String getCaption() {
        return caption;
    }


    public void setFolder(MailFolder folder) {
        this.folder = folder;
    }

    public MailFolder getFolder() {
        return folder;
    }


    public void setUpdatedTs(Date updatedTs) {
        this.updatedTs = updatedTs;
    }

    public Date getUpdatedTs() {
        return updatedTs;
    }


    public void setMsgUid(Long msgUid) {
        this.msgUid = msgUid;
    }

    public Long getMsgUid() {
        return msgUid;
    }

    public void setDeleted(Boolean deleted) {
        this.deleted = deleted;
    }

    public Boolean getDeleted() {
        return deleted;
    }

    public void setFlagged(Boolean flagged) {
        this.flagged = flagged;
    }

    public Boolean getFlagged() {
        return flagged;
    }

    public void setAnswered(Boolean answered) {
        this.answered = answered;
    }

    public Boolean getAnswered() {
        return answered;
    }

    public void setSeen(Boolean seen) {
        this.seen = seen;
    }

    public Boolean getSeen() {
        return seen;
    }


}