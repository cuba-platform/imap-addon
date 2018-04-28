package com.haulmont.addon.imap.api;

import com.haulmont.addon.imap.config.ImapConfig;
import com.haulmont.addon.imap.core.ImapHelper;
import com.haulmont.addon.imap.core.MessageFunction;
import com.haulmont.addon.imap.core.Task;
import com.haulmont.addon.imap.dao.ImapDao;
import com.haulmont.addon.imap.dto.ImapFolderDto;
import com.haulmont.addon.imap.dto.ImapMessageDto;
import com.haulmont.addon.imap.entity.ImapMailBox;
import com.haulmont.addon.imap.entity.ImapMessage;
import com.haulmont.addon.imap.entity.ImapMessageAttachment;
import com.haulmont.addon.imap.exception.ImapException;
import com.haulmont.cuba.core.global.Metadata;
import com.haulmont.cuba.core.global.TimeSource;
import com.haulmont.cuba.core.sys.AppContext;
import com.sun.mail.imap.IMAPFolder;
import com.sun.mail.imap.IMAPMessage;
import org.apache.commons.lang.ArrayUtils;
import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import javax.annotation.Nonnull;
import javax.annotation.PostConstruct;
import javax.inject.Inject;
import javax.mail.*;
import javax.mail.internet.MimeUtility;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

@Component(ImapAPI.NAME)
public class Imap implements ImapAPI, AppContext.Listener {

    private final static Logger log = LoggerFactory.getLogger(Imap.class);

    private final ImapHelper imapHelper;
    private final ImapDao dao;
    private final Metadata metadata;
    private final TimeSource timeSource;
    private final ImapConfig imapConfig;
    private ExecutorService fetchMessagesExecutor;

    @SuppressWarnings("CdiInjectionPointsInspection")
    @Inject
    public Imap(ImapHelper imapHelper, ImapDao dao, Metadata metadata, TimeSource timeSource, ImapConfig imapConfig) {
        this.imapHelper = imapHelper;
        this.dao = dao;
        this.metadata = metadata;
        this.timeSource = timeSource;
        this.imapConfig = imapConfig;
    }

    @PostConstruct
    public void setupExecutor() {
        AppContext.addListener(this);

    }

    @Override
    public void applicationStarted() {
        fetchMessagesExecutor = Executors.newFixedThreadPool(
                imapConfig.getFetchMessagesMaxParallelism(),
                new ThreadFactory() {
                    private final AtomicInteger threadNumber = new AtomicInteger(1);

                    @Override
                    public Thread newThread(@Nonnull Runnable r) {
                        Thread thread = new Thread(r, "ImapFetchMessages-" + threadNumber.getAndIncrement());
                        thread.setDaemon(true);
                        return thread;
                    }
                }
        );
    }

    @Override
    public void applicationStopped() {
        try {
            fetchMessagesExecutor.shutdownNow();
        } catch (Exception e) {
            log.warn("Exception while shutting down executor for fetching messages", e);
        }
    }

    @Override
    public Collection<ImapFolderDto> fetchFolders(ImapMailBox box) {
        log.debug("fetch folders for box {}", box);

        try {
            Store store = imapHelper.getStore(box, true);

            List<ImapFolderDto> result = new ArrayList<>();

            Folder defaultFolder = store.getDefaultFolder();

            IMAPFolder[] rootFolders = (IMAPFolder[]) defaultFolder.list();
            for (IMAPFolder folder : rootFolders) {
                result.add(map(folder));
            }

            return result;
        } catch (MessagingException e) {
            throw new ImapException(e);
        }
    }

    @Override
    public Collection<ImapFolderDto> fetchFolders(ImapMailBox box, String... folderNames) {
        log.debug("fetch folders {} for box {}", folderNames, box);

        Collection<ImapFolderDto> allFolders = ImapFolderDto.flattenList(fetchFolders(box));
        for (ImapFolderDto allFolder : allFolders) {
            allFolder.setParent(null);
            allFolder.setChildren(Collections.emptyList());
        }
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
        log.debug("fetch message {}", message);

        return consumeMessage(message, nativeMessage -> {
            ImapMailBox mailBox = message.getFolder().getMailBox();

            return toDto(mailBox, message.getFolder().getName(), message.getMsgUid(), nativeMessage);

        }, "fetch and transform message");
    }

