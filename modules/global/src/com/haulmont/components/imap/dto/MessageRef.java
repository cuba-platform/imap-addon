package com.haulmont.components.imap.dto;

import com.haulmont.components.imap.entity.MailBox;

import java.io.Serializable;

public class MessageRef implements Serializable {
    private MailBox mailBox;
    private String folderName;
    private long uid;

    public MessageRef() {
    }

    public MessageRef(MailBox mailBox, String folderName, long uid) {
        this.mailBox = mailBox;
        this.folderName = folderName;
        this.uid = uid;
    }

    public MailBox getMailBox() {
        return mailBox;
    }

    public void setMailBox(MailBox mailBox) {
        this.mailBox = mailBox;
    }

    public String getFolderName() {
        return folderName;
    }

    public void setFolderName(String folderName) {
        this.folderName = folderName;
    }

    public long getUid() {
        return uid;
    }

    public void setUid(long uid) {
        this.uid = uid;
    }

    @Override
    public String toString() {
        return "MessageRef{" +
                "mailBox=" + mailBox +
                ", folderName='" + folderName + '\'' +
                ", uid=" + uid +
                '}';
    }
}