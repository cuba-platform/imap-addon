package com.haulmon.components.imap.service;

import com.haulmon.components.imap.core.ImapBase;
import com.haulmon.components.imap.dto.FolderDto;
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
    public List<FolderDto> fetchFolders(MailBox box) throws MessagingException {
        Store store = getStore(box);

        List<FolderDto> result = new ArrayList<>();

        Folder defaultFolder = store.getDefaultFolder();

        IMAPFolder[] rootFolders = (IMAPFolder[]) defaultFolder.list();
        for (IMAPFolder folder : rootFolders) {
            result.add(map(folder));
        }


        return result;
    }

    private FolderDto map(IMAPFolder folder) throws MessagingException {
        if ((folder.getType() & Folder.HOLDS_MESSAGES) != 0) {
            return new FolderDto(folder.getName(), folder.getFullName(), true, Collections.emptyList());
        } else if ((folder.getType() & Folder.HOLDS_FOLDERS) != 0) {
            List<FolderDto> subFolders = new ArrayList<>();
            for (Folder childFolder : folder.list()) {
                subFolders.add(map((IMAPFolder) childFolder));
            }

            return new FolderDto(folder.getName(), folder.getFullName(), false, subFolders);
        }
        //todo: log such strange case
        return new FolderDto(folder.getName(), folder.getFullName(), false, Collections.emptyList());
    }
}