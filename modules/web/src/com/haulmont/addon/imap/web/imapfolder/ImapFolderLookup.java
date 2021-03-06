/*
 * Copyright (c) 2008-2019 Haulmont.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.haulmont.addon.imap.web.imapfolder;

import com.haulmont.addon.imap.entity.ImapFolder;
import com.haulmont.addon.imap.entity.ImapMailBox;
import com.haulmont.cuba.gui.components.AbstractLookup;
import com.haulmont.cuba.gui.components.TreeTable;
import com.haulmont.cuba.gui.data.Datasource;
import com.haulmont.cuba.gui.data.HierarchicalDatasource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import java.util.*;

public class ImapFolderLookup extends AbstractLookup {

    public static final String MAILBOX_PARAM = "mailBox";

    private final static Logger log = LoggerFactory.getLogger(ImapFolderLookup.class);

    @Inject
    private Datasource<ImapMailBox> mailBoxDs;

    @Inject
    private HierarchicalDatasource<ImapFolder, UUID> imapFolderDs;

    @Inject
    private TreeTable<ImapFolder> imapFoldersTable;

    @Override
    public void init(Map<String, Object> params) {
        if (!params.containsKey(MAILBOX_PARAM) || !(params.get(MAILBOX_PARAM) instanceof ImapMailBox)) {
            throw new UnsupportedOperationException();
        }

        ImapMailBox mailBox = (ImapMailBox) params.get(MAILBOX_PARAM);
        mailBoxDs.setItem(mailBox);
        imapFolderDs.refresh();

        String trashFolderName = mailBox.getTrashFolderName();
        if (trashFolderName != null) {
            Collection<ImapFolder> items = new ArrayList<>(imapFolderDs.getItems());

            log.debug("find trash folder {} among {}", trashFolderName, items);
            items.stream()
                    .filter(f -> f.getName().equals(trashFolderName))
                    .findFirst().ifPresent(trashFolder -> imapFoldersTable.setSelected(trashFolder));

        }
    }
}
