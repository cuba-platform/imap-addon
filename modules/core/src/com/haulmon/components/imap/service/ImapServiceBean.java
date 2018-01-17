package com.haulmon.components.imap.service;

import com.haulmon.components.imap.core.ImapBase;
import com.haulmon.components.imap.dto.MailFolderDto;
import com.haulmon.components.imap.entity.MailBox;
import com.sun.mail.imap.IMAPFolder;
import org.springframework.stereotype.Service;

import javax.mail.*;
import java.util.*;

@Service(ImapService.NAME)
public class ImapServiceBean extends ImapBase implements ImapService {

    @Override
    public void testConnection(MailBox box) throws MessagingException {
        getStore(box);
    }

    @Override
    public List<MailFolderDto> fetchFolders(MailBox box) throws MessagingException {
        Store store = getStore(box);

        List<MailFolderDto> result = new ArrayList<>();

        Folder defaultFolder = store.getDefaultFolder();

        IMAPFolder[] rootFolders = (IMAPFolder[]) defaultFolder.list();
        for (IMAPFolder folder : rootFolders) {
            result.add(map(folder));
        }


        return result;
    }

    private MailFolderDto map(IMAPFolder folder) throws MessagingException {
        List<MailFolderDto> subFolders = new ArrayList<>();

        if ((folder.getType() & Folder.HOLDS_FOLDERS) != 0) {
            for (Folder childFolder : folder.list()) {
                subFolders.add(map((IMAPFolder) childFolder));
            }
        }
        MailFolderDto result = new MailFolderDto(
                folder.getName(),
                folder.getFullName(),
                (folder.getType() & Folder.HOLDS_MESSAGES) != 0,
                subFolders);
        result.getChildren().forEach(f -> f.setParent(result));
        return result;
    }
}