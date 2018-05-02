package com.haulmont.addon.imap.service;

import com.haulmont.addon.imap.api.ImapFlag;
import com.haulmont.addon.imap.dto.ImapFolderDto;
import com.haulmont.addon.imap.dto.ImapMessageDto;
import com.haulmont.addon.imap.entity.ImapMailBox;
import com.haulmont.addon.imap.entity.ImapMessage;

import java.util.Collection;

@SuppressWarnings("unused")
public interface ImapAPIService {
    String NAME = "imap_ImapAPIService";

    void testConnection(ImapMailBox box);
    Collection<ImapFolderDto> fetchFolders(ImapMailBox box);
    Collection<ImapFolderDto> fetchFolders(ImapMailBox box, String... folderNames);

    ImapMessageDto fetchMessage(ImapMessage message);
    Collection<ImapMessageDto> fetchMessages(Collection<ImapMessage> messages);

    void moveMessage(ImapMessage msg, String folderName);
    void deleteMessage(ImapMessage message);
    void markAsRead(ImapMessage message);
    void markAsImportant(ImapMessage message);
    void setFlag(ImapMessage message, ImapFlag flag, boolean set);
}