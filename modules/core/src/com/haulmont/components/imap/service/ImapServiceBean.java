package com.haulmont.components.imap.service;

import com.haulmont.components.imap.core.ImapHelper;
import com.haulmont.components.imap.dto.MailFolderDto;
import com.haulmont.components.imap.dto.MailMessageDto;
import com.haulmont.components.imap.dto.MessageRef;
import com.haulmont.components.imap.entity.MailBox;
import com.sun.mail.imap.IMAPFolder;
import org.springframework.stereotype.Service;

import javax.inject.Inject;
import javax.mail.*;
import javax.mail.internet.MimeUtility;
import java.io.UnsupportedEncodingException;
import java.util.*;
import java.util.stream.Collectors;

@Service(ImapService.NAME)
public class ImapServiceBean implements ImapService {

    @Inject
    private ImapHelper imapHelper;

    @Override
    public void testConnection(MailBox box) throws MessagingException {
        imapHelper.getStore(box);
    }

    @Override
    public List<MailFolderDto> fetchFolders(MailBox box) throws MessagingException {
        Store store = imapHelper.getStore(box);

        List<MailFolderDto> result = new ArrayList<>();

        Folder defaultFolder = store.getDefaultFolder();

        IMAPFolder[] rootFolders = (IMAPFolder[]) defaultFolder.list();
        for (IMAPFolder folder : rootFolders) {
            result.add(map(folder));
        }


        return result;
    }

    @Override
    public MailMessageDto fetchMessage(MessageRef messageRef) throws MessagingException {
        return consumeMessage(messageRef, nativeMessage -> {
            MailBox mailBox = messageRef.getMailBox();

            MailMessageDto result = new MailMessageDto();
            result.setUid(messageRef.getUid());
            result.setFrom(getAddressList(nativeMessage.getFrom()).toString());
            result.setToList(getAddressList(nativeMessage.getRecipients(Message.RecipientType.TO)));
            result.setCcList(getAddressList(nativeMessage.getRecipients(Message.RecipientType.CC)));
            result.setBccList(getAddressList(nativeMessage.getRecipients(Message.RecipientType.BCC)));
            result.setSubject(nativeMessage.getSubject());
            result.setFlags(getFlags(nativeMessage));
            result.setMailBoxHost(mailBox.getHost());
            result.setMailBoxPort(mailBox.getPort());
            result.setDate(nativeMessage.getReceivedDate());
            result.setFolderName(messageRef.getFolderName());
            result.setMailBoxId(mailBox.getId());

            return result;
        }, "fetch and transform message");


    }

    @Override
    public List<MailMessageDto> fetchMessages(List<MessageRef> messageRefs) throws MessagingException {
        List<MailMessageDto> mailMessageDtos = new ArrayList<>(messageRefs.size());
        Map<MailBox, List<MessageRef>> byMailBox = messageRefs.stream().collect(Collectors.groupingBy(MessageRef::getMailBox));
        byMailBox.entrySet().parallelStream().forEach(mailBoxGroup -> {
            try {
                MailBox mailBox = mailBoxGroup.getKey();
                Map<String, List<MessageRef>> byFolder = mailBoxGroup.getValue().stream().collect(Collectors.groupingBy(MessageRef::getFolderName));

                Store store = imapHelper.getStore(mailBox);
                for (Map.Entry<String, List<MessageRef>> folderGroup : byFolder.entrySet()) {
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
                                        for (MessageRef messageRef : folderGroup.getValue()) {
                                            long uid = messageRef.getUid();
                                            Message nativeMessage = folder.getMessageByUID(uid);
                                            if (nativeMessage == null) {
                                                continue;
                                            }
                                            MailMessageDto dto = new MailMessageDto();
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
                                            mailMessageDtos.add(dto);
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
    public void deleteMessage(MessageRef messageRef) throws MessagingException {
        Store store = imapHelper.getStore(messageRef.getMailBox());
        try {

            if (messageRef.getMailBox().getTrashFolderName() != null) {
                Message message = consumeMessage(messageRef, msg -> msg, "Get message#" + messageRef.getUid());
                IMAPFolder trashFolder = (IMAPFolder) store.getFolder(messageRef.getMailBox().getTrashFolderName());
                imapHelper.doWithFolder(
                        messageRef.getMailBox(),
                        trashFolder,
                        new ImapHelper.FolderTask<>(
                                "copy message to trash bin",
                                false,
                                true,
                                f -> f.appendUIDMessages(new Message[] { message})
                        ));
            } else {
                consumeMessage(messageRef, msg -> {
                    msg.setFlag(Flags.Flag.DELETED, true);
                    return null;
                }, "Mark message#" + messageRef.getUid() + " as DELETED");
            }
        } finally {
            store.close();
        }
    }

    @Override
    public void markAsRead(MessageRef messageRef) throws MessagingException {
        consumeMessage(messageRef, msg -> {
            msg.setFlag(Flags.Flag.SEEN, true);
            return null;
        }, "Mark message#" + messageRef.getUid() + " as SEEN");
    }

    @Override
    public void markAsImportant(MessageRef messageRef) throws MessagingException {
        consumeMessage(messageRef, msg -> {
            msg.setFlag(Flags.Flag.FLAGGED, true);
            return null;
        }, "Mark message#" + messageRef.getUid() + " as FLAGGED");
    }

    private <T> T consumeMessage(MessageRef ref, ImapHelper.MessageFunction<Message, T> consumer, String actionDescription) throws MessagingException {
        MailBox mailBox = ref.getMailBox();
        String folderName = ref.getFolderName();
        long uid = ref.getUid();
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
                            f -> consumer.apply(folder.getMessageByUID(uid))
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

    private MailFolderDto map(IMAPFolder folder) throws MessagingException {
        List<MailFolderDto> subFolders = new ArrayList<>();

        if (imapHelper.canHoldFolders(folder)) {
            for (Folder childFolder : folder.list()) {
                subFolders.add(map((IMAPFolder) childFolder));
            }
        }
        MailFolderDto result = new MailFolderDto(
                folder.getName(),
                folder.getFullName(),
                imapHelper.canHoldMessages(folder),
                subFolders);
        result.getChildren().forEach(f -> f.setParent(result));
        return result;
    }

}