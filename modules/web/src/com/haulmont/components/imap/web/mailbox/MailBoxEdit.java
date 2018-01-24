package com.haulmont.components.imap.web.mailbox;

import com.haulmont.components.imap.entity.MailAuthenticationMethod;
import com.haulmont.components.imap.entity.MailSimpleAuthentication;
import com.haulmont.components.imap.service.ImapService;
import com.haulmont.bali.util.ParamsMap;
import com.haulmont.components.imap.entity.MailAuthenticationMethod;
import com.haulmont.components.imap.entity.MailBox;
import com.haulmont.components.imap.entity.MailSimpleAuthentication;
import com.haulmont.cuba.core.global.Metadata;
import com.haulmont.cuba.gui.WindowManager;
import com.haulmont.cuba.gui.components.AbstractEditor;
import com.haulmont.components.imap.entity.MailBox;
import com.haulmont.cuba.gui.components.FieldGroup;
import com.haulmont.cuba.gui.data.Datasource;

import javax.inject.Inject;
import java.util.Objects;

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

    public void selectFolders() {
        AbstractEditor selectFolders = openEditor(
                "mailcomponent$MailFolder.browse",
                getItem(),
                WindowManager.OpenType.THIS_TAB,
                ParamsMap.of("mailBox", getItem())
        );
        selectFolders.addCloseWithCommitListener(() -> mailBoxDs.refresh());
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

}