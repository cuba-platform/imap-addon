package com.haulmont.addon.imap.api;

import com.haulmont.addon.imap.config.ImapConfig;
import com.haulmont.addon.imap.core.ImapHelper;
import com.haulmont.addon.imap.core.MessageFunction;
import com.haulmont.addon.imap.core.Task;
import com.haulmont.addon.imap.dto.ImapFolderDto;
import com.haulmont.addon.imap.dto.ImapMessageDto;
import com.haulmont.addon.imap.entity.ImapMailBox;
import com.haulmont.addon.imap.entity.ImapMessage;
import com.haulmont.addon.imap.exception.ImapException;
import com.haulmont.bali.datastruct.Pair;
import com.haulmont.cuba.core.global.Metadata;
import com.haulmont.cuba.core.sys.AppContext;
import com.sun.mail.imap.IMAPMessage;
import com.sun.mail.imap.IMAPStore;
import org.apache.commons.lang.ArrayUtils;
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
import java.util.function.Function;
import java.util.stream.Collectors;

@Component(ImapAPI.NAME)
public class Imap implements ImapAPI, AppContext.Listener {

    private final static Logger log = LoggerFactory.getLogger(Imap.class);

    private final ImapHelper imapHelper;
    private final Metadata metadata;
    private final ImapConfig imapConfig;
    private ExecutorService fetchMessagesExecutor;

    @SuppressWarnings("CdiInjectionPointsInspection")
    @Inject
    public Imap(ImapHelper imapHelper,
                Metadata metadata,
                @SuppressWarnings("SpringJavaInjectionPointsAutowiringInspection") ImapConfig imapConfig) {

        this.imapHelper = imapHelper;
        this.metadata = metadata;
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
            IMAPStore store = imapHelper.getStore(box);

            return imapHelper.fetchFolders(store);
        } catch (MessagingException e) {
            throw new ImapException(e);
        }
    }

    @Override
    public List<ImapFolderDto> fetchFolders(ImapMailBox box, String... folderNames) {
        log.debug("fetch folders {} for box {}", folderNames, box);

        List<ImapFolderDto> allFolders = ImapFolderDto.flattenList(fetchFolders(box));
        for (ImapFolderDto allFolder : allFolders) {
            allFolder.setParent(null);
            allFolder.setChildren(Collections.emptyList());
        }
        if (ArrayUtils.isEmpty(folderNames)) {
            return allFolders;
        }

        Map<String, ImapFolderDto> foldersByFullNames = allFolders.stream()
                .collect(Collectors.toMap(ImapFolderDto::getFullName, Function.identity()));

        return Arrays.stream(folderNames).map(foldersByFullNames::get).filter(Objects::nonNull).collect(Collectors.toList());
    }

    @Override
    public ImapMessageDto fetchMessage(ImapMessage message) {
        log.debug("fetch message {}", message);

        return consumeMessage(message, nativeMessage -> {
            ImapMailBox mailBox = message.getFolder().getMailBox();

            return toDto(mailBox, message, nativeMessage);

        }, "fetch and transform message");
    }

    @Override
    public List<ImapMessageDto> fetchMessages(List<ImapMessage> messages) {
        List<Pair<ImapMessageDto, ImapMessage>> mailMessageDtos = new ArrayList<>(messages.size());
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
                                                mailMessageDtos.add(new Pair<>(
                                                        toDto(mailBox, message, nativeMessage), message)
                                                );
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


        return mailMessageDtos.stream()
                .sorted(Comparator.comparingInt(pair -> messages.indexOf(pair.getSecond())))
                .map(Pair::getFirst)
                .collect(Collectors.toList());
    }

    private ImapMessageDto toDto(ImapMailBox mailBox, ImapMessage imapMessage, IMAPMessage nativeMessage) throws MessagingException {
        if (nativeMessage == null) {
            return null;
        }

        ImapMessageDto dto = metadata.create(ImapMessageDto.class);
        dto.setUid(imapMessage.getMsgUid());
        dto.setFrom(getAddressList(nativeMessage.getFrom()).get(0));
        dto.setToList(getAddressList(nativeMessage.getRecipients(Message.RecipientType.TO)));
        dto.setCcList(getAddressList(nativeMessage.getRecipients(Message.RecipientType.CC)));
        dto.setBccList(getAddressList(nativeMessage.getRecipients(Message.RecipientType.BCC)));
        dto.setSubject(nativeMessage.getSubject());
        dto.setFlagsList(getFlags(nativeMessage));
        dto.setDate(nativeMessage.getReceivedDate());
        dto.setFolderName(imapMessage.getFolder().getName());
        dto.setMailBox(mailBox);
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
                msg.getFolder().expunge();
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
        if (msg.getFolder().getName().equals(newFolderName)) {
            return;
        }
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
                            folder.expunge();

                            return null;
                        }
                ));
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
                .map(address -> {
                    try {
                        return MimeUtility.decodeText(address);
                    } catch (UnsupportedEncodingException e) {
                        return address;
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
}
