package com.haulmont.components.imap.service;

import com.haulmont.components.imap.core.ImapHelper;
import com.haulmont.components.imap.dto.ImapFolderDto;
import com.haulmont.components.imap.dto.ImapMessageDto;
import com.haulmont.components.imap.entity.ImapMailBox;
import com.haulmont.components.imap.entity.ImapMessageRef;
import com.haulmont.cuba.core.app.FileStorageAPI;
import com.haulmont.cuba.core.entity.FileDescriptor;
import com.haulmont.cuba.core.global.FileStorageException;
import com.haulmont.cuba.core.global.Metadata;
import com.haulmont.cuba.core.global.TimeSource;
import com.sun.mail.imap.IMAPFolder;
import com.sun.mail.imap.IMAPMessage;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.lang.ArrayUtils;
import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import javax.inject.Inject;
import javax.mail.*;
import javax.mail.internet.MimeUtility;
import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.util.*;
import java.util.stream.Collectors;

@Service(ImapAPIService.NAME)
public class ImapAPIServiceBean implements ImapAPIService {

    private final static Logger log = LoggerFactory.getLogger(ImapAPIService.class);


    @Inject
    private ImapHelper imapHelper;

    @Inject
    private FileStorageAPI fileStorageAPI;

    @Inject
    private Metadata metadata;

    @Inject
    private TimeSource timeSource;

    @Override
    public void testConnection(ImapMailBox box) throws MessagingException {
        imapHelper.getStore(box);
    }

    @Override
    public Collection<ImapFolderDto> fetchFolders(ImapMailBox box) throws MessagingException {
        Store store = imapHelper.getStore(box);

        List<ImapFolderDto> result = new ArrayList<>();

        Folder defaultFolder = store.getDefaultFolder();

        IMAPFolder[] rootFolders = (IMAPFolder[]) defaultFolder.list();
        for (IMAPFolder folder : rootFolders) {
            result.add(map(folder));
        }


        return result;
    }

    @Override
    public Collection<ImapFolderDto> fetchFolders(ImapMailBox box, String... folderNames) throws MessagingException {
        //todo: this method can be optimised to hit IMAP server only when necessary

        Collection<ImapFolderDto> allFolders = fetchFolders(box);
        if (ArrayUtils.isEmpty(folderNames)) {
            return allFolders;
        }

        Arrays.sort(folderNames);

        return allFolders.stream()
                .filter(f -> Arrays.binarySearch(folderNames, f.getFullName()) >= 0)
                .collect(Collectors.toList());
    }

    @Override
    public ImapMessageDto fetchMessage(ImapMessageRef messageRef) throws MessagingException {
        return consumeMessage(messageRef, nativeMessage -> {
            ImapMailBox mailBox = messageRef.getFolder().getMailBox();

            return toDto(mailBox, messageRef.getFolder().getName(), messageRef.getMsgUid(), nativeMessage);

        }, "fetch and transform message");


    }

    @Override
    public Collection<ImapMessageDto> fetchMessages(Collection<ImapMessageRef> messageRefs) throws MessagingException {
        List<ImapMessageDto> mailMessageDtos = new ArrayList<>(messageRefs.size());
        Map<ImapMailBox, List<ImapMessageRef>> byMailBox = messageRefs.stream().collect(Collectors.groupingBy(msg -> msg.getFolder().getMailBox()));
        byMailBox.entrySet().parallelStream().forEach(mailBoxGroup -> {
            try {
                ImapMailBox mailBox = mailBoxGroup.getKey();
                Map<String, List<ImapMessageRef>> byFolder = mailBoxGroup.getValue().stream().collect(Collectors.groupingBy(msg -> msg.getFolder().getName()));

                Store store = imapHelper.getStore(mailBox);
                for (Map.Entry<String, List<ImapMessageRef>> folderGroup : byFolder.entrySet()) {
                    String folderName = folderGroup.getKey();
                    IMAPFolder folder = (IMAPFolder) store.getFolder(folderName);
                    imapHelper.doWithFolder(
                            mailBox,
                            folder,
                            new ImapHelper.FolderTask<>(
                                    "fetch messages",
                                    false,
                                    true,
                                    f -> {
                                        for (ImapMessageRef messageRef : folderGroup.getValue()) {
                                            long uid = messageRef.getMsgUid();
                                            IMAPMessage nativeMessage = (IMAPMessage) folder.getMessageByUID(uid);
                                            if (nativeMessage == null) {
                                                continue;
                                            }
                                            mailMessageDtos.add(toDto(mailBox, folderName, uid, nativeMessage));
                                        }
                                        return null;
                                    })
                            );
                }
            } catch (MessagingException e) {
                throw new RuntimeException("fetch exception", e);
            }

        });

        //todo: sort dto according to messages input
        return mailMessageDtos;
    }

