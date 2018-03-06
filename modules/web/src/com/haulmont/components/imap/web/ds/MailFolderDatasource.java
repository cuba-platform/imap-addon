package com.haulmont.components.imap.web.ds;

import com.haulmont.components.imap.dto.MailFolderDto;
import com.haulmont.components.imap.entity.MailBox;
import com.haulmont.components.imap.entity.MailFolder;
import com.haulmont.components.imap.service.ImapAPIService;
import com.haulmont.cuba.core.global.AppBeans;
import com.haulmont.cuba.gui.data.impl.CustomHierarchicalDatasource;

import javax.mail.MessagingException;
import java.util.*;
import java.util.stream.Collectors;

public class MailFolderDatasource extends CustomHierarchicalDatasource<MailFolderDto, UUID> {

    public static final String FOLDER_DS_MAILBOX_PARAM = "mailBox";
    private ImapAPIService service = AppBeans.get(ImapAPIService.class);

    @Override
    protected Collection<MailFolderDto> getEntities(Map<String, Object> params) {
        MailBox mailBox = (MailBox) params.get(FOLDER_DS_MAILBOX_PARAM);
        if (mailBox == null) {
            throw new UnsupportedOperationException();
        }
        try {
            Collection<MailFolderDto> rootFolders = service.fetchFolders(mailBox);

            List<MailFolderDto> folders = new ArrayList<>(rootFolders);

            rootFolders.forEach(folder -> addFolderWithChildren(folders, folder));

            List<MailFolder> selectedFolders = mailBox.getFolders();
            if (selectedFolders == null) {
                selectedFolders = Collections.emptyList();
            }
            List<String> fullNames = selectedFolders.stream().map(MailFolder::getName).collect(Collectors.toList());
            folders.forEach(f -> f.setSelected(fullNames.contains(f.getFullName())));

            return folders;
        } catch (MessagingException e) {
            throw new RuntimeException("Cannot fetch folders for mailBox " + mailBox, e);
        }

    }

    private void addFolderWithChildren(List<MailFolderDto> foldersList, MailFolderDto folder) {
        foldersList.add(folder);
        List<MailFolderDto> children = folder.getChildren();
        if (children != null) {
            children.forEach(childFolder -> addFolderWithChildren(foldersList, childFolder));
        }
    }
}
