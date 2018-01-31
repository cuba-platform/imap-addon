package com.haulmont.components.imap.dto;

import com.haulmont.chile.core.annotations.MetaClass;
import com.haulmont.chile.core.annotations.MetaProperty;
import com.haulmont.chile.core.annotations.NamePattern;
import com.haulmont.components.imap.entity.MailBox;
import com.haulmont.cuba.core.entity.AbstractNotPersistentEntity;

import java.util.Date;
import java.util.List;

@NamePattern("%s | subject")
@MetaClass(name = "mailcomponent$MailMessageDto")
public class MailMessageDto extends AbstractNotPersistentEntity {

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
    private List<String> flags;
    @MetaProperty
    private Date date;

    @MetaProperty(mandatory = true)
    private String mailBoxHost;
    @MetaProperty(mandatory = true)
    private Integer mailBoxPort;

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

    @Override
    public String toString() {
        return "MailMessageDto{" +
                "from='" + from + '\'' +
                ", toList=" + toList +
                ", ccList=" + ccList +
                ", bccList=" + bccList +
                ", subject='" + subject + '\'' +
                ", body='" + body + '\'' +
                ", flags=" + flags +
                ", mailBoxHost='" + mailBoxHost + '\'' +
                ", mailBoxPort='" + mailBoxPort + '\'' +
                '}';
    }
}
