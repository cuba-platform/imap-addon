package com.haulmont.addon.imap.service;

import com.haulmont.addon.imap.api.ImapAPI;
import com.haulmont.addon.imap.api.ImapAttachmentsAPI;
import com.haulmont.addon.imap.api.ImapFlag;
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
import java.util.*;

@Service(ImapAPIService.NAME)
public class ImapAPIServiceBean implements ImapAPIService {

    private final static Logger log = LoggerFactory.getLogger(ImapAPIServiceBean.class);

    private final ImapAPI imapAPI;
    private final ImapAttachmentsAPI imapAttachmentsAPI;

    @Inject
    public ImapAPIServiceBean(ImapAPI imapAPI,
                              ImapAttachmentsAPI imapAttachmentsAPI) {
        this.imapAPI = imapAPI;
        this.imapAttachmentsAPI = imapAttachmentsAPI;
    }

    @Override
    public void testConnection(ImapMailBox box) throws ImapException {
        log.info("Check connection for {}", box);
        fetchFolders(box);

    }

    @Override
    public Collection<ImapFolderDto> fetchFolders(ImapMailBox box) {
        return imapAPI.fetchFolders(box);
    }

    @Override
    public ImapMessageDto fetchMessage(ImapMessage message) {
        return imapAPI.fetchMessage(message);
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
    public void setFlag(ImapMessage message, ImapFlag flag, boolean set) {
        imapAPI.setFlag(message, flag, set);
    }

    @Override
    public Collection<ImapMessageAttachment> fetchAttachments(ImapMessage message) {
        return imapAttachmentsAPI.fetchAttachments(message);
    }

    @Override
    public byte[] loadFile(ImapMessageAttachment attachment) {
        return imapAttachmentsAPI.loadFile(attachment);
    }
}