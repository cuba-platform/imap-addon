package com.haulmont.components.imap.web.imapmailbox;

import com.haulmont.components.imap.entity.*;
import com.haulmont.components.imap.service.ImapAPIService;
import com.haulmont.bali.util.ParamsMap;
import com.haulmont.components.imap.entity.ImapAuthenticationMethod;
import com.haulmont.components.imap.entity.ImapSimpleAuthentication;
import com.haulmont.cuba.core.global.*;
import com.haulmont.cuba.gui.WindowManager;
import com.haulmont.cuba.gui.components.AbstractEditor;
import com.haulmont.components.imap.entity.ImapMailBox;
import com.haulmont.cuba.gui.components.FieldGroup;
import com.haulmont.cuba.gui.data.CollectionDatasource;
import com.haulmont.cuba.gui.data.Datasource;

import javax.inject.Inject;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

public class ImapMailBoxEdit extends AbstractEditor<ImapMailBox> {

    @Inject
    private FieldGroup mainParams;

    @Inject
    private ImapAPIService service;

    @Inject
    private Metadata metadata;

    @Inject
    private Datasource<ImapMailBox> mailBoxDs;

    @Inject
    private CollectionDatasource<ImapFolder, UUID> foldersDs;

    @Inject
    private DataManager dm;

    public void checkTheConnection() {
        try {
            service.testConnection(getItem());
            showNotification("Connection succeed", NotificationType.HUMANIZED);
        } catch (Exception e) {
            showNotification("Connection failed", NotificationType.ERROR);
        }
    }

    public void selectTrashFolder() {
        ImapMailBox mailBox = getItem();
        Boolean newEntity = mailBox.getNewEntity();
        AbstractEditor selectFolders = openEditor(
                "mailcomponent$ImapMailBox.trashFolder",
                mailBox,
                WindowManager.OpenType.THIS_TAB,
                ParamsMap.of("mailBox", mailBox),
                mailBoxDs
        );
        selectFolders.addCloseWithCommitListener(() -> getItem().setNewEntity(newEntity));
    }

    public void selectFolders() {
        ImapMailBox mailBox = getItem();
        Boolean newEntity = mailBox.getNewEntity();
        AbstractEditor selectFolders = openEditor(
                "mailcomponent$ImapMailBox.folders",
                mailBox,
                WindowManager.OpenType.THIS_TAB,
                ParamsMap.of("mailBox", mailBox),
                mailBoxDs
        );
        selectFolders.addCloseWithCommitListener(() -> {
            foldersDs.refresh();
            getItem().setNewEntity(newEntity);
        });
    }

    @Override
    protected void initNewItem(ImapMailBox item) {
        item.setAuthenticationMethod(ImapAuthenticationMethod.SIMPLE);
        item.setPollInterval(10 * 60);
        item.setAuthentication(metadata.create(ImapSimpleAuthentication.class));
        item.setNewEntity(true);
    }

    @Override
    protected void postInit() {
        FieldGroup.FieldConfig mailBoxRootCertificateField = this.mainParams.getFieldNN("mailBoxRootCertificateField");
        mailBoxRootCertificateField.setVisible(getItem().getSecureMode() != null);
        mailBoxDs.addItemPropertyChangeListener(event -> {
            if (Objects.equals("secureMode", event.getProperty())) {
                mailBoxRootCertificateField.setVisible(event.getValue() != null);
            }
        });

        addCloseWithCommitListener(() -> {
            ImapMailBox mailBox = getItem();

            List<ImapFolder> toCommit = mailBox.getFolders().stream().filter(PersistenceHelper::isNew).collect(Collectors.toList());
            List<ImapFolder> toDelete = dm.loadList(LoadContext.create(ImapFolder.class).setQuery(
                    LoadContext.createQuery(
                            "select f from mailcomponent$ImapFolder f where f.mailBox.id = :boxId"
                    ).setParameter("boxId", mailBox))).stream()
                    .filter(f -> !mailBox.getFolders().contains(f))
                    .collect(Collectors.toList());

            if (Boolean.TRUE.equals(mailBox.getNewEntity())) {
                getDsContext().addAfterCommitListener((context, e) -> {
                    context.getCommitInstances().addAll(toCommit);
                    context.getRemoveInstances().addAll(toDelete);
                });
            } else {
                dm.commit(new CommitContext(toCommit, toDelete));
            }
        });
    }

}