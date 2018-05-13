package com.haulmont.addon.imap.service;

import com.haulmont.addon.imap.api.ImapFlag;
import com.haulmont.addon.imap.dto.ImapFolderDto;
import com.haulmont.addon.imap.dto.ImapMessageDto;
import com.haulmont.addon.imap.entity.ImapMailBox;
import com.haulmont.addon.imap.entity.ImapMessage;
import com.haulmont.addon.imap.entity.ImapMessageAttachment;

import java.util.Collection;
import java.util.List;

@SuppressWarnings("unused")
public interface ImapAPIService {
    String NAME = "imap_ImapAPIService";

    void testConnection(ImapMailBox box);
    Collection<ImapFolderDto> fetchFolders(ImapMailBox box);

    ImapMessageDto fetchMessage(ImapMessage message);
    List<ImapMessageDto> fetchMessages(List<ImapMessage> messages);

    void moveMessage(ImapMessage msg, String folderName);
    void deleteMessage(ImapMessage message);
    void setFlag(ImapMessage message, ImapFlag flag, boolean set);

    Collection<ImapMessageAttachment> fetchAttachments(ImapMessage message);
    byte[] loadFile(ImapMessageAttachment attachment);
}