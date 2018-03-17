package com.haulmont.components.imap.api;

import com.haulmont.components.imap.dto.ImapFolderDto;
import com.haulmont.components.imap.dto.ImapMessageDto;
import com.haulmont.components.imap.entity.ImapMailBox;
import com.haulmont.components.imap.entity.ImapMessageRef;
import com.haulmont.cuba.core.entity.FileDescriptor;

import javax.mail.MessagingException;
import java.util.Collection;

public interface ImapAPI {
    String NAME = "imapcomponent_ImapAPI";

    Collection<ImapFolderDto> fetchFolders(ImapMailBox box) throws MessagingException;
    Collection<ImapFolderDto> fetchFolders(ImapMailBox box, String... folderNames) throws MessagingException;

    ImapMessageDto fetchMessage(ImapMessageRef messageRef) throws MessagingException;
    Collection<ImapMessageDto> fetchMessages(Collection<ImapMessageRef> messageRefs) throws MessagingException;
    Collection<FileDescriptor> fetchAttachments(ImapMessageRef msg) throws MessagingException;

    void moveMessage(ImapMessageRef msg, String folderName) throws MessagingException;
    void deleteMessage(ImapMessageRef messageRef) throws MessagingException;
    void markAsRead(ImapMessageRef messageRef) throws MessagingException;
    void markAsImportant(ImapMessageRef messageRef) throws MessagingException;
    void setFlag(ImapMessageRef messageRef, ImapFlag flag, boolean set) throws MessagingException;
}
