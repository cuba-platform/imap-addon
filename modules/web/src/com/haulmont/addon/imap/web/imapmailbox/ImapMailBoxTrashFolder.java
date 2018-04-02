package com.haulmont.addon.imap.web.imapmailbox;

import com.haulmont.addon.imap.entity.ImapFolder;
import com.haulmont.addon.imap.entity.ImapMailBox;
import com.haulmont.cuba.gui.components.AbstractEditor;
import com.haulmont.cuba.gui.components.SelectAction;
import com.haulmont.cuba.gui.components.TreeTable;
import com.haulmont.cuba.gui.data.Datasource;
import com.haulmont.cuba.gui.data.HierarchicalDatasource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import java.util.*;

@SuppressWarnings({"CdiInjectionPointsInspection", "SpringJavaAutowiredFieldsWarningInspection"})
public class ImapMailBoxTrashFolder extends AbstractEditor<ImapMailBox> {

    private final static Logger log = LoggerFactory.getLogger(ImapMailBoxTrashFolder.class);

    @Inject
    private HierarchicalDatasource<ImapFolder, UUID> imapFolderDs;

    @Inject
    private TreeTable<ImapFolder> imapFoldersTable;

    @Override
    protected void postInit() {
        ImapMailBox mailBox = getItem();

        String trashFolderName = mailBox.getTrashFolderName();
        if (trashFolderName != null) {
            Collection<ImapFolder> items = new ArrayList<>(imapFolderDs.getItems());

            log.debug("find trash folder {} among {}", trashFolderName, items);
            items.stream()
                    .filter(f -> f.getName().equals(trashFolderName))
                    .findFirst().ifPresent(trashFolder -> imapFoldersTable.setSelected(trashFolder));

        }

        imapFolderDs.addItemChangeListener(e -> {
            ImapFolder folder = e.getItem();
            if (folder == null) {
                return;
            }
            if (Boolean.TRUE.equals(folder.getDisabled()) ||
                    !Boolean.TRUE.equals(folder.getSelectable())) {
                imapFoldersTable.setSelected(e.getPrevItem());
            }
        });

        addCloseWithCommitListener(() -> {
            Set<ImapFolder> selected = imapFoldersTable.getSelected();
            if (selected != null && !selected.isEmpty()) {
                log.debug("selected trash folder {}", selected);
                @SuppressWarnings("unchecked")
                Datasource<ImapMailBox> parentDs = getParentDs();
                //noinspection ConstantConditions
                parentDs.getItem().setTrashFolderName(selected.iterator().next().getName());
            }
        });
    }

}
