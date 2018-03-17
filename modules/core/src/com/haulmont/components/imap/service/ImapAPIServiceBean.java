package com.haulmont.components.imap.service;

import com.haulmont.components.imap.api.ImapAPI;
import com.haulmont.components.imap.api.ImapFlag;
import com.haulmont.components.imap.core.ImapHelper;
import com.haulmont.components.imap.dto.ImapFolderDto;
import com.haulmont.components.imap.dto.ImapMessageDto;
import com.haulmont.components.imap.entity.ImapMailBox;
import com.haulmont.components.imap.entity.ImapMessageRef;
import com.haulmont.cuba.core.entity.FileDescriptor;
import org.springframework.stereotype.Service;

import javax.inject.Inject;
import javax.mail.*;
import java.util.*;

@Service(ImapAPIService.NAME)
public class ImapAPIServiceBean implements ImapAPIService {


    @Inject
    private ImapHelper imapHelper;

    @Inject
    private ImapAPI imapAPI;

    @Override
    public void testConnection(ImapMailBox box) throws MessagingException {
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
    public ImapMessageDto fetchMessage(ImapMessageRef messageRef) throws MessagingException {
        return imapAPI.fetchMessage(messageRef);
    }

    @Override
    public Collection<ImapMessageDto> fetchMessages(Collection<ImapMessageRef> messageRefs) throws MessagingException {
        return imapAPI.fetchMessages(messageRefs);
    }

    @Override
    public Collection<FileDescriptor> fetchAttachments(ImapMessageRef ref) throws MessagingException {
        return imapAPI.fetchAttachments(ref);
    }

    @Override
    public void deleteMessage(ImapMessageRef messageRef) throws MessagingException {
        imapAPI.deleteMessage(messageRef);
    }

    @Override
    public void moveMessage(ImapMessageRef ref, String folderName) throws MessagingException {
        imapAPI.moveMessage(ref, folderName);
    }

    @Override
    public void markAsRead(ImapMessageRef messageRef) throws MessagingException {
        imapAPI.markAsRead(messageRef);
    }

    @Override
    public void markAsImportant(ImapMessageRef messageRef) throws MessagingException {
        imapAPI.markAsImportant(messageRef);
    }

    @Override
    public void setFlag(ImapMessageRef messageRef, ImapFlag flag, boolean set) throws MessagingException {
        imapAPI.setFlag(messageRef, flag, set);
    }

}