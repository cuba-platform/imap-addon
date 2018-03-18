package com.haulmont.components.imap.service;

import com.haulmont.components.imap.api.ImapAPI;
import com.haulmont.components.imap.api.ImapFlag;
import com.haulmont.components.imap.core.ImapHelper;
import com.haulmont.components.imap.dto.ImapFolderDto;
import com.haulmont.components.imap.dto.ImapMessageDto;
import com.haulmont.components.imap.entity.ImapMailBox;
import com.haulmont.components.imap.entity.ImapMessageAttachmentRef;
import com.haulmont.components.imap.entity.ImapMessageRef;
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



}