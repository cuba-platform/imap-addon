package com.haulmont.components.imap.web.mailbox;

import com.haulmont.bali.util.ParamsMap;
import com.haulmont.components.imap.dto.MailFolderDto;
import com.haulmont.components.imap.entity.MailBox;
import com.haulmont.components.imap.web.ds.MailFolderDatasource;
import com.haulmont.cuba.gui.components.AbstractEditor;
import com.haulmont.cuba.gui.components.TreeTable;

import javax.inject.Inject;
import java.util.*;
import java.util.stream.Collectors;

public class MailBoxTrashFolder extends AbstractEditor<MailBox> {

    @Inject
    private MailFolderDatasource mailFolderDs;

    @Inject
    private TreeTable<MailFolderDto> mailFoldersTable;

    @Override
    public void init(Map<String, Object> params) {
        mailFolderDs.refresh(ParamsMap.of(MailFolderDatasource.FOLDER_DS_MAILBOX_PARAM, params.get("mailBox")));
    }

    @Override
    protected void postInit() {
        MailBox mailBox = getItem();

        String trashFolderName = mailBox.getTrashFolderName();
        if (trashFolderName != null) {
            Collection<MailFolderDto> items = new ArrayList<>(mailFolderDs.getItems());
            MailFolderDto trashFolder = null;
            while (!items.isEmpty() && (trashFolder = findByName(items, trashFolderName)) == null) {
                items = items.stream()
                        .flatMap(folder -> Optional.ofNullable(folder.getChildren()).orElse(Collections.emptyList()).stream())
                        .collect(Collectors.toList());
            }
            if (trashFolder != null) {
                mailFoldersTable.setSelected(trashFolder);
            }
        }

        addCloseWithCommitListener(() -> {
            Set<MailFolderDto> selected = mailFoldersTable.getSelected();
            if (selected != null && !selected.isEmpty()) {
                mailBox.setTrashFolderName(selected.iterator().next().getFullName());
            }
        });
    }

    private MailFolderDto findByName(Collection<MailFolderDto> items, String fullName) {
        return items.stream().filter(folder -> folder.getFullName().equals(fullName)).findFirst().orElse(null);
    }

}