    @Override
    public Collection<ImapMessageDto> fetchMessages(Collection<ImapMessage> messages) {
        List<ImapMessageDto> mailMessageDtos = new ArrayList<>(messages.size());
        Map<ImapMailBox, List<ImapMessage>> byMailBox = messages.stream().collect(Collectors.groupingBy(msg -> msg.getFolder().getMailBox()));
        Collection<Future<?>> futures = new ArrayList<>(byMailBox.size());
        byMailBox.forEach((mailBox, value) -> futures.add(
                fetchMessagesExecutor.submit(() -> {
                    Map<String, List<ImapMessage>> byFolder = value.stream()
                            .collect(Collectors.groupingBy(msg -> msg.getFolder().getName()));

                    for (Map.Entry<String, List<ImapMessage>> folderGroup : byFolder.entrySet()) {
                        String folderName = folderGroup.getKey();
                        imapHelper.doWithFolder(
                                mailBox,
                                folderName,
                                new Task<>(
                                        "fetch messages",
                                        false,
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
                })
        ));

        try {
            for (Future<?> future : futures) {
                future.get(1, TimeUnit.MINUTES);
            }
        } catch (InterruptedException e) {
            log.info("Fetching of messages was interrupted");
            for (Future<?> task : futures) {
                if (!task.isDone() && !task.isCancelled()) {
                    task.cancel(true);
                }
            }
            Thread.currentThread().interrupt();
        } catch (ExecutionException | TimeoutException e) {
            throw new RuntimeException("Can't fetch messages", e);
        }

        //todo: sort dto according to messages input
        return mailMessageDtos;
    }

    @Override
    public Collection<ImapMessageAttachment> fetchAttachments(ImapMessage message) {
        log.info("fetch attachments for message {}", message);
        ImapMessage msg = dao.findMessageById(message.getId());
        if (msg == null) {
            throw new RuntimeException("Can't find msg#" + message.getId());
        }

        if (Boolean.TRUE.equals(msg.getAttachmentsLoaded())) {
            log.debug("attachments for message {} were loaded, reading from database", msg);
            return dao.findAttachments(message.getId());
        }

        log.debug("attachments for message {} were not loaded, reading from IMAP server and cache in database", msg);
        ImapMailBox mailBox = msg.getFolder().getMailBox();
        String folderName = msg.getFolder().getName();

        return imapHelper.doWithFolder(mailBox, folderName, new Task<>(
                        "extracting attachments", true, f -> {

                    IMAPMessage imapMsg = (IMAPMessage) f.getMessageByUID(msg.getMsgUid());
                    Collection<ImapMessageAttachment> attachments = makeAttachments(imapMsg);
                    dao.saveAttachments(msg, attachments);

                    return attachments;
                })
        );

    }

    private Collection<ImapMessageAttachment> makeAttachments(IMAPMessage msg) throws MessagingException {
        log.debug("make attachments for message {}", msg);

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
            log.trace("processing attachment#{} with name {} for message {}", i, bodyPart.getFileName(), msg);
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
        ImapMessageDto dto = metadata.create(ImapMessageDto.class);
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
    public void deleteMessage(ImapMessage message) {
        log.info("delete message {}", message);
        ImapMailBox mailBox = message.getFolder().getMailBox();

        if (mailBox.getTrashFolderName() != null) {
            doMove(message, mailBox.getTrashFolderName(), mailBox);
        } else {
            consumeMessage(message, msg -> {
                msg.setFlag(Flags.Flag.DELETED, true);
                msg.getFolder().close(true);
                return null;
            }, "Mark message#" + message.getMsgUid() + " as DELETED");
        }

    }

    @Override
    public void moveMessage(ImapMessage msg, String folderName) {
        log.info("move message {} to folder {}", msg, folderName);
        ImapMailBox mailBox = msg.getFolder().getMailBox();
        doMove(msg, folderName, mailBox);
    }

    private void doMove(ImapMessage msg, String newFolderName, ImapMailBox mailBox) {
        Message message = consumeMessage(msg, _msg -> _msg, "Get message#" + msg.getMsgUid());
        imapHelper.doWithFolder(
                mailBox,
                newFolderName,
                new Task<>(
                        "move message to folder " + newFolderName,
                        false,
                        f -> {
                            Folder folder = message.getFolder();
                            if (!folder.isOpen()) {
                                folder.open(Folder.READ_WRITE);
                            }
                            log.debug("[move]delete message {} from folder {}", msg, folder.getFullName());
                            folder.setFlags(new Message[]{message}, new Flags(Flags.Flag.DELETED), true);
                            log.debug("[move]append message {} to folder {}", msg, f.getFullName());
                            f.appendMessages(new Message[]{message});
                            folder.close(true);
                            f.close(false);

                            return null;
                        }
                ));
    }

    @Override
    public void markAsRead(ImapMessage message) {
        log.info("mark message {} as read", message);
        consumeMessage(message, msg -> {
            msg.setFlag(Flags.Flag.SEEN, true);
            return null;
        }, "Mark message#" + message.getMsgUid() + " as SEEN");
    }

    @Override
    public void markAsImportant(ImapMessage message) {
        log.info("mark message {} as important", message);
        consumeMessage(message, msg -> {
            msg.setFlag(Flags.Flag.FLAGGED, true);
            return null;
        }, "Mark message#" + message.getMsgUid() + " as FLAGGED");
    }

    @Override
    public void setFlag(ImapMessage message, ImapFlag flag, boolean set) {
        log.info("set flag {} for message {} to {}", message, flag, set);
        consumeMessage(message, msg -> {
            msg.setFlags(flag.imapFlags(), set);
            return null;
        }, "Set flag " + flag + " of message " + message.getMsgUid() + " to " + set);
    }

    private <T> T consumeMessage(ImapMessage msg, MessageFunction<IMAPMessage, T> consumer, String actionDescription) {
        log.trace("perform {} on message {} ", actionDescription, msg);
        ImapMailBox mailBox = msg.getFolder().getMailBox();
        String folderName = msg.getFolder().getName();
        long uid = msg.getMsgUid();

        return imapHelper.doWithFolder(
                mailBox,
                folderName,
                new Task<>(
                        actionDescription,
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
        ImapFolderDto result = metadata.create(ImapFolderDto.class);
        result.setName(folder.getName());
        result.setFullName(folder.getFullName());
        result.setCanHoldMessages(imapHelper.canHoldMessages(folder));
        result.setChildren(subFolders);
        result.setImapFolder(folder);
        for (ImapFolderDto subFolder : result.getChildren()) {
            subFolder.setParent(result) ;
        }
        return result;
    }
}
