package com.haulmont.components.imap.entity.demo;

import javax.persistence.Entity;
import javax.persistence.Table;

import com.haulmont.components.imap.dto.MailMessageDto;
import com.haulmont.components.imap.entity.MailBox;
import com.haulmont.cuba.core.entity.annotation.OnDeleteInverse;
import com.haulmont.cuba.core.global.DeletePolicy;
import java.util.Date;
import java.util.function.Supplier;
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

    @Column(name = "FROM_")
    protected String from;

    @Column(name = "TO_LIST")
    protected String toList;

    @Column(name = "CC_LIST")
    protected String ccList;

    @Column(name = "BCC_LIST")
    protected String bccList;

    @Column(name = "SUBJECT")
    protected String subject;

    @Column(name = "FLAGS_LIST")
    protected String flagsList;

    @Temporal(TemporalType.TIMESTAMP)
    @Column(name = "DATE_")
    protected Date date;

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

    public void setDate(Date date) {
        this.date = date;
    }

    public Date getDate() {
        return date;
    }


    public void setFlagsList(String flagsList) {
        this.flagsList = flagsList;
    }

    public String getFlagsList() {
        return flagsList;
    }


    public void setFrom(String from) {
        this.from = from;
    }

    public String getFrom() {
        return from;
    }

    public void setToList(String toList) {
        this.toList = toList;
    }

    public String getToList() {
        return toList;
    }

    public void setCcList(String ccList) {
        this.ccList = ccList;
    }

    public String getCcList() {
        return ccList;
    }

    public void setBccList(String bccList) {
        this.bccList = bccList;
    }

    public String getBccList() {
        return bccList;
    }

    public void setSubject(String subject) {
        this.subject = subject;
    }

    public String getSubject() {
        return subject;
    }


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

    public static void fillMessage(MailMessage mailMessage, MailMessageDto dto, Supplier<MailBox> mailBoxSupplier) {
        mailMessage.setMessageUid(dto.getUid());
        mailMessage.setMailBox(mailBoxSupplier.get());
        mailMessage.setFolderName(dto.getFolderName());
        mailMessage.setDate(dto.getDate());
        mailMessage.setSubject(dto.getSubject());
        mailMessage.setFrom(dto.getFrom());
        mailMessage.setToList(dto.getToList().toString());
        mailMessage.setBccList(dto.getBccList().toString());
        mailMessage.setCcList(dto.getCcList().toString());
        mailMessage.setFlagsList(dto.getFlags().toString());
    }
}