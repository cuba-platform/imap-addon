package com.haulmont.addon.imap.dto;

import com.haulmont.addon.imap.entity.ImapMailBox;
import com.haulmont.chile.core.annotations.MetaClass;
import com.haulmont.chile.core.annotations.MetaProperty;
import com.haulmont.chile.core.annotations.NamePattern;
import com.haulmont.cuba.core.entity.BaseUuidEntity;
import org.apache.commons.lang3.builder.ToStringBuilder;

import java.util.Date;
import java.util.List;

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
    private List<String> toList;
    private List<String> ccList;
    private List<String> bccList;
    @MetaProperty
    private String subject;
    @MetaProperty
    private String body;
    @MetaProperty
    private Boolean html = false;
    private List<String> flagsList;
    @MetaProperty
    private Date date;

    @MetaProperty(mandatory = true)
    private ImapMailBox mailBox;

    @MetaProperty(mandatory = true)
    private String folderName;

    @SuppressWarnings("UnusedReturnValue")
    public Long getUid() {
        return uid;
    }

    public void setUid(Long uid) {
        this.uid = uid;
    }

    @SuppressWarnings("UnusedReturnValue")
    public String getFrom() {
        return from;
    }

    public void setFrom(String from) {
        this.from = from;
    }

    @SuppressWarnings("UnusedReturnValue")
    @MetaProperty
    public String getTo() {
        return toList != null ? toList.toString() : "";
    }

    public void setToList(List<String> toList) {
        this.toList = toList;
    }

    @SuppressWarnings("UnusedReturnValue")
    @MetaProperty
    public String getCc() {
        return ccList != null ? ccList.toString() : "";
    }

    public void setCcList(List<String> ccList) {
        this.ccList = ccList;
    }

    @SuppressWarnings("UnusedReturnValue")
    @MetaProperty
    public String getBcc() {
        return bccList != null ? bccList.toString() : "";
    }

    public void setBccList(List<String> bccList) {
        this.bccList = bccList;
    }

    @SuppressWarnings("UnusedReturnValue")
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

    @SuppressWarnings("UnusedReturnValue")
    @MetaProperty
    public String getFlags() {
        return flagsList != null ? flagsList.toString() : "";
    }

    public void setFlagsList(List<String> flags) {
        this.flagsList = flags;
    }

    public Date getDate() {
        return date;
    }

    public void setDate(Date sendDate) {
        this.date = sendDate;
    }

    public ImapMailBox getMailBox() {
        return mailBox;
    }

    public void setMailBox(ImapMailBox mailBox) {
        this.mailBox = mailBox;
    }

    @SuppressWarnings("UnusedReturnValue")
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
                append("flags", flagsList).
                append("mailBoxHost", mailBox.getHost()).
                append("mailBoxPort", mailBox.getPort()).
                append("mailBoxId", mailBox.getId()).
                toString();
    }
}
