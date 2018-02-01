package com.haulmont.components.imap.web.mailbox;

import com.haulmont.components.imap.dto.MailFolderDto;
import com.haulmont.components.imap.entity.MailBox;
import com.haulmont.components.imap.entity.MailEventType;
import com.haulmont.components.imap.entity.MailFolder;
import com.haulmont.components.imap.entity.PredefinedEventType;
import com.haulmont.components.imap.web.ds.MailFolderDatasource;
import com.haulmont.bali.util.ParamsMap;
import com.haulmont.components.imap.web.ds.MailFolderDatasource;
import com.haulmont.cuba.core.entity.Entity;
import com.haulmont.cuba.core.global.*;
import com.haulmont.cuba.gui.components.AbstractEditor;

import javax.inject.Inject;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

public class MailBoxFolders extends AbstractEditor<MailBox> {

    @Inject
    private MailFolderDatasource mailFolderDs;

    @Inject
    private DataManager dm;

    @Inject
    private Metadata metadata;

    @Override
    public void init(Map<String, Object> params) {
        MailBox mailBox = (MailBox) params.get("mailBox");
        mailFolderDs.refresh(ParamsMap.of(MailFolderDatasource.FOLDER_DS_MAILBOX_PARAM, mailBox));

        addBeforeCloseWithCloseButtonListener(event -> {
            if (PersistenceHelper.isNew(mailBox)) {
                dm.commit(new CommitContext(mailBox));
                /*toCommit.add(mailBox);
                if (mailBox.getAuthentication() != null) {
                    toCommit.add(mailBox.getAuthentication());
                }
                if (mailBox.getClientCertificate() != null) {
                    toCommit.add(mailBox.getClientCertificate());
                }
                if (mailBox.getRootCertificate() != null) {
                    toCommit.add(mailBox.getRootCertificate());
                }*/
            }
        });

        addCloseWithCommitListener(() -> {
            Map<String, MailFolderDto> selected = new HashMap<>(mailFolderDs.getItems().stream()
                    .filter(MailFolderDto::getSelected)
                    .collect(Collectors.toMap(MailFolderDto::getFullName, Function.identity()))
            );

            List<MailFolder> mailBoxFolders = mailBox.getFolders();
            if (mailBoxFolders == null) {
                mailBoxFolders = new ArrayList<>();
            }
            List<MailFolder> toDelete = new ArrayList<>(mailBoxFolders.size());
            for (MailFolder folder : mailBoxFolders) {
                String fullName = folder.getName();
                if (!selected.containsKey(fullName)) {
                    toDelete.add(folder);
                } else {
                    selected.remove(fullName);
                }
            }

            MailEventType newEmailEvent = dm.load(LoadContext.create(MailEventType.class).setQuery(
                    LoadContext.createQuery(
                            String.format("select e from mailcomponent$MailEventType e where e.eventType = '%s'", PredefinedEventType.NEW_EMAIL.getId())
                    )
            ));

            List<Entity<?>> toCommit = selected.values().stream().map(dto -> {
                MailFolder mailFolder = metadata.create(MailFolder.class);
                mailFolder.setMailBox(mailBox);
                mailFolder.setName(dto.getFullName());
                mailFolder.setEvents(Collections.singletonList(newEmailEvent));
                return mailFolder;
            }).collect(Collectors.toList());

            dm.commit(new CommitContext(toCommit, toDelete));
        });
    }

}
