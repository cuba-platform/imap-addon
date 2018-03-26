package com.haulmont.addon.imap.api;

import com.haulmont.addon.imap.dto.ImapFolderDto;
import com.haulmont.addon.imap.dto.ImapMessageDto;
import com.haulmont.addon.imap.entity.ImapMailBox;
import com.haulmont.addon.imap.entity.ImapMessageAttachment;
import com.haulmont.addon.imap.entity.ImapMessage;

import javax.mail.MessagingException;
import java.util.Collection;
import java.util.UUID;

public interface ImapAPI {
    String NAME = "imapcomponent_ImapAPI";

    Collection<ImapFolderDto> fetchFolders(ImapMailBox box) throws MessagingException;
    Collection<ImapFolderDto> fetchFolders(ImapMailBox box, String... folderNames) throws MessagingException;

    ImapMessageDto fetchMessage(ImapMessage message) throws MessagingException;
    Collection<ImapMessageDto> fetchMessages(Collection<ImapMessage> messages);
    Collection<ImapMessageAttachment> fetchAttachments(UUID msgRefId) throws MessagingException;

    void moveMessage(ImapMessage msg, String folderName) throws MessagingException;
    void deleteMessage(ImapMessage message) throws MessagingException;
    void markAsRead(ImapMessage message) throws MessagingException;
    void markAsImportant(ImapMessage message) throws MessagingException;
    void setFlag(ImapMessage message, ImapFlag flag, boolean set) throws MessagingException;
}