    @Override
    public Collection<FileDescriptor> fetchAttachments(ImapMessageRef ref) throws MessagingException {
        return consumeMessage(ref, msg -> {
            if (!msg.getContentType().contains("multipart")) {
                return Collections.emptyList();
            }

            Multipart multipart;
            try {
                msg.setPeek(true);
                multipart = (Multipart) msg.getContent();
            } catch (IOException e) {
                log.warn("can't extract attachments:", e);

                return Collections.emptyList();
            }

            List<FileDescriptor> result = new ArrayList<>();
            for (int i = 0; i < multipart.getCount(); i++) {
                BodyPart bodyPart = multipart.getBodyPart(i);
                if (!Part.ATTACHMENT.equalsIgnoreCase(bodyPart.getDisposition()) &&
                        StringUtils.isBlank(bodyPart.getFileName())) {
                    continue; // dealing with attachments only
                }
                try (InputStream is = bodyPart.getInputStream()) {
                    FileDescriptor fileDescriptor = metadata.create(FileDescriptor.class);
                    fileDescriptor.setName(bodyPart.getFileName());
                    fileDescriptor.setSize((long) bodyPart.getSize());
                    fileDescriptor.setExtension(FilenameUtils.getExtension(bodyPart.getFileName()));
                    fileDescriptor.setCreateDate(timeSource.currentTimestamp());
                    fileStorageAPI.saveStream(fileDescriptor, is);
                    result.add(fileDescriptor);
                } catch (IOException | FileStorageException e) {
                    log.warn("can't extract attachment#" + i, e);
                }
            }

            return result;
        }, "extracting attachments");
    }

    private ImapMessageDto toDto(ImapMailBox mailBox, String folderName, long uid, IMAPMessage nativeMessage) throws MessagingException {
        ImapMessageDto dto = new ImapMessageDto();
        dto.setUid(uid);
        dto.setFrom(getAddressList(nativeMessage.getFrom()).toString());
        dto.setToList(getAddressList(nativeMessage.getRecipients(Message.RecipientType.TO)));
        dto.setCcList(getAddressList(nativeMessage.getRecipients(Message.RecipientType.CC)));
        dto.setBccList(getAddressList(nativeMessage.getRecipients(Message.RecipientType.BCC)));
        dto.setSubject(nativeMessage.getSubject());
        dto.setFlags(getFlags(nativeMessage));
        dto.setMailBoxHost(mailBox.getHost());
        dto.setMailBoxPort(mailBox.getPort());
        dto.setDate(nativeMessage.getReceivedDate());
        dto.setFolderName(folderName);
        dto.setMailBoxId(mailBox.getId());
        try {
            nativeMessage.setPeek(true);
            ImapHelper.Body body = imapHelper.getText(nativeMessage);
            dto.setBody(body.getText());
            dto.setHtml(body.isHtml());
        } catch (IOException e) {
            log.warn("Can't extract body:", e);
        }
        return dto;
    }

    @Override
    public void deleteMessage(ImapMessageRef messageRef) throws MessagingException {
        ImapMailBox mailBox = messageRef.getFolder().getMailBox();
        Store store = imapHelper.getStore(mailBox);
        try {

            if (mailBox.getTrashFolderName() != null) {
                doMove(messageRef, mailBox.getTrashFolderName(), mailBox, store);
            } else {
                consumeMessage(messageRef, msg -> {
                    msg.setFlag(Flags.Flag.DELETED, true);
                    return null;
                }, "Mark message#" + messageRef.getMsgUid() + " as DELETED");
            }
        } finally {
            store.close();
        }
    }

    @Override
    public void moveMessage(ImapMessageRef ref, String folderName) throws MessagingException {
        ImapMailBox mailBox = ref.getFolder().getMailBox();
        Store store = imapHelper.getStore(mailBox);
        try {
            doMove(ref, folderName, mailBox, store);
        } finally {
            store.close();
        }

    }

