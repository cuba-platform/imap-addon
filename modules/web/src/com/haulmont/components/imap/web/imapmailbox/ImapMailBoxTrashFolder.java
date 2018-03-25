package com.haulmont.components.imap.web.imapmailbox;

import com.haulmont.bali.util.ParamsMap;
import com.haulmont.components.imap.dto.ImapFolderDto;
import com.haulmont.components.imap.entity.ImapMailBox;
import com.haulmont.components.imap.web.ds.ImapFolderDatasource;
import com.haulmont.cuba.gui.components.AbstractEditor;
import com.haulmont.cuba.gui.components.TreeTable;
import com.haulmont.cuba.gui.data.Datasource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import java.util.*;
import java.util.stream.Collectors;

public class ImapMailBoxTrashFolder extends AbstractEditor<ImapMailBox> {

    private final static Logger log = LoggerFactory.getLogger(ImapMailBoxTrashFolder.class);

    @Inject
    private ImapFolderDatasource imapFolderDs;

    @Inject
    private TreeTable<ImapFolderDto> imapFoldersTable;

    @Override
    public void init(Map<String, Object> params) {
        imapFolderDs.refresh(ParamsMap.of(ImapFolderDatasource.FOLDER_DS_MAILBOX_PARAM, params.get("mailBox")));
    }

    @Override
    protected void postInit() {
        ImapMailBox mailBox = getItem();

        String trashFolderName = mailBox.getTrashFolderName();
        if (trashFolderName != null) {
            Collection<ImapFolderDto> items = new ArrayList<>(imapFolderDs.getItems());
            ImapFolderDto trashFolder = null;
            log.debug("find trash folder {} among {}", trashFolderName, items);
            while (!items.isEmpty() && (trashFolder = findByName(items, trashFolderName)) == null) {
                log.debug("Going deeper to sub folders of {} to find trash folder {}", items, trashFolderName);
                items = items.stream()
                        .flatMap(folder -> Optional.ofNullable(folder.getChildren()).orElse(Collections.emptyList()).stream())
                        .collect(Collectors.toList());
            }
            if (trashFolder != null) {
                imapFoldersTable.setSelected(trashFolder);
            }
        }

        addCloseWithCommitListener(() -> {
            Set<ImapFolderDto> selected = imapFoldersTable.getSelected();
            if (selected != null && !selected.isEmpty()) {
                log.debug("selected trash folder {}", selected);
                @SuppressWarnings("unchecked")
                Datasource<ImapMailBox> parentDs = getParentDs();
                //noinspection ConstantConditions
                parentDs.getItem().setTrashFolderName(selected.iterator().next().getFullName());
            }
        });
    }

    private ImapFolderDto findByName(Collection<ImapFolderDto> items, String fullName) {
        return items.stream().filter(folder -> folder.getFullName().equals(fullName)).findFirst().orElse(null);
    }

}
