package com.haulmont.components.imap.dto;

import com.haulmont.chile.core.annotations.MetaClass;
import com.haulmont.chile.core.annotations.NamePattern;
import com.haulmont.cuba.core.entity.AbstractNotPersistentEntity;

import java.util.List;

@NamePattern("%s | subject")
@MetaClass(name = "mailcomponent$MailMessageDto")
public class MailMessageDto extends AbstractNotPersistentEntity {

    private String from;
    private List<String> toList;
    private List<String> ccList;
    private List<String> bccList;
    private String subject;
    private String body;
    private List<String> flags;

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
                '}';
    }
}
