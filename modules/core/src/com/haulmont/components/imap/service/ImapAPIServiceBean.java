package com.haulmont.components.imap.service;

import com.haulmont.components.imap.api.ImapAPI;
import com.haulmont.components.imap.api.ImapFlag;
import com.haulmont.components.imap.core.ImapHelper;
import com.haulmont.components.imap.dto.ImapFolderDto;
import com.haulmont.components.imap.dto.ImapMessageDto;
import com.haulmont.components.imap.entity.ImapMailBox;
import com.haulmont.components.imap.entity.ImapMessage;
import com.haulmont.components.imap.entity.ImapMessageAttachment;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import javax.inject.Inject;
import javax.mail.*;
import java.util.*;

@Service(ImapAPIService.NAME)
public class ImapAPIServiceBean implements ImapAPIService {

    private final static Logger log = LoggerFactory.getLogger(ImapAPIServiceBean.class);

    @Inject
    private ImapHelper imapHelper;

    @Inject
    private ImapAPI imapAPI;

    @Override
    public void testConnection(ImapMailBox box) throws MessagingException {
        log.info("Check connection for {}", box);
        imapHelper.getStore(box);
    }

    @Override
    public Collection<ImapFolderDto> fetchFolders(ImapMailBox box) throws MessagingException {
        return imapAPI.fetchFolders(box);
    }

    @Override
    public Collection<ImapFolderDto> fetchFolders(ImapMailBox box, String... folderNames) throws MessagingException {
        return imapAPI.fetchFolders(box, folderNames);
    }

    @Override
    public ImapMessageDto fetchMessage(ImapMessage message) throws MessagingException {
        return imapAPI.fetchMessage(message);
    }

    @Override
    public Collection<ImapMessageDto> fetchMessages(Collection<ImapMessage> messages) {
        return imapAPI.fetchMessages(messages);
    }

    @Override
    public Collection<ImapMessageAttachment> fetchAttachments(UUID msgId) throws MessagingException {
        return imapAPI.fetchAttachments(msgId);
    }

    @Override
    public void moveMessage(ImapMessage msg, String folderName) throws MessagingException {
        imapAPI.moveMessage(msg, folderName);
    }

    @Override
    public void deleteMessage(ImapMessage message) throws MessagingException {
        imapAPI.deleteMessage(message);
    }

    @Override
    public void markAsRead(ImapMessage message) throws MessagingException {
        imapAPI.markAsRead(message);
    }

    @Override
    public void markAsImportant(ImapMessage message) throws MessagingException {
        imapAPI.markAsImportant(message);
    }

    @Override
    public void setFlag(ImapMessage message, ImapFlag flag, boolean set) throws MessagingException {
        imapAPI.setFlag(message, flag, set);
    }
}