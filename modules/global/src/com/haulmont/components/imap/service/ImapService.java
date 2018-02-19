package com.haulmont.components.imap.service;

import com.haulmont.components.imap.dto.MailFolderDto;
import com.haulmont.components.imap.dto.MailMessageDto;
import com.haulmont.components.imap.dto.MessageRef;
import com.haulmont.components.imap.entity.MailBox;
import javax.mail.MessagingException;
import java.util.List;

public interface ImapService {
    String NAME = "mailcomponent_ImapService";

    void testConnection(MailBox box) throws MessagingException;
    List<MailFolderDto> fetchFolders(MailBox box) throws MessagingException;

    MailMessageDto fetchMessage(MessageRef messageRef) throws MessagingException;
    List<MailMessageDto> fetchMessages(List<MessageRef> messageRefs) throws MessagingException;

    void deleteMessage(MessageRef messageRef) throws MessagingException;
    void markAsRead(MessageRef messageRef) throws MessagingException;
    void markAsImportant(MessageRef messageRef) throws MessagingException;

}