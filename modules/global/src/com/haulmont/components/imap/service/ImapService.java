package com.haulmont.components.imap.service;

import com.haulmont.components.imap.dto.MailFolderDto;
import com.haulmont.components.imap.dto.MailMessageDto;
import com.haulmont.components.imap.entity.MailBox;
import com.haulmont.components.imap.entity.MailMessage;
import javax.mail.MessagingException;
import java.util.List;

public interface ImapService {
    String NAME = "mailcomponent_ImapService";

    void testConnection(MailBox box) throws MessagingException;
    List<MailFolderDto> fetchFolders(MailBox box) throws MessagingException;

    MailMessageDto fetchMessage(MailMessage message) throws MessagingException;
}