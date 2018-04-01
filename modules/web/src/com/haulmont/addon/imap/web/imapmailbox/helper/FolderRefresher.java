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
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component(FolderRefresher.NAME)
@SuppressWarnings({"CdiInjectionPointsInspection", "SpringJavaAutowiredFieldsWarningInspection", "SpringJavaInjectionPointsAutowiringInspection"})
public class FolderRefresher {
    static final String NAME = "imapcomponent_FolderRefresher";

    private final static Logger log = LoggerFactory.getLogger(FolderRefresher.class);

    @Inject
    private ImapAPIService imapService;

    @Inject
    private Metadata metadata;

    public void refreshFolders(ImapMailBox mailBox) {
        log.info("refresh folder for {}", mailBox);
        Collection<ImapFolderDto> folderDtos = imapService.fetchFolders(mailBox);
        List<ImapFolder> folders = mailBox.getFolders();
        if (CollectionUtils.isEmpty(folders)) {
            log.debug("There is no folders for {}. Will add all from IMAP server, fully enabling folders that can contain messages", mailBox);
            mailBox.setFolders(folderDtos.stream()
                    .flatMap(dto -> folderWithChildren(dto).stream())
                    .peek(f -> {
                        if (Boolean.TRUE.equals(f.getSelectable())) {
                            enableCompletely(f);
                        }
                    }).collect(Collectors.toList())
            );
        } else {
            log.debug("There are folders for {}. Will add new from IMAP server and disable missing", mailBox);
            mergeFolders(ImapFolderDto.flattenList(folderDtos), folders);
        }
        mailBox.getFolders().forEach(f -> f.setMailBox(mailBox));
    }

    private void mergeFolders(List<ImapFolderDto> folderDtos, List<ImapFolder> folders) {
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
        log.trace("New folders:{}", newFoldersWithParent.keySet());
        log.trace("Deleted folders:{}", deletedFolders);
        List<String> folderNames = folderDtos.stream().map(ImapFolderDto::getFullName).collect(Collectors.toList());
        folders.sort(Comparator.comparingInt(f -> folderNames.indexOf(f.getName())));
    }

    private List<ImapFolder> folderWithChildren(ImapFolderDto dto) {
        return folderWithChildren(dto, null);
    }

    private List<ImapFolder> folderWithChildren(ImapFolderDto dto, ImapFolder parent) {
        log.trace("Convert dto {} to folder with children, parent of folder-{}", dto, parent);
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
        log.trace("Set {} selected and enable all events", imapFolder);
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
