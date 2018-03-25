package com.haulmont.components.imap.web.ds;

import com.haulmont.components.imap.dto.ImapFolderDto;
import com.haulmont.components.imap.entity.ImapMailBox;
import com.haulmont.components.imap.entity.ImapFolder;
import com.haulmont.components.imap.service.ImapAPIService;
import com.haulmont.cuba.core.global.AppBeans;
import com.haulmont.cuba.gui.data.impl.CustomHierarchicalDatasource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.mail.MessagingException;
import java.util.*;
import java.util.stream.Collectors;

public class ImapFolderDatasource extends CustomHierarchicalDatasource<ImapFolderDto, UUID> {

    private final static Logger log = LoggerFactory.getLogger(ImapFolderDatasource.class);

    public static final String FOLDER_DS_MAILBOX_PARAM = "mailBox";
    private ImapAPIService service = AppBeans.get(ImapAPIService.class);

    @Override
    protected Collection<ImapFolderDto> getEntities(Map<String, Object> params) {
        ImapMailBox mailBox = (ImapMailBox) params.get(FOLDER_DS_MAILBOX_PARAM);
        if (mailBox == null) {
            throw new UnsupportedOperationException();
        }
        try {
            log.debug("Fetch folders for {}", mailBox);
            Collection<ImapFolderDto> rootFolders = service.fetchFolders(mailBox);

            List<ImapFolderDto> folders = new ArrayList<>(rootFolders);

            rootFolders.forEach(folder -> addFolderWithChildren(folders, folder));

            List<ImapFolder> selectedFolders = mailBox.getFolders();
            if (selectedFolders == null) {
                selectedFolders = Collections.emptyList();
            }
            log.debug("Fetch folders for {}. All folders: {}, selected: {}", mailBox, folders, selectedFolders);

            List<String> fullNames = selectedFolders.stream().map(ImapFolder::getName).collect(Collectors.toList());
            folders.forEach(f -> f.setSelected(fullNames.contains(f.getFullName())));

            return folders;
        } catch (MessagingException e) {
            throw new RuntimeException("Cannot fetch folders for mailBox " + mailBox, e);
        }

    }

    private void addFolderWithChildren(List<ImapFolderDto> foldersList, ImapFolderDto folder) {
        foldersList.add(folder);
        List<ImapFolderDto> children = folder.getChildren();
        if (children != null) {
            children.forEach(childFolder -> addFolderWithChildren(foldersList, childFolder));
        }
    }
}
