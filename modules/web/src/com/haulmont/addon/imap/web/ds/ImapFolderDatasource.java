package com.haulmont.addon.imap.web.ds;

import com.haulmont.addon.imap.dto.ImapFolderDto;
import com.haulmont.addon.imap.entity.ImapMailBox;
import com.haulmont.addon.imap.service.ImapAPIService;
import com.haulmont.cuba.core.global.AppBeans;
import com.haulmont.cuba.gui.data.impl.CustomHierarchicalDatasource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.mail.MessagingException;
import java.util.*;

public class ImapFolderDatasource extends CustomHierarchicalDatasource<ImapFolderDto, UUID> {

    private final static Logger LOG = LoggerFactory.getLogger(ImapFolderDatasource.class);

    public static final String FOLDER_DS_MAILBOX_PARAM = "mailBox";
    private ImapAPIService service = AppBeans.get(ImapAPIService.class);

    @Override
    protected Collection<ImapFolderDto> getEntities(Map<String, Object> params) {
        ImapMailBox mailBox = (ImapMailBox) params.get(FOLDER_DS_MAILBOX_PARAM);
        if (mailBox == null) {
            throw new UnsupportedOperationException();
        }
        try {
            LOG.debug("Fetch folders for {}", mailBox);
            Collection<ImapFolderDto> rootFolders = service.fetchFolders(mailBox);

            List<ImapFolderDto> folders = ImapFolderDto.flattenList(rootFolders);

            LOG.debug("Fetch folders for {}. All folders: {}", mailBox, folders);

            return folders;
        } catch (MessagingException e) {
            throw new RuntimeException("Cannot fetch folders for mailBox " + mailBox, e);
        }

    }

}
