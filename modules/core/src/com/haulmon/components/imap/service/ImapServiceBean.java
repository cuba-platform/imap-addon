package com.haulmon.components.imap.service;

import com.haulmon.components.imap.core.ImapBase;
import com.haulmon.components.imap.entity.MailBox;
import org.springframework.stereotype.Service;

import javax.mail.*;
import java.util.*;
import java.util.stream.Collectors;

@Service(ImapService.NAME)
public class ImapServiceBean extends ImapBase implements ImapService {

    @Override
    public void testConnection(MailBox box) throws MessagingException {
        getStore(box);
    }

    @Override
    public List<String> fetchFolders(MailBox box) throws MessagingException {
        Store store = getStore(box);

        Folder defaultFolder = store.getDefaultFolder();

        return Arrays.stream(defaultFolder.list()).map(Folder::getName).collect(Collectors.toList());
    }
}