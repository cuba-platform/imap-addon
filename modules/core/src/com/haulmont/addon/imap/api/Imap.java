package com.haulmont.addon.imap.api;

import com.haulmont.addon.imap.core.FolderTask;
import com.haulmont.addon.imap.core.ImapHelper;
import com.haulmont.addon.imap.core.MessageFunction;
import com.haulmont.addon.imap.dto.ImapFolderDto;
import com.haulmont.addon.imap.dto.ImapMessageDto;
import com.haulmont.addon.imap.entity.ImapMailBox;
import com.haulmont.addon.imap.entity.ImapMessage;
import com.haulmont.addon.imap.entity.ImapMessageAttachment;
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
@SuppressWarnings({"CdiInjectionPointsInspection", "SpringJavaAutowiredFieldsWarningInspection"})
public class Imap implements ImapAPI {

    private final static Logger LOG = LoggerFactory.getLogger(Imap.class);

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
        LOG.debug("fetch folders for box {}", box);

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
        LOG.debug("fetch folders {} for box {}", folderNames, box);
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
    public ImapMessageDto fetchMessage(ImapMessage message) {
        LOG.debug("fetch message {}", message);

        return consumeMessage(message, nativeMessage -> {
            ImapMailBox mailBox = message.getFolder().getMailBox();

            return toDto(mailBox, message.getFolder().getName(), message.getMsgUid(), nativeMessage);

        }, "fetch and transform message");
    }

    @Override
    public Collection<ImapMessageDto> fetchMessages(Collection<ImapMessage> messages) {
        List<ImapMessageDto> mailMessageDtos = new ArrayList<>(messages.size());
        Map<ImapMailBox, List<ImapMessage>> byMailBox = messages.stream().collect(Collectors.groupingBy(msg -> msg.getFolder().getMailBox()));
        byMailBox.entrySet().parallelStream().forEach(mailBoxGroup -> {
            ImapMailBox mailBox = mailBoxGroup.getKey();
            Map<String, List<ImapMessage>> byFolder = mailBoxGroup.getValue().stream()
                    .collect(Collectors.groupingBy(msg -> msg.getFolder().getName()));

            for (Map.Entry<String, List<ImapMessage>> folderGroup : byFolder.entrySet()) {
                String folderName = folderGroup.getKey();
                imapHelper.doWithFolder(
                        mailBox,
                        folderName,
                        new FolderTask<>(
                                "fetch messages",
                                false,
                                true,
                                f -> {
                                    for (ImapMessage message : folderGroup.getValue()) {
                                        long uid = message.getMsgUid();
                                        IMAPMessage nativeMessage = (IMAPMessage) f.getMessageByUID(uid);
                                        if (nativeMessage == null) {
                                            continue;
                                        }
                                        mailMessageDtos.add(toDto(mailBox, folderName, uid, nativeMessage));
                                    }
                                    return null;
                                })
                );
            }

        });

        //todo: sort dto according to messages input
        return mailMessageDtos;
    }

    @Override
    public Collection<ImapMessageAttachment> fetchAttachments(UUID messageId) {
        LOG.info("fetch attachments for message with id {}", messageId);
        ImapMessage msg;
        try (Transaction ignored = persistence.createTransaction()) {
            EntityManager em = persistence.getEntityManager();
            msg = em.find(ImapMessage.class, messageId, "imap-msg-full");
            if (msg == null) {
                throw new RuntimeException("Can't find msg#" + messageId);
            }
        }

        if (Boolean.TRUE.equals(msg.getAttachmentsLoaded())) {
            LOG.debug("attachments for message {} were loaded, reading from database", msg);
            try (Transaction ignored = persistence.createTransaction()) {
                EntityManager em = persistence.getEntityManager();
                TypedQuery<ImapMessageAttachment> query = em.createQuery(
                        "select a from imapcomponent$ImapMessageAttachment a where a.imapMessage.id = :msg",
                        ImapMessageAttachment.class
                ).setParameter("msg", messageId).setViewName("imap-msg-attachment-full");
                return query.getResultList();
            }
        }

        LOG.debug("attachments for message {} were not loaded, reading from IMAP server and cache in database", msg);
        ImapMailBox mailBox = msg.getFolder().getMailBox();
        String folderName = msg.getFolder().getName();

        ImapMessage finalMsg = msg;
        return imapHelper.doWithFolder(mailBox, folderName, new FolderTask<>(
                        "extracting attachments", true, false, f -> {

                    IMAPMessage imapMsg = (IMAPMessage) f.getMessageByUID(finalMsg.getMsgUid());

                    Collection<ImapMessageAttachment> attachments = makeAttachments(imapMsg);

                    try (Transaction tx = persistence.createTransaction()) {
                        LOG.trace("storing {} for message {} and mark loaded", attachments, finalMsg);
                        EntityManager em = persistence.getEntityManager();
                        attachments.forEach(it -> {
                            it.setImapMessage(finalMsg);
                            em.persist(it);
                        });
                        em.createQuery("update imapcomponent$ImapMessage m set m.attachmentsLoaded = true where m.id = :msg")
                                .setParameter("msg", messageId).executeUpdate();
                        tx.commit();
                    }

                    return attachments;
                })
        );

    }

