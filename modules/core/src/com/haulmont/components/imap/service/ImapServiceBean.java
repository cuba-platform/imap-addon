package com.haulmont.components.imap.service;

import com.haulmont.components.imap.core.ImapBase;
import com.haulmont.components.imap.dto.MailFolderDto;
import com.haulmont.components.imap.dto.MailMessageDto;
import com.haulmont.components.imap.entity.MailBox;
import com.haulmont.components.imap.entity.MailMessage;
import com.haulmont.components.imap.events.NewEmailEvent;
import com.haulmont.components.imap.core.ImapBase;
import com.haulmont.components.imap.dto.MailFolderDto;
import com.haulmont.components.imap.dto.MailMessageDto;
import com.haulmont.components.imap.entity.MailBox;
import com.haulmont.components.imap.entity.MailMessage;
import com.sun.mail.imap.IMAPFolder;
import org.springframework.stereotype.Service;

import javax.mail.*;
import java.util.*;
import java.util.stream.Collectors;

@Service(ImapService.NAME)
public class ImapServiceBean extends ImapBase implements ImapService {

    @Override
    public void testConnection(MailBox box) throws MessagingException {
        getStore(box);
    }

    @Override
    public List<MailFolderDto> fetchFolders(MailBox box) throws MessagingException {
        Store store = getStore(box);

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
        Store store = getStore(message.getMailBox());

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

        return result;
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

        if ((folder.getType() & Folder.HOLDS_FOLDERS) != 0) {
            for (Folder childFolder : folder.list()) {
                subFolders.add(map((IMAPFolder) childFolder));
            }
        }
        MailFolderDto result = new MailFolderDto(
                folder.getName(),
                folder.getFullName(),
                (folder.getType() & Folder.HOLDS_MESSAGES) != 0,
                subFolders);
        result.getChildren().forEach(f -> f.setParent(result));
        return result;
    }
}