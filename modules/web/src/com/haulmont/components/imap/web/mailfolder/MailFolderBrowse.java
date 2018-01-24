package com.haulmont.components.imap.web.mailfolder;

import com.haulmont.components.imap.dto.MailFolderDto;
import com.haulmont.components.imap.entity.MailBox;
import com.haulmont.components.imap.entity.MailEventType;
import com.haulmont.components.imap.entity.MailFolder;
import com.haulmont.components.imap.entity.PredefinedEventType;
import com.haulmont.components.imap.web.ds.MailFolderDatasource;
import com.haulmont.bali.util.ParamsMap;
import com.haulmont.components.imap.web.ds.MailFolderDatasource;
import com.haulmont.cuba.core.entity.Entity;
import com.haulmont.cuba.core.global.CommitContext;
import com.haulmont.cuba.core.global.DataManager;
import com.haulmont.cuba.core.global.LoadContext;
import com.haulmont.cuba.core.global.Metadata;
import com.haulmont.cuba.gui.components.AbstractEditor;

import javax.inject.Inject;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

public class MailFolderBrowse extends AbstractEditor<MailBox> {

    @Inject
    private MailFolderDatasource mailFolderDs;

    @Inject
    private DataManager dm;

    @Inject
    private Metadata metadata;

    @Override
    public void init(Map<String, Object> params) {
        MailBox mailBox = (MailBox) params.get("mailBox");
        mailFolderDs.refresh(ParamsMap.of("mail-box", mailBox));

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
                            String.format("select e from mailcomponent$MailEventType e where e.name = '%s'", PredefinedEventType.NEW_EMAIL.name())
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
