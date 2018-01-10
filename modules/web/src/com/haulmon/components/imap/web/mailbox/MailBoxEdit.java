package com.haulmon.components.imap.web.mailbox;

import com.haulmon.components.imap.dto.FolderDto;
import com.haulmon.components.imap.entity.MailAuthenticationMethod;
import com.haulmon.components.imap.entity.MailFolder;
import com.haulmon.components.imap.entity.MailSimpleAuthentication;
import com.haulmon.components.imap.service.ImapService;
import com.haulmont.cuba.core.global.Metadata;
import com.haulmont.cuba.core.global.PersistenceHelper;
import com.haulmont.cuba.gui.components.AbstractEditor;
import com.haulmon.components.imap.entity.MailBox;
import com.haulmont.cuba.gui.components.FieldGroup;
import com.haulmont.cuba.gui.data.Datasource;

import javax.inject.Inject;
import javax.mail.MessagingException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

public class MailBoxEdit extends AbstractEditor<MailBox> {

    @Inject
    private FieldGroup mainParams;

    @Inject
    private ImapService service;

    @Inject
    private Metadata metadata;

    @Inject
    private Datasource<MailBox> mailBoxDs;

    public void checkTheConnection() {
        try {
            service.testConnection(getItem());
            showNotification("Connection succeed", NotificationType.HUMANIZED);
        } catch (Exception e) {
            showNotification("Connection failed", NotificationType.ERROR);
        }
    }

    @Override
    protected void initNewItem(MailBox item) {
        item.setAuthenticationMethod(MailAuthenticationMethod.SIMPLE);
        item.setPollInterval(10 * 60);
        item.setAuthentication(metadata.create(MailSimpleAuthentication.class));
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
    }

    @Override
    protected boolean preCommit() {

        //todo: it should be done through usage of custom add\exclude for folders using service.fetchFolders(mailBox)

        MailBox mailBox = getItem();
        try {
            List<FolderDto> folders = service.fetchFolders(mailBox);
            List<MailFolder> boxFolders = mailBox.getFolders();
            if (boxFolders == null) {
                mailBox.setFolders(new ArrayList<>(folders.size()));
            }

            if (PersistenceHelper.isNew(mailBox)) {
                MailFolder mailFolder = metadata.create(MailFolder.class);
                mailFolder.setMailBox(mailBox);
                mailFolder.setName(folders.get(0).getFullName());
                mailBox.getFolders().add(mailFolder);
                getDsContext().addBeforeCommitListener(context -> context.getCommitInstances().add(mailFolder));
            }
            /*List<String> savedFolders = mailBox.getFolders().stream()
                    .map(MailFolder::getName)
                    .collect(Collectors.toList());

            folders.stream().filter(f -> !savedFolders.contains(f)).forEach(name -> {
                MailFolder mailFolder = metadata.create(MailFolder.class);
                mailFolder.setMailBox(mailBox);
                mailFolder.setName(name);
                mailBox.getFolders().add(mailFolder);
                getDsContext().addBeforeCommitListener(context -> context.getCommitInstances().add(mailFolder));
            });*/

            return true;
        } catch (MessagingException e) {
            return false;
        }
    }
}