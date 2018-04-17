package com.haulmont.addon.imap.api;

import com.haulmont.addon.imap.dto.ImapFolderDto;
import com.haulmont.addon.imap.dto.ImapMessageDto;
import com.haulmont.addon.imap.entity.ImapMailBox;
import com.haulmont.addon.imap.entity.ImapMessageAttachment;
import com.haulmont.addon.imap.entity.ImapMessage;
import com.haulmont.addon.imap.exception.ImapException;

import java.util.Collection;
import java.util.UUID;

public interface ImapAPI {
    String NAME = "imap_ImapAPI";

    Collection<ImapFolderDto> fetchFolders(ImapMailBox box) throws ImapException;
    Collection<ImapFolderDto> fetchFolders(ImapMailBox box, String... folderNames);

    ImapMessageDto fetchMessage(ImapMessage message);
    Collection<ImapMessageDto> fetchMessages(Collection<ImapMessage> messages);
    Collection<ImapMessageAttachment> fetchAttachments(UUID msgRefId);

    void moveMessage(ImapMessage msg, String folderName);
    void deleteMessage(ImapMessage message);
    void markAsRead(ImapMessage message);
    void markAsImportant(ImapMessage message);
    void setFlag(ImapMessage message, ImapFlag flag, boolean set);
}
