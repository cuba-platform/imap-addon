package com.haulmont.addon.imap.api;

import com.haulmont.addon.imap.config.ImapConfig;
import com.haulmont.addon.imap.core.*;
import com.haulmont.addon.imap.dto.ImapFolderDto;
import com.haulmont.addon.imap.dto.ImapMessageDto;
import com.haulmont.addon.imap.entity.ImapMailBox;
import com.haulmont.addon.imap.entity.ImapMessage;
import com.haulmont.addon.imap.execution.GlobalMailboxTask;
import com.haulmont.addon.imap.execution.ImapExecutor;
import com.haulmont.addon.imap.execution.ImmediateTask;
import com.haulmont.bali.datastruct.Pair;
import com.haulmont.cuba.core.global.Metadata;
import com.haulmont.cuba.core.sys.AppContext;
import com.sun.mail.imap.IMAPFolder;
import com.sun.mail.imap.IMAPMessage;
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
    private final ImapOperations imapOperations;
    private final Metadata metadata;
    private final ImapConfig imapConfig;
    private final ImapExecutor imapExecutor;
    private ExecutorService fetchMessagesExecutor;

    @SuppressWarnings("CdiInjectionPointsInspection")
    @Inject
    public Imap(ImapHelper imapHelper,
                ImapOperations imapOperations,
                Metadata metadata,
                @SuppressWarnings("SpringJavaInjectionPointsAutowiringInspection") ImapConfig imapConfig,
                ImapExecutor imapExecutor) {

        this.imapHelper = imapHelper;
        this.imapOperations = imapOperations;
        this.metadata = metadata;
        this.imapConfig = imapConfig;
        this.imapExecutor = imapExecutor;
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

        return imapExecutor.invokeGlobal(new GlobalMailboxTask<>(
                box,
                imapOperations::fetchFolders,
                "fetch all folders"
        ));
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

        }, "fetch and transform message with uid " + message.getUuid());
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
                        for (ImapMessage message : folderGroup.getValue()) {
                            Pair<ImapMessageDto, ImapMessage> dtoWithMessage = fetchDtoWithMessage(mailBox, folderName, message);
                            if (dtoWithMessage != null) {
                                mailMessageDtos.add(dtoWithMessage);
                            }
                        }

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

    private Pair<ImapMessageDto, ImapMessage> fetchDtoWithMessage(ImapMailBox mailBox, String folderName, ImapMessage message) {
        long uid = message.getMsgUid();
        return imapExecutor.invokeImmediate(new ImmediateTask<>(
                folderKey(mailBox, folderName),
                imapFolder -> {
                    IMAPMessage nativeMessage = (IMAPMessage) imapFolder.getMessageByUID(uid);
                    if (nativeMessage == null) {
                        return null;
                    }
                    return new Pair<>(
                            toDto(mailBox, message, nativeMessage), message);
                },
                "fetch message with uid " + uid
        ));
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
            }, "Mark message with uid " + message.getMsgUid() + " as DELETED");
        }
    }

    @Override
    public void moveMessage(ImapMessage msg, String folderName) {
        log.info("move message {} to folder {}", msg, folderName);
        ImapMailBox mailBox = msg.getFolder().getMailBox();
        doMove(msg, folderName, mailBox);
    }

    private void doMove(ImapMessage msg, String newFolderName, ImapMailBox mailBox) {
        String oldFolderName = msg.getFolder().getName();
        if (oldFolderName.equals(newFolderName)) {
            return;
        }
        Message message = imapExecutor.invokeImmediate(new ImmediateTask<>(
                folderKey(mailBox, oldFolderName),
                imapFolder -> {
                    Message m = imapFolder.getMessageByUID(msg.getMsgUid());
                    FetchProfile fp = new FetchProfile();
                    fp.add(FetchProfile.Item.FLAGS);
                    fp.add(FetchProfile.Item.ENVELOPE);
                    fp.add(IMAPFolder.FetchProfileItem.MESSAGE);
                    imapFolder.fetch(new Message[]{m}, fp);
                    log.debug("[move]delete message {} from folder {}", msg, imapFolder.getFullName());
                    imapFolder.setFlags(new Message[]{m}, new Flags(Flags.Flag.DELETED), true);
                    m.getReceivedDate();
                    m.getLineCount();
                    return m;
                },
                "deleting message with uid " + msg.getUuid()
        ));
        imapExecutor.invokeImmediate(ImmediateTask.noResultTask(
                folderKey(mailBox, newFolderName),
                imapFolder -> imapFolder.appendMessages(new Message[]{message}),
                "append message with uid " + msg.getUuid()
        ));
        imapExecutor.invokeImmediate(ImmediateTask.noResultTask(
                folderKey(mailBox, oldFolderName),
                IMAPFolder::expunge,
                "expunge"
        ));
    }

    @Override
    public void setFlag(ImapMessage message, ImapFlag flag, boolean set) {
        log.info("set flag {} for message {} to {}", message, flag, set);
        consumeMessage(message, msg -> {
            msg.setFlags(flag.imapFlags(), set);
            return null;
        }, "Set flag " + flag + " of message with uid " + message.getMsgUid() + " to " + set);
    }

    private <T> T consumeMessage(ImapMessage msg, ImapFunction<IMAPMessage, T> consumer, String actionDescription) {
        log.trace("perform {} on message {} ", actionDescription, msg);
        ImapMailBox mailBox = msg.getFolder().getMailBox();
        String folderName = msg.getFolder().getName();
        long uid = msg.getMsgUid();

        return imapExecutor.invokeImmediate(new ImmediateTask<>(
                folderKey(mailBox, folderName),
                imapFolder -> consumer.apply((IMAPMessage) imapFolder.getMessageByUID(uid)),
                actionDescription
        ));
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

    private FolderKey folderKey(ImapMailBox mailBox, String folderName) {
        return new FolderKey(new MailboxKey(mailBox), folderName);
    }
}
