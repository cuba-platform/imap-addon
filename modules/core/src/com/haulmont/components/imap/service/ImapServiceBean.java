package com.haulmont.components.imap.service;

import com.haulmont.components.imap.core.ImapHelper;
import com.haulmont.components.imap.dto.MailFolderDto;
import com.haulmont.components.imap.dto.MailMessageDto;
import com.haulmont.components.imap.entity.MailBox;
import com.haulmont.components.imap.entity.MailMessage;
import com.sun.mail.imap.IMAPFolder;
import org.springframework.stereotype.Service;

import javax.inject.Inject;
import javax.mail.*;
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
    public MailMessageDto fetchMessage(MailMessage message) throws MessagingException {
        MailBox mailBox = message.getMailBox();
        Store store = imapHelper.getStore(mailBox);


        //todo: add getFolderMethod in imapHelper and cache it
        IMAPFolder folder = (IMAPFolder) store.getFolder(message.getFolderName());
        folder.open(Folder.READ_WRITE);
        Message nativeMessage = folder.getMessageByUID(message.getMessageUid());

        MailMessageDto result = new MailMessageDto();
        result.setFrom(Arrays.toString(nativeMessage.getFrom()));
        result.setToList(getAddressList(nativeMessage.getRecipients(Message.RecipientType.TO)));
        result.setCcList(getAddressList(nativeMessage.getRecipients(Message.RecipientType.CC)));
        result.setBccList(getAddressList(nativeMessage.getRecipients(Message.RecipientType.BCC)));
        result.setSubject(nativeMessage.getSubject());
        result.setFlags(getFlags(nativeMessage));
        result.setMailBoxHost(mailBox.getHost());
        result.setMailBoxPort(mailBox.getPort());

        return result;
    }

    @Override
    public List<MailMessageDto> fetchMessages(List<MailMessage> messages) throws MessagingException {
        List<MailMessageDto> mailMessageDtos = new ArrayList<>(messages.size());
        Map<MailBox, List<MailMessage>> byMailBox = messages.stream().collect(Collectors.groupingBy(MailMessage::getMailBox));
        byMailBox.entrySet().parallelStream().forEach(mailBoxGroup -> {
            try {
                MailBox mailBox = mailBoxGroup.getKey();
                Map<String, List<MailMessage>> byFolder = mailBoxGroup.getValue().stream().collect(Collectors.groupingBy(MailMessage::getFolderName));

                Store store = imapHelper.getStore(mailBox);
                for (Map.Entry<String, List<MailMessage>> folderGroup : byFolder.entrySet()) {
                    String folderName = folderGroup.getKey();
                    IMAPFolder folder = (IMAPFolder) store.getFolder(folderName);
                    folder.open(Folder.READ_WRITE);
                    for (MailMessage message : folderGroup.getValue()) {
                        Message nativeMessage = folder.getMessageByUID(message.getMessageUid());
                        MailMessageDto dto = new MailMessageDto();
                        dto.setFrom(Arrays.toString(nativeMessage.getFrom()));
                        dto.setToList(getAddressList(nativeMessage.getRecipients(Message.RecipientType.TO)));
                        dto.setCcList(getAddressList(nativeMessage.getRecipients(Message.RecipientType.CC)));
                        dto.setBccList(getAddressList(nativeMessage.getRecipients(Message.RecipientType.BCC)));
                        dto.setSubject(nativeMessage.getSubject());
                        dto.setFlags(getFlags(nativeMessage));
                        dto.setMailBoxHost(mailBox.getHost());
                        dto.setMailBoxPort(mailBox.getPort());
                        mailMessageDtos.add(dto);
                    }
                }
            } catch (MessagingException e) {
                throw new RuntimeException("fetch exception", e);
            }

        });

        //todo: sort dto according to messages input
        return mailMessageDtos;
    }

    private List<String> getAddressList(Address[] addresses) {
        if (addresses == null) {
            return Collections.emptyList();
        }

        return Arrays.stream(addresses).map(Object::toString).collect(Collectors.toList());
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