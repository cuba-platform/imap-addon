package com.haulmont.components.imap.api;

import com.haulmont.components.imap.core.FolderTask;
import com.haulmont.components.imap.core.ImapHelper;
import com.haulmont.components.imap.core.MessageFunction;
import com.haulmont.components.imap.core.Task;
import com.haulmont.components.imap.dto.ImapFolderDto;
import com.haulmont.components.imap.dto.ImapMessageDto;
import com.haulmont.components.imap.entity.ImapMailBox;
import com.haulmont.components.imap.entity.ImapMessageAttachment;
import com.haulmont.components.imap.entity.ImapMessage;
import com.haulmont.components.imap.service.ImapAPIService;
import com.haulmont.cuba.core.EntityManager;
import com.haulmont.cuba.core.Persistence;
import com.haulmont.cuba.core.Transaction;
import com.haulmont.cuba.core.TypedQuery;
import com.haulmont.cuba.core.global.Metadata;
import com.haulmont.cuba.core.global.TimeSource;
import com.sun.mail.imap.IMAPFolder;
import com.sun.mail.imap.IMAPMessage;
import org.apache.commons.lang.ArrayUtils;
import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import javax.inject.Inject;
import javax.mail.*;
import javax.mail.internet.MimeUtility;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.*;
import java.util.stream.Collectors;

@Component(ImapAPI.NAME)
public class Imap implements ImapAPI {

    private final static Logger log = LoggerFactory.getLogger(ImapAPIService.class);

    @Inject
    private ImapHelper imapHelper;

    @Inject
    private Persistence persistence;

    @Inject
    private Metadata metadata;

    @Inject
    private TimeSource timeSource;

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
    public ImapMessageDto fetchMessage(ImapMessage message) throws MessagingException {
        return consumeMessage(message, nativeMessage -> {
            ImapMailBox mailBox = message.getFolder().getMailBox();

            return toDto(mailBox, message.getFolder().getName(), message.getMsgUid(), nativeMessage);

        }, "fetch and transform message");


    }

    @Override
    public Collection<ImapMessageDto> fetchMessages(Collection<ImapMessage> messages) throws MessagingException {
        List<ImapMessageDto> mailMessageDtos = new ArrayList<>(messages.size());
        Map<ImapMailBox, List<ImapMessage>> byMailBox = messages.stream().collect(Collectors.groupingBy(msg -> msg.getFolder().getMailBox()));
        byMailBox.entrySet().parallelStream().forEach(mailBoxGroup -> {
            try {
                ImapMailBox mailBox = mailBoxGroup.getKey();
                Map<String, List<ImapMessage>> byFolder = mailBoxGroup.getValue().stream().collect(Collectors.groupingBy(msg -> msg.getFolder().getName()));

                Store store = imapHelper.getStore(mailBox);
                for (Map.Entry<String, List<ImapMessage>> folderGroup : byFolder.entrySet()) {
                    String folderName = folderGroup.getKey();
                    IMAPFolder folder = (IMAPFolder) store.getFolder(folderName);
                    imapHelper.doWithFolder(
                            mailBox,
                            folder,
                            new FolderTask<>(
                                    "fetch messages",
                                    false,
                                    true,
                                    f -> {
                                        for (ImapMessage message : folderGroup.getValue()) {
                                            long uid = message.getMsgUid();
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
    public Collection<ImapMessageAttachment> fetchAttachments(UUID messageId) throws MessagingException {
        ImapMessage msg = null;
        try (Transaction tx = persistence.createTransaction()) {
            EntityManager em = persistence.getEntityManager();
            msg = em.find(ImapMessage.class, messageId, "imap-msg-full");
            if (msg == null) {
                throw new RuntimeException("Can't find msg#" + messageId);
            }
        }

        if (Boolean.TRUE.equals(msg.getAttachmentsLoaded())) {
            try (Transaction tx = persistence.createTransaction()) {
                EntityManager em = persistence.getEntityManager();
                TypedQuery<ImapMessageAttachment> query = em.createQuery(
                        "select a from imapcomponent$ImapMessageAttachment a where a.imapMessage.id = :msg",
                        ImapMessageAttachment.class
                ).setParameter("msg", messageId).setViewName("imap-msg-attachment-full");
                return query.getResultList();
            }
        }

        ImapMailBox mailBox = msg.getFolder().getMailBox();
        String folderName = msg.getFolder().getName();
        Store store = imapHelper.getStore(mailBox);
        try {
            IMAPFolder folder = (IMAPFolder) store.getFolder(folderName);
            Task<ImapMessage, Collection<ImapMessageAttachment>> task = new Task<>(
                    "extracting attachments", true, msgRef -> {
                IMAPMessage imapMsg = (IMAPMessage) folder.getMessageByUID(msgRef.getMsgUid());

                Collection<ImapMessageAttachment> attachments = makeAttachments(imapMsg);

                try (Transaction tx = persistence.createTransaction()) {
                    EntityManager em = persistence.getEntityManager();
                    attachments.forEach(it -> {
                        it.setImapMessage(msgRef);
                        em.persist(it);
                    });
                    em.createQuery("update imapcomponent$ImapMessage m set m.attachmentsLoaded = true where m.id = :msg")
                            .setParameter("msg", messageId).executeUpdate();
                    tx.commit();
                }

                return attachments;

            });
            return imapHelper.doWithMsg(msg, folder, task);
        } finally {
            store.close();
        }
    }

    private Collection<ImapMessageAttachment> makeAttachments(IMAPMessage msg) throws MessagingException {

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

        List<ImapMessageAttachment> result = new ArrayList<>();
        for (int i = 0; i < multipart.getCount(); i++) {
            BodyPart bodyPart = multipart.getBodyPart(i);
            if (!Part.ATTACHMENT.equalsIgnoreCase(bodyPart.getDisposition()) &&
                    StringUtils.isBlank(bodyPart.getFileName())) {
                continue; // dealing with attachments only
            }
            ImapMessageAttachment attachment = metadata.create(ImapMessageAttachment.class);
            String name = bodyPart.getFileName();
            try {
                name = MimeUtility.decodeText(name);
            } catch (UnsupportedEncodingException e) {
                log.warn("Can't decode name of attachment", e);
            }
            attachment.setName(name);
            attachment.setFileSize((long) bodyPart.getSize());
            attachment.setCreatedTs(timeSource.currentTimestamp());
            attachment.setOrderNumber(i);
            result.add(attachment);
        }

        return result;
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
    public void deleteMessage(ImapMessage message) throws MessagingException {
        ImapMailBox mailBox = message.getFolder().getMailBox();
        Store store = imapHelper.getStore(mailBox);
        try {

            if (mailBox.getTrashFolderName() != null) {
                doMove(message, mailBox.getTrashFolderName(), mailBox, store);
            } else {
                consumeMessage(message, msg -> {
                    msg.setFlag(Flags.Flag.DELETED, true);
                    return null;
                }, "Mark message#" + message.getMsgUid() + " as DELETED");
            }
        } finally {
            store.close();
        }
    }

    @Override
    public void moveMessage(ImapMessage msg, String folderName) throws MessagingException {
        ImapMailBox mailBox = msg.getFolder().getMailBox();
        Store store = imapHelper.getStore(mailBox);
        try {
            doMove(msg, folderName, mailBox, store);
        } finally {
            store.close();
        }

    }

    private void doMove(ImapMessage msg, String newFolderName, ImapMailBox mailBox, Store store) throws MessagingException {
        Message message = consumeMessage(msg, _msg -> _msg, "Get message#" + msg.getMsgUid());
        IMAPFolder newFolder = (IMAPFolder) store.getFolder(newFolderName);
        imapHelper.doWithFolder(
                mailBox,
                newFolder,
                new FolderTask<>(
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
    public void markAsRead(ImapMessage message) throws MessagingException {
        consumeMessage(message, msg -> {
            msg.setFlag(Flags.Flag.SEEN, true);
            return null;
        }, "Mark message#" + message.getMsgUid() + " as SEEN");
    }

    @Override
    public void markAsImportant(ImapMessage message) throws MessagingException {
        consumeMessage(message, msg -> {
            msg.setFlag(Flags.Flag.FLAGGED, true);
            return null;
        }, "Mark message#" + message.getMsgUid() + " as FLAGGED");
    }

    @Override
    public void setFlag(ImapMessage message, ImapFlag flag, boolean set) throws MessagingException {
        consumeMessage(message, msg -> {
            msg.setFlags(flag.imapFlags(), set);
            return null;
        }, "Set flag " + flag + " of message " + message.getMsgUid() + " to " + set);
    }

    private <T> T consumeMessage(ImapMessage msg, MessageFunction<IMAPMessage, T> consumer, String actionDescription) throws MessagingException {
        ImapMailBox mailBox = msg.getFolder().getMailBox();
        String folderName = msg.getFolder().getName();
        long uid = msg.getMsgUid();
        Store store = imapHelper.getStore(mailBox);
        try {

            IMAPFolder folder = (IMAPFolder) store.getFolder(folderName);
            return imapHelper.doWithFolder(
                    mailBox,
                    folder,
                    new FolderTask<>(
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
        result.setImapFolder(folder);
        result.getChildren().forEach(f -> f.setParent(result));
        return result;
    }
}
