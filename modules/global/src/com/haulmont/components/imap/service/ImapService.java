package com.haulmont.components.imap.service;

import com.haulmont.components.imap.dto.MailFolderDto;
import com.haulmont.components.imap.dto.MailMessageDto;
import com.haulmont.components.imap.entity.ImapMessageRef;
import com.haulmont.components.imap.entity.MailBox;
import javax.mail.MessagingException;
import java.util.List;

public interface ImapService {
    String NAME = "mailcomponent_ImapService";

    void testConnection(MailBox box) throws MessagingException;
    List<MailFolderDto> fetchFolders(MailBox box) throws MessagingException;

    MailMessageDto fetchMessage(ImapMessageRef messageRef) throws MessagingException;
    List<MailMessageDto> fetchMessages(List<ImapMessageRef> messageRefs) throws MessagingException;

    void deleteMessage(ImapMessageRef messageRef) throws MessagingException;
    void markAsRead(ImapMessageRef messageRef) throws MessagingException;
    void markAsImportant(ImapMessageRef messageRef) throws MessagingException;

}