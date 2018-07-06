package com.haulmont.addon.imap.entity;

import javax.persistence.*;

import com.haulmont.cuba.core.entity.annotation.OnDeleteInverse;
import com.haulmont.cuba.core.global.DeletePolicy;

import javax.validation.constraints.NotNull;
import com.haulmont.chile.core.annotations.NamePattern;

@NamePattern("%s with %s|message,status")
@Table(name = "IMAP_MESSAGE_SYNC")
@Entity(name = "imap$MessageSync")
public class ImapMessageSync extends ImapFlagsHolder {
    private static final long serialVersionUID = 4840104340467502496L;

    @NotNull
    @OnDeleteInverse(DeletePolicy.CASCADE)
    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "MESSAGE_ID", unique = true)
    private ImapMessage message;

    @OnDeleteInverse(DeletePolicy.CASCADE)
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "FOLDER_ID")
    private ImapFolder folder;

    @NotNull
    @Column(name = "STATUS", nullable = false)
    private String status;

    @Column(name = "NEW_FOLDER_NAME")
    private String newFolderName;

    public void setMessage(ImapMessage message) {
        this.message = message;
    }

    public ImapMessage getMessage() {
        return message;
    }

    public void setStatus(ImapSyncStatus status) {
        this.status = status == null ? null : status.getId();
    }

    public ImapSyncStatus getStatus() {
        return status == null ? null : ImapSyncStatus.fromId(status);
    }

    public ImapFolder getFolder() {
        return folder;
    }

    public void setFolder(ImapFolder folder) {
        this.folder = folder;
    }

    public String getNewFolderName() {
        return newFolderName;
    }

    public void setNewFolderName(String newFolderName) {
        this.newFolderName = newFolderName;
    }
}