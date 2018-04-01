package com.haulmont.addon.imap.web.imapmailbox.helper;

import com.haulmont.addon.imap.dto.ImapFolderDto;
import com.haulmont.addon.imap.entity.ImapEventType;
import com.haulmont.addon.imap.entity.ImapFolder;
import com.haulmont.addon.imap.entity.ImapFolderEvent;
import com.haulmont.addon.imap.entity.ImapMailBox;
import com.haulmont.addon.imap.service.ImapAPIService;
import com.haulmont.cuba.core.global.Metadata;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

import javax.inject.Inject;
import javax.mail.MessagingException;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
public class FolderRefresher {
    private final static Logger LOG = LoggerFactory.getLogger(FolderRefresher.class);

    @Inject
    private ImapAPIService imapService;

    @Inject
    private Metadata metadata;

    public boolean refreshFolders(ImapMailBox mailBox) throws MessagingException {
        LOG.info("refresh folder for {}", mailBox);
        Collection<ImapFolderDto> folderDtos = imapService.fetchFolders(mailBox);
        boolean refresh;
        List<ImapFolder> folders = mailBox.getFolders();
        if (CollectionUtils.isEmpty(folders)) {
            LOG.debug("There is no folders for {}. Will add all from IMAP server, fully enabling folders that can contain messages", mailBox);
            mailBox.setFolders(folderDtos.stream()
                    .flatMap(dto -> folderWithChildren(dto).stream())
                    .peek(f -> {
                        if (Boolean.TRUE.equals(f.getSelectable())) {
                            enableCompletely(f);
                        }
                    }).collect(Collectors.toList())
            );
            refresh = true;
        } else {
            LOG.debug("There are folders for {}. Will add new from IMAP server and disable missing", mailBox);
            refresh = mergeFolders(ImapFolderDto.flattenList(folderDtos), folders);
        }
        if (refresh) {
            mailBox.getFolders().forEach(f -> f.setMailBox(mailBox));
//            mailBox.getFolders().sort(Comparator.comparing(ImapFolder::getName));
        }
        return refresh;
    }

    private boolean mergeFolders(Collection<ImapFolderDto> folderDtos, List<ImapFolder> folders) {
        boolean refresh;
        Map<String, ImapFolderDto> dtosByNames = folderDtos.stream()
                .collect(Collectors.toMap(ImapFolderDto::getFullName, Function.identity()));
        Map<String, ImapFolder> foldersByNames = folders.stream().collect(
                Collectors.toMap(ImapFolder::getName, Function.identity()));
        Map<ImapFolder, String> newFoldersWithParent = folderDtos.stream()
                .filter(dto -> !foldersByNames.containsKey(dto.getFullName()))
                .collect(Collectors.toMap(
                        this::mapDto,
                        dto -> dto.getParent() != null ? dto.getParent().getFullName() : null)
                );
        folders.addAll(newFoldersWithParent.keySet());
        foldersByNames.putAll(newFoldersWithParent.keySet().stream().collect(
                Collectors.toMap(ImapFolder::getName, Function.identity()))
        );
        newFoldersWithParent.forEach((folder, parentName) -> {
            if (parentName != null) {
                folder.setParent(foldersByNames.get(parentName));
            }
        });
        List<ImapFolder> deletedFolders = folders.stream()
                .filter(folder -> !dtosByNames.containsKey(folder.getName()))
                .collect(Collectors.toList());
        deletedFolders.forEach(folder -> folder.setDisabled(true));
        LOG.trace("New folders:{}", newFoldersWithParent.keySet());
        LOG.trace("Deleted folders:{}", deletedFolders);
        refresh = !newFoldersWithParent.isEmpty() || !deletedFolders.isEmpty();
        return refresh;
    }

    private List<ImapFolder> folderWithChildren(ImapFolderDto dto) {
        return folderWithChildren(dto, null);
    }

    private List<ImapFolder> folderWithChildren(ImapFolderDto dto, ImapFolder parent) {
        LOG.trace("Convert dto {} to folder with children, parent of folder-{}", dto, parent);
        List<ImapFolder> result = new ArrayList<>();

        ImapFolder imapFolder = mapDto(dto);
        imapFolder.setParent(parent);

        result.add(imapFolder);
        if (!CollectionUtils.isEmpty(dto.getChildren())) {
            result.addAll(
                    dto.getChildren().stream()
                            .flatMap(child -> folderWithChildren(child, imapFolder).stream())
                            .collect(Collectors.toList())
            );
        }

        return result;
    }

    private ImapFolder mapDto(ImapFolderDto dto) {
        ImapFolder imapFolder = metadata.create(ImapFolder.class);

        imapFolder.setName(dto.getFullName());
        imapFolder.setSelectable(Boolean.TRUE.equals(dto.getCanHoldMessages()));
        return imapFolder;
    }

    private void enableCompletely(ImapFolder imapFolder) {
        LOG.trace("Set {} selected and enable all events", imapFolder);
        imapFolder.setSelected(true);

        imapFolder.setEvents(
                Arrays.stream(ImapEventType.values()).map(eventType -> {
                    ImapFolderEvent imapEvent = metadata.create(ImapFolderEvent.class);
                    imapEvent.setEvent(eventType);
                    imapEvent.setFolder(imapFolder);

                    return imapEvent;
                }).collect(Collectors.toList())
        );
    }
}
