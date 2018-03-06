package com.haulmont.components.imap.web.mailbox;

import com.haulmont.components.imap.entity.*;
import com.haulmont.components.imap.service.ImapAPIService;
import com.haulmont.bali.util.ParamsMap;
import com.haulmont.components.imap.entity.MailAuthenticationMethod;
import com.haulmont.components.imap.entity.MailSimpleAuthentication;
import com.haulmont.cuba.core.global.*;
import com.haulmont.cuba.gui.WindowManager;
import com.haulmont.cuba.gui.components.AbstractEditor;
import com.haulmont.components.imap.entity.MailBox;
import com.haulmont.cuba.gui.components.FieldGroup;
import com.haulmont.cuba.gui.data.CollectionDatasource;
import com.haulmont.cuba.gui.data.Datasource;

import javax.inject.Inject;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

public class MailBoxEdit extends AbstractEditor<MailBox> {

    @Inject
    private FieldGroup mainParams;

    @Inject
    private ImapAPIService service;

    @Inject
    private Metadata metadata;

    @Inject
    private Datasource<MailBox> mailBoxDs;

    @Inject
    private CollectionDatasource<MailFolder, UUID> foldersDs;

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
        MailBox mailBox = getItem();
        Boolean newEntity = mailBox.getNewEntity();
        AbstractEditor selectFolders = openEditor(
                "mailcomponent$MailBox.trashFolder",
                mailBox,
                WindowManager.OpenType.THIS_TAB,
                ParamsMap.of("mailBox", mailBox),
                mailBoxDs
        );
        selectFolders.addCloseWithCommitListener(() -> getItem().setNewEntity(newEntity));
    }

    public void selectFolders() {
        MailBox mailBox = getItem();
        Boolean newEntity = mailBox.getNewEntity();
        AbstractEditor selectFolders = openEditor(
                "mailcomponent$MailBox.folders",
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
    protected void initNewItem(MailBox item) {
        item.setAuthenticationMethod(MailAuthenticationMethod.SIMPLE);
        item.setPollInterval(10 * 60);
        item.setAuthentication(metadata.create(MailSimpleAuthentication.class));
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
            MailBox mailBox = getItem();


            List<MailFolder> toCommit = mailBox.getFolders().stream().filter(PersistenceHelper::isNew).collect(Collectors.toList());
            List<MailFolder> toDelete = dm.loadList(LoadContext.create(MailFolder.class).setQuery(
                    LoadContext.createQuery(
                            "select f from mailcomponent$MailFolder f where f.mailBox.id = :boxId"
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