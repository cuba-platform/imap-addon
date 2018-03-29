package com.haulmont.addon.imap.dto;

import com.haulmont.chile.core.annotations.MetaClass;
import com.haulmont.chile.core.annotations.MetaProperty;
import com.haulmont.chile.core.annotations.NamePattern;
import com.haulmont.cuba.core.entity.AbstractNotPersistentEntity;
import org.apache.commons.lang.builder.ToStringBuilder;

import java.util.Date;
import java.util.List;
import java.util.UUID;

@NamePattern("%s | subject")
@MetaClass(name = "imapcomponent$ImapMessageDto")
public class ImapMessageDto extends AbstractNotPersistentEntity {

    @MetaProperty(mandatory = true)
    private Long uid;
    @MetaProperty(mandatory = true)
    private String from;
    @MetaProperty(mandatory = true)
    private List<String> toList;
    @MetaProperty
    private List<String> ccList;
    @MetaProperty
    private List<String> bccList;
    @MetaProperty
    private String subject;
    @MetaProperty
    private String body;
    @MetaProperty
    private Boolean html = false;
    @MetaProperty
    private List<String> flags;
    @MetaProperty
    private Date date;

    @MetaProperty(mandatory = true)
    private String mailBoxHost;
    @MetaProperty(mandatory = true)
    private Integer mailBoxPort;

    private UUID mailBoxId;
    private String folderName;

    public Long getUid() {
        return uid;
    }

    public void setUid(Long uid) {
        this.uid = uid;
    }

    public String getFrom() {
        return from;
    }

    public void setFrom(String from) {
        this.from = from;
    }

    public List<String> getToList() {
        return toList;
    }

    public void setToList(List<String> toList) {
        this.toList = toList;
    }

    public List<String> getCcList() {
        return ccList;
    }

    public void setCcList(List<String> ccList) {
        this.ccList = ccList;
    }

    public List<String> getBccList() {
        return bccList;
    }

    public void setBccList(List<String> bccList) {
        this.bccList = bccList;
    }

    public String getSubject() {
        return subject;
    }

    public void setSubject(String subject) {
        this.subject = subject;
    }

    public String getBody() {
        return body;
    }

    public void setBody(String body) {
        this.body = body;
    }

    public Boolean getHtml() {
        return html;
    }

    public void setHtml(Boolean html) {
        this.html = html;
    }

    public List<String> getFlags() {
        return flags;
    }

    public void setFlags(List<String> flags) {
        this.flags = flags;
    }

    public Date getDate() {
        return date;
    }

    public void setDate(Date sendDate) {
        this.date = sendDate;
    }

    public String getMailBoxHost() {
        return mailBoxHost;
    }

    public void setMailBoxHost(String mailBoxHost) {
        this.mailBoxHost = mailBoxHost;
    }

    public Integer getMailBoxPort() {
        return mailBoxPort;
    }

    public void setMailBoxPort(Integer mailBoxPort) {
        this.mailBoxPort = mailBoxPort;
    }

    public UUID getMailBoxId() {
        return mailBoxId;
    }

    public void setMailBoxId(UUID mailBoxId) {
        this.mailBoxId = mailBoxId;
    }

    public String getFolderName() {
        return folderName;
    }

    public void setFolderName(String folderName) {
        this.folderName = folderName;
    }

    @Override
    public String toString() {
        return new ToStringBuilder(this).
                append("from", from).
                append("toList", toList).
                append("ccList", ccList).
                append("bccList", bccList).
                append("subject", subject).
                append("body", body).
                append("flags", flags).
                append("mailBoxHost", mailBoxHost).
                append("mailBoxPort", mailBoxPort).
                toString();
    }
}
