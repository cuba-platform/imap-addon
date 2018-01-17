package com.haulmon.components.imap.service;

import com.haulmon.components.imap.dto.MailFolderDto;
import com.haulmon.components.imap.entity.MailBox;

import javax.mail.MessagingException;
import java.util.List;

public interface ImapService {
    String NAME = "mailcomponent_ImapService";

    void testConnection(MailBox box) throws MessagingException;
    List<MailFolderDto> fetchFolders(MailBox box) throws MessagingException;
}