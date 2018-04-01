package com.haulmont.addon.imap.service;

import com.haulmont.addon.imap.api.ImapAPI;
import com.haulmont.addon.imap.api.ImapFlag;
import com.haulmont.addon.imap.core.ImapHelper;
import com.haulmont.addon.imap.dto.ImapFolderDto;
import com.haulmont.addon.imap.dto.ImapMessageDto;
import com.haulmont.addon.imap.entity.ImapMailBox;
import com.haulmont.addon.imap.entity.ImapMessage;
import com.haulmont.addon.imap.entity.ImapMessageAttachment;
import com.haulmont.addon.imap.exception.ImapException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import javax.inject.Inject;
import javax.mail.*;
import java.util.*;

@Service(ImapAPIService.NAME)
@SuppressWarnings({"CdiInjectionPointsInspection", "SpringJavaAutowiredFieldsWarningInspection"})
public class ImapAPIServiceBean implements ImapAPIService {

    private final static Logger log = LoggerFactory.getLogger(ImapAPIServiceBean.class);

    @Inject
    private ImapHelper imapHelper;

    @Inject
    private ImapAPI imapAPI;

    @Override
    public void testConnection(ImapMailBox box) throws ImapException {
        log.info("Check connection for {}", box);
        try {
            imapHelper.getStore(box);
        } catch (MessagingException e) {
            throw new ImapException(e);
        }

    }

    @Override
    public Collection<ImapFolderDto> fetchFolders(ImapMailBox box) {
        return imapAPI.fetchFolders(box);
    }

    @Override
    public Collection<ImapFolderDto> fetchFolders(ImapMailBox box, String... folderNames) {
        return imapAPI.fetchFolders(box, folderNames);
    }

    @Override
    public ImapMessageDto fetchMessage(ImapMessage message) {
        return imapAPI.fetchMessage(message);
    }

    @Override
    public Collection<ImapMessageDto> fetchMessages(Collection<ImapMessage> messages) {
        return imapAPI.fetchMessages(messages);
    }

    @Override
    public Collection<ImapMessageAttachment> fetchAttachments(UUID msgId) {
        return imapAPI.fetchAttachments(msgId);
    }

    @Override
    public void moveMessage(ImapMessage msg, String folderName) {
        imapAPI.moveMessage(msg, folderName);
    }

    @Override
    public void deleteMessage(ImapMessage message) {
        imapAPI.deleteMessage(message);
    }

    @Override
    public void markAsRead(ImapMessage message) {
        imapAPI.markAsRead(message);
    }

    @Override
    public void markAsImportant(ImapMessage message) {
        imapAPI.markAsImportant(message);
    }

    @Override
    public void setFlag(ImapMessage message, ImapFlag flag, boolean set) {
        imapAPI.setFlag(message, flag, set);
    }
}