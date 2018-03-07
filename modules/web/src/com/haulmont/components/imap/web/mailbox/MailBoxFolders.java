package com.haulmont.components.imap.web.mailbox;

import com.google.common.collect.Lists;
import com.haulmont.components.imap.dto.MailFolderDto;
import com.haulmont.components.imap.entity.ImapFolderEvent;
import com.haulmont.components.imap.entity.MailBox;
import com.haulmont.components.imap.entity.MailFolder;
import com.haulmont.components.imap.entity.ImapEventType;
import com.haulmont.components.imap.web.ds.MailFolderDatasource;
import com.haulmont.bali.util.ParamsMap;
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
    private Metadata metadata;

    @Override
    public void init(Map<String, Object> params) {
        mailFolderDs.refresh(ParamsMap.of(MailFolderDatasource.FOLDER_DS_MAILBOX_PARAM, params.get("mailBox")));
    }

    @Override
    protected void postInit() {
        MailBox mailBox = getItem();

        addCloseWithCommitListener(() -> {
            Map<String, MailFolderDto> selected = new HashMap<>(mailFolderDs.getItems().stream()
                    .filter(MailFolderDto::getSelected)
                    .collect(Collectors.toMap(MailFolderDto::getFullName, Function.identity()))
            );

            List<MailFolder> mailBoxFolders = mailBox.getFolders();
            if (mailBoxFolders == null) {
                mailBoxFolders = new ArrayList<>();
                mailBox.setFolders(mailBoxFolders);
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


            mailBox.getFolders().addAll(selected.values().stream().map(dto -> {
                        MailFolder imapFolder = metadata.create(MailFolder.class);
                        imapFolder.setMailBox(mailBox);
                        imapFolder.setName(dto.getFullName());

                        ImapFolderEvent newEmailEvent = metadata.create(ImapFolderEvent.class);
                        newEmailEvent.setEvent(ImapEventType.NEW_EMAIL);
                        newEmailEvent.setFolder(imapFolder);

                        imapFolder.setEvents(Lists.newArrayList(newEmailEvent));
                        return imapFolder;
                    }).collect(Collectors.toList())
            );

            mailBox.getFolders().removeAll(toDelete);
        });
    }

}
