package com.haulmont.addon.imap.web.imapmailbox;

import com.google.common.collect.Lists;
import com.haulmont.addon.imap.dto.ImapFolderDto;
import com.haulmont.addon.imap.entity.ImapFolderEvent;
import com.haulmont.addon.imap.entity.ImapMailBox;
import com.haulmont.addon.imap.entity.ImapFolder;
import com.haulmont.addon.imap.entity.ImapEventType;
import com.haulmont.addon.imap.web.ds.ImapFolderDatasource;
import com.haulmont.bali.util.ParamsMap;
import com.haulmont.cuba.core.global.*;
import com.haulmont.cuba.gui.components.AbstractEditor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

public class ImapMailBoxFolders extends AbstractEditor<ImapMailBox> {

    private final static Logger LOG = LoggerFactory.getLogger(ImapMailBoxFolders.class);

    @Inject
    private ImapFolderDatasource imapFolderDs;

    @Inject
    private Metadata metadata;

    @Override
    public void init(Map<String, Object> params) {
        imapFolderDs.refresh(ParamsMap.of(ImapFolderDatasource.FOLDER_DS_MAILBOX_PARAM, params.get("mailBox")));
    }

    @Override
    protected void postInit() {
        ImapMailBox mailBox = getItem();

        addCloseWithCommitListener(() -> {
            Map<String, ImapFolderDto> selected = new HashMap<>(imapFolderDs.getItems().stream()
                    .filter(ImapFolderDto::getSelected)
                    .collect(Collectors.toMap(ImapFolderDto::getFullName, Function.identity()))
            );

            List<ImapFolder> mailBoxFolders = mailBox.getFolders();
            if (mailBoxFolders == null) {
                mailBoxFolders = new ArrayList<>();
                mailBox.setFolders(mailBoxFolders);
            }
            List<ImapFolder> toDelete = new ArrayList<>(mailBoxFolders.size());
            for (ImapFolder folder : mailBoxFolders) {
                String fullName = folder.getName();
                if (!selected.containsKey(fullName)) {
                    toDelete.add(folder);
                } else {
                    selected.remove(fullName);
                }
            }

            LOG.debug("Updating folders of {}. To add: {}, to delete: {}", mailBox, selected, toDelete);

            mailBox.getFolders().addAll(selected.values().stream().map(dto -> {
                ImapFolder imapFolder = metadata.create(ImapFolder.class);
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