    private Collection<ImapMessageAttachment> makeAttachments(IMAPMessage msg) throws MessagingException {
        LOG.debug("make attachments for message {}", msg);

        if (!msg.getContentType().contains("multipart")) {
            return Collections.emptyList();
        }

        Multipart multipart;
        try {
            msg.setPeek(true);
            multipart = (Multipart) msg.getContent();
        } catch (IOException e) {
            LOG.warn("can't extract attachments:", e);

            return Collections.emptyList();
        }

        List<ImapMessageAttachment> result = new ArrayList<>();
        for (int i = 0; i < multipart.getCount(); i++) {
            BodyPart bodyPart = multipart.getBodyPart(i);
            if (!Part.ATTACHMENT.equalsIgnoreCase(bodyPart.getDisposition()) &&
                    StringUtils.isBlank(bodyPart.getFileName())) {
                continue; // dealing with attachments only
            }
            LOG.trace("processing attachment#{} with name {} for message {}", i, bodyPart.getFileName(), msg);
            ImapMessageAttachment attachment = metadata.create(ImapMessageAttachment.class);
            String name = bodyPart.getFileName();
            try {
                name = MimeUtility.decodeText(name);
            } catch (UnsupportedEncodingException e) {
                LOG.warn("Can't decode name of attachment", e);
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
            LOG.warn("Can't extract body:", e);
        }
        return dto;
    }

    @Override
    public void deleteMessage(ImapMessage message) {
        LOG.info("delete message {}", message);
        ImapMailBox mailBox = message.getFolder().getMailBox();

        if (mailBox.getTrashFolderName() != null) {
            doMove(message, mailBox.getTrashFolderName(), mailBox);
        } else {
            consumeMessage(message, msg -> {
                msg.setFlag(Flags.Flag.DELETED, true);
                return null;
            }, "Mark message#" + message.getMsgUid() + " as DELETED");
        }

    }

    @Override
    public void moveMessage(ImapMessage msg, String folderName) {
        LOG.info("move message {} to folder {}", msg, folderName);
        ImapMailBox mailBox = msg.getFolder().getMailBox();
        doMove(msg, folderName, mailBox);
    }

    private void doMove(ImapMessage msg, String newFolderName, ImapMailBox mailBox) {
        Message message = consumeMessage(msg, _msg -> _msg, "Get message#" + msg.getMsgUid());
        imapHelper.doWithFolder(
                mailBox,
                newFolderName,
                new FolderTask<>(
                        "move message to folder " + newFolderName,
                        false,
                        true,
                        f -> {
                            Folder folder = message.getFolder();
                            if (!folder.isOpen()) {
                                folder.open(Folder.READ_WRITE);
                            }
                            LOG.debug("[move]delete message {} from folder {}", msg, folder.getFullName());
                            folder.setFlags(new Message[]{message}, new Flags(Flags.Flag.DELETED), true);
                            LOG.debug("[move]append message {} to folder {}", msg, f.getFullName());
                            f.appendMessages(new Message[]{message});
                            folder.close(true);

                            return null;
                        }
                ));
    }

    @Override
    public void markAsRead(ImapMessage message) {
        LOG.info("mark message {} as read", message);
        consumeMessage(message, msg -> {
            msg.setFlag(Flags.Flag.SEEN, true);
            return null;
        }, "Mark message#" + message.getMsgUid() + " as SEEN");
    }

    @Override
    public void markAsImportant(ImapMessage message) {
        LOG.info("mark message {} as important", message);
        consumeMessage(message, msg -> {
            msg.setFlag(Flags.Flag.FLAGGED, true);
            return null;
        }, "Mark message#" + message.getMsgUid() + " as FLAGGED");
    }

    @Override
    public void setFlag(ImapMessage message, ImapFlag flag, boolean set) {
        LOG.info("set flag {} for message {} to {}", message, flag, set);
        consumeMessage(message, msg -> {
            msg.setFlags(flag.imapFlags(), set);
            return null;
        }, "Set flag " + flag + " of message " + message.getMsgUid() + " to " + set);
    }

    private <T> T consumeMessage(ImapMessage msg, MessageFunction<IMAPMessage, T> consumer, String actionDescription) {
        LOG.trace("perform {} on message {} ", actionDescription, msg);
        ImapMailBox mailBox = msg.getFolder().getMailBox();
        String folderName = msg.getFolder().getName();
        long uid = msg.getMsgUid();

        return imapHelper.doWithFolder(
                mailBox,
                folderName,
                new FolderTask<>(
                        actionDescription,
                        true,
                        true,
                        f -> consumer.apply((IMAPMessage) f.getMessageByUID(uid))
                )
        );
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