    private void doMove(ImapMessageRef ref, String newFolderName, ImapMailBox mailBox, Store store) throws MessagingException {
        Message message = consumeMessage(ref, msg -> msg, "Get message#" + ref.getMsgUid());
        IMAPFolder newFolder = (IMAPFolder) store.getFolder(newFolderName);
        imapHelper.doWithFolder(
                mailBox,
                newFolder,
                new ImapHelper.FolderTask<>(
                        "move message to folder " + newFolderName,
                        false,
                        true,
                        f -> {
                            Folder folder = message.getFolder();
                            if (!folder.isOpen()) {
                                folder.open(Folder.READ_WRITE);
                            }
                            folder.setFlags(new Message[]{message}, new Flags(Flags.Flag.DELETED), true);
                            f.appendMessages(new Message[]{message});
                            folder.close(true);

                            return null;
                        }
                ));
    }

    @Override
    public void markAsRead(ImapMessageRef messageRef) throws MessagingException {
        consumeMessage(messageRef, msg -> {
            msg.setFlag(Flags.Flag.SEEN, true);
            return null;
        }, "Mark message#" + messageRef.getMsgUid() + " as SEEN");
    }

    @Override
    public void markAsImportant(ImapMessageRef messageRef) throws MessagingException {
        consumeMessage(messageRef, msg -> {
            msg.setFlag(Flags.Flag.FLAGGED, true);
            return null;
        }, "Mark message#" + messageRef.getMsgUid() + " as FLAGGED");
    }

    @Override
    public void setFlag(ImapMessageRef messageRef, ImapFlag flag, boolean set) throws MessagingException {
        consumeMessage(messageRef, msg -> {
            msg.setFlags(flag.getFlags(), set);
            return null;
        }, "Set flag " + flag + " of message " + messageRef.getMsgUid() + " to " + set);
    }

    private <T> T consumeMessage(ImapMessageRef ref, ImapHelper.MessageFunction<IMAPMessage, T> consumer, String actionDescription) throws MessagingException {
        ImapMailBox mailBox = ref.getFolder().getMailBox();
        String folderName = ref.getFolder().getName();
        long uid = ref.getMsgUid();
        Store store = imapHelper.getStore(mailBox);
        try {

            IMAPFolder folder = (IMAPFolder) store.getFolder(folderName);
            return imapHelper.doWithFolder(
                    mailBox,
                    folder,
                    new ImapHelper.FolderTask<>(
                            actionDescription,
                            true,
                            true,
                            f -> consumer.apply((IMAPMessage) folder.getMessageByUID(uid))
                    )
            );
        } finally {
            store.close();
        }
    }

    private List<String> getAddressList(Address[] addresses) {
        if (addresses == null) {
            return Collections.emptyList();
        }

        return Arrays.stream(addresses)
                .map(Object::toString)
                .map(addr -> {
                    try {
                        return MimeUtility.decodeText(addr);
                    } catch (UnsupportedEncodingException e) {
                        return addr;
                    }
                }).collect(Collectors.toList());
    }

    private List<String> getFlags(Message message) throws MessagingException {
        Flags flags = message.getFlags();
        List<String> flagNames = new ArrayList<>();
        if (flags.contains(Flags.Flag.ANSWERED)) {
            flagNames.add("ANSWERED");
        }
        if (flags.contains(Flags.Flag.DELETED)) {
            flagNames.add("DELETED");
        }
        if (flags.contains(Flags.Flag.DRAFT)) {
            flagNames.add("DRAFT");
        }
        if (flags.contains(Flags.Flag.FLAGGED)) {
            flagNames.add("FLAGGED");
        }
        if (flags.contains(Flags.Flag.RECENT)) {
            flagNames.add("RECENT");
        }
        if (flags.contains(Flags.Flag.SEEN)) {
            flagNames.add("SEEN");
        }
        if (flags.contains(Flags.Flag.USER)) {
            flagNames.add("USER");
        }
        if (flags.getUserFlags() != null) {
            Collections.addAll(flagNames, flags.getUserFlags());
        }

        return flagNames;
    }

    private ImapFolderDto map(IMAPFolder folder) throws MessagingException {
        List<ImapFolderDto> subFolders = new ArrayList<>();

        if (imapHelper.canHoldFolders(folder)) {
            for (Folder childFolder : folder.list()) {
                subFolders.add(map((IMAPFolder) childFolder));
            }
        }
        ImapFolderDto result = new ImapFolderDto(
                folder.getName(),
                folder.getFullName(),
                imapHelper.canHoldMessages(folder),
                subFolders);
        result.getChildren().forEach(f -> f.setParent(result));
        return result;
    }

}