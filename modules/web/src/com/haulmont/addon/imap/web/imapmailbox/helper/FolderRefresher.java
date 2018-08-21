package com.haulmont.addon.imap.web.imapmailbox.helper;

import com.haulmont.addon.imap.dto.ImapConnectResult;
import com.haulmont.addon.imap.dto.ImapFolderDto;
import com.haulmont.addon.imap.entity.ImapEventType;
import com.haulmont.addon.imap.entity.ImapFolder;
import com.haulmont.addon.imap.entity.ImapFolderEvent;
import com.haulmont.addon.imap.entity.ImapMailBox;
import com.haulmont.addon.imap.exception.ImapException;
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
public class FolderRefresher {
    @SuppressWarnings("WeakerAccess")
    static final String NAME = "imap_FolderRefresher";

    private final static Logger log = LoggerFactory.getLogger(FolderRefresher.class);

    private final ImapAPIService imapService;
    private final Metadata metadata;

    @SuppressWarnings("CdiInjectionPointsInspection")
    @Inject
    public FolderRefresher(@SuppressWarnings("SpringJavaInjectionPointsAutowiringInspection") ImapAPIService imapService,
                           @SuppressWarnings("SpringJavaInjectionPointsAutowiringInspection") Metadata metadata) {
        this.imapService = imapService;
        this.metadata = metadata;
    }

    public enum State {
        DELETED, NEW, UNCHANGED
    }

    public LinkedHashMap<ImapFolder, State> refreshFolders(ImapMailBox mailBox) {
        log.info("refresh folders for {}", mailBox);
//        imapService.testConnection(mailBox);
        ImapConnectResult connectResultDto = imapService.testConnection(mailBox);
        if (!connectResultDto.isSuccess()) {
            throw connectResultDto.getFailure() != null ? connectResultDto.getFailure() : new ImapException("Cannot connect to the server");
        }
        Collection<ImapFolderDto> folderDtos = connectResultDto.getAllFolders();
        mailBox.setFlagsSupported(connectResultDto.isCustomFlagSupported());
        List<ImapFolder> folders = mailBox.getFolders();
        LinkedHashMap<ImapFolder, State> result;
        if (CollectionUtils.isEmpty(folders)) {
            log.debug("There is no folders for {}. Will add all from IMAP server, fully enabling folders that can contain messages", mailBox);
            result = new LinkedHashMap<>();
            folderDtos.stream()
                    .flatMap(dto -> folderWithChildren(dto).stream())
                    .peek(f -> {
                        if (Boolean.TRUE.equals(f.getSelectable())) {
                            enableCompletely(f);
                        }
                    }).forEach(folder -> result.put(folder, State.UNCHANGED));
        } else {
            log.debug("There are folders for {}. Will add new from IMAP server and disable missing", mailBox);
            result = mergeFolders(ImapFolderDto.flattenList(folderDtos), folders);
        }
        for (ImapFolder folder : result.keySet()) {
            folder.setMailBox(mailBox);
        }

        return result;
    }

    private LinkedHashMap<ImapFolder, State> mergeFolders(List<ImapFolderDto> folderDtos, List<ImapFolder> folders) {
        Map<String, ImapFolderDto> dtosByNames = folderDtos.stream()
                .collect(Collectors.toMap(ImapFolderDto::getFullName, Function.identity()));
        Map<String, ImapFolder> foldersByNames = folders.stream().collect(
                Collectors.toMap(ImapFolder::getName, Function.identity()));
        Map<ImapFolder, String> newFoldersWithParent = new HashMap<>(folderDtos.size());
        folderDtos.stream()
                .filter(dto -> !foldersByNames.containsKey(dto.getFullName()))
                .forEach(dto -> newFoldersWithParent.put(
                        mapDto(dto),
                        dto.getParent() != null ? dto.getParent().getFullName() : null)
                );
        List<ImapFolder> resultList = new ArrayList<>(folders.size() + newFoldersWithParent.size());
        resultList.addAll(folders);
        resultList.addAll(newFoldersWithParent.keySet());
        foldersByNames.putAll(newFoldersWithParent.keySet().stream().collect(
                Collectors.toMap(ImapFolder::getName, Function.identity()))
        );
        newFoldersWithParent.forEach((folder, parentName) -> {
            if (parentName != null) {
                folder.setParent(foldersByNames.get(parentName));
            }
        });
        List<ImapFolder> deletedFolders = resultList.stream()
                .filter(folder -> !dtosByNames.containsKey(folder.getName()))
                .collect(Collectors.toList());
        log.trace("New folders:{}", newFoldersWithParent.keySet());
        log.trace("Deleted folders:{}", deletedFolders);
        List<String> folderNames = folderDtos.stream().map(ImapFolderDto::getFullName).collect(Collectors.toList());
        resultList.sort(Comparator.comparingInt(f -> folderNames.indexOf(f.getName())));

        LinkedHashMap<ImapFolder, State> result = new LinkedHashMap<>(resultList.size());
        for (ImapFolder folder : resultList) {
            result.put(
                    folder,
                    newFoldersWithParent.containsKey(folder)
                            ? State.NEW
                            : (deletedFolders.contains(folder) ? State.DELETED : State.UNCHANGED)
            );
        }

        return result;
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
