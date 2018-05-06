package com.haulmont.addon.imap.dto;

import com.haulmont.addon.imap.entity.ImapMailBox;
import com.haulmont.chile.core.annotations.MetaClass;
import com.haulmont.chile.core.annotations.MetaProperty;
import com.haulmont.chile.core.annotations.NamePattern;
import com.haulmont.cuba.core.entity.BaseUuidEntity;
import org.apache.commons.lang.builder.ToStringBuilder;

import java.util.Date;
import java.util.List;
import java.util.UUID;

/**
 * encapsulates IMAP message details:
 * <ul>
 *     <li>
 *     Folder name
 *     </li>
 *     <li>
 *     UID
 *     </li>
 *     <li>
 *     Sender
 *     </li>
 *     <li>
 *     Recipient lists (to, cc, bcc)
 *     </li>
 *     <li>
 *     Subject
 *     </li>
 *     <li>
 *     Body content
 *     </li>
 *     <li>
 *     Receive date
 *     </li>
 *     <li>
 *     IMAP metadata flags
 *     </li>
 * </ul>
 */
@NamePattern("%s | subject")
@MetaClass(name = "imap$MessageDto")
public class ImapMessageDto extends BaseUuidEntity {

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
    private ImapMailBox mailBox;

    @MetaProperty(mandatory = true)
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

    @MetaProperty
    public String getTo() {
        return toList != null ? toList.toString() : "";
    }

    public void setToList(List<String> toList) {
        this.toList = toList;
    }

    public List<String> getCcList() {
        return ccList;
    }

    @MetaProperty
    public String getCc() {
        return ccList != null ? ccList.toString() : "";
    }

    public void setCcList(List<String> ccList) {
        this.ccList = ccList;
    }

    public List<String> getBccList() {
        return bccList;
    }

    @MetaProperty
    public String getBcc() {
        return bccList != null ? bccList.toString() : "";
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

    @MetaProperty
    public String getFlagsList() {
        return flags != null ? flags.toString() : "";
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

    @MetaProperty
    public String getMailBoxHost() {
        return mailBox.getHost();
    }

    @MetaProperty
    public Integer getMailBoxPort() {
        return mailBox.getPort();
    }

    public ImapMailBox getMailBox() {
        return mailBox;
    }

    public void setMailBox(ImapMailBox mailBox) {
        this.mailBox = mailBox;
    }

    @MetaProperty
    public UUID getMailBoxId() {
        return mailBox.getId();
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
                append("mailBoxHost", getMailBoxHost()).
                append("mailBoxPort", getMailBoxPort()).
                append("mailBoxId", getMailBoxId()).
                toString();
    }
}
