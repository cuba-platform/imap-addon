package com.haulmont.components.imap.service;

import com.haulmont.components.imap.dto.ImapFolderDto;
import com.haulmont.components.imap.entity.ImapMailBox;

import javax.mail.MessagingException;
import java.util.Collection;

public interface ImapAPIService {
    String NAME = "imapcomponent_ImapAPIService";

    void testConnection(ImapMailBox box) throws MessagingException;
    Collection<ImapFolderDto> fetchFolders(ImapMailBox box) throws MessagingException;
    Collection<ImapFolderDto> fetchFolders(ImapMailBox box, String... folderNames) throws MessagingException;

}