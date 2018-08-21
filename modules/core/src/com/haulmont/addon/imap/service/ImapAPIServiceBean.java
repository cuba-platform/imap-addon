package com.haulmont.addon.imap.service;

import com.haulmont.addon.imap.api.ImapAPI;
import com.haulmont.addon.imap.api.ImapAttachmentsAPI;
import com.haulmont.addon.imap.api.ImapFlag;
import com.haulmont.addon.imap.core.ImapHelper;
import com.haulmont.addon.imap.core.ImapOperations;
import com.haulmont.addon.imap.dto.ImapConnectResult;
import com.haulmont.addon.imap.dto.ImapFolderDto;
import com.haulmont.addon.imap.dto.ImapMessageDto;
import com.haulmont.addon.imap.entity.ImapMailBox;
import com.haulmont.addon.imap.entity.ImapMessage;
import com.haulmont.addon.imap.entity.ImapMessageAttachment;
import com.haulmont.addon.imap.exception.ImapException;
import com.sun.mail.imap.IMAPStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import javax.inject.Inject;
import javax.mail.MessagingException;
import java.util.*;

@Service(ImapAPIService.NAME)
public class ImapAPIServiceBean implements ImapAPIService {

    private final static Logger log = LoggerFactory.getLogger(ImapAPIServiceBean.class);

    private final ImapAPI imapAPI;
    private final ImapAttachmentsAPI imapAttachmentsAPI;
    private final ImapHelper imapHelper;
    private final ImapOperations imapOperations;

    @Inject
    public ImapAPIServiceBean(ImapAPI imapAPI,
                              ImapAttachmentsAPI imapAttachmentsAPI,
                              ImapHelper imapHelper,
                              @SuppressWarnings("CdiInjectionPointsInspection") ImapOperations imapOperations) {
        this.imapAPI = imapAPI;
        this.imapAttachmentsAPI = imapAttachmentsAPI;
        this.imapHelper = imapHelper;
        this.imapOperations = imapOperations;
    }

    @Override
    public ImapConnectResult testConnection(ImapMailBox box) {
        log.info("Check connection for {}", box);

        ImapConnectResult result = new ImapConnectResult();
        result.setMailBox(box);
        try {
            IMAPStore store = imapHelper.getStore(box);
            try {
                result.setAllFolders(imapOperations.fetchFolders(store));
                result.setCustomFlagSupported(imapOperations.supportsCustomFlag(store));
                result.setSuccess(true);
            } finally {
                store.close();
            }
        } catch (MessagingException e) {
            result.setSuccess(false);
            result.setFailure(new ImapException(e));
        }

        return result;
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