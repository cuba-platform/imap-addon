package com.haulmont.addon.imap.api;

import com.haulmont.addon.imap.core.ImapHelper;
import com.haulmont.addon.imap.core.Task;
import com.haulmont.addon.imap.dao.ImapDao;
import com.haulmont.addon.imap.entity.ImapMailBox;
import com.haulmont.addon.imap.entity.ImapMessage;
import com.haulmont.addon.imap.entity.ImapMessageAttachment;
import com.haulmont.cuba.core.global.Metadata;
import com.haulmont.cuba.core.global.TimeSource;
import com.sun.mail.imap.IMAPMessage;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import javax.inject.Inject;
import javax.mail.BodyPart;
import javax.mail.MessagingException;
import javax.mail.Multipart;
import javax.mail.Part;
import javax.mail.internet.MimeUtility;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

@Component(ImapAttachmentsAPI.NAME)
public class ImapAttachments implements ImapAttachmentsAPI {
    private final static Logger log = LoggerFactory.getLogger(ImapAttachments.class);

    private final ImapHelper imapHelper;
    private final ImapDao dao;
    private final TimeSource timeSource;
    private final Metadata metadata;

    @SuppressWarnings("CdiInjectionPointsInspection")
    @Inject
    public ImapAttachments(ImapHelper imapHelper, ImapDao dao, TimeSource timeSource, Metadata metadata) {
        this.imapHelper = imapHelper;
        this.dao = dao;
        this.timeSource = timeSource;
        this.metadata = metadata;
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

        return imapHelper.doWithFolder(mailBox, folderName, true, new Task<>(
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
    
    @Override
    public InputStream openStream(ImapMessageAttachment attachment) {
        log.info("Open stream for attachment {}", attachment);
        //todo: streaming should be fair
        return new ByteArrayInputStream(loadFile(attachment));
    }

    @Override
    public byte[] loadFile(ImapMessageAttachment attachment) {
        log.info("load attachment {}", attachment);

        ImapMessage msg = attachment.getImapMessage();
        ImapMailBox mailBox = msg.getFolder().getMailBox();
        String folderName = msg.getFolder().getName();

        return imapHelper.doWithFolder(mailBox, folderName, true, new Task<>(
                "fetch attachment content",
                true,
                f -> {
                    IMAPMessage imapMessage = (IMAPMessage) f.getMessageByUID(msg.getMsgUid());
                    imapMessage.setPeek(true);
                    try {
                        Multipart multipart = (Multipart) imapMessage.getContent();

                        BodyPart imapAttachment = multipart.getBodyPart(attachment.getOrderNumber());

                        return IOUtils.toByteArray(imapAttachment.getInputStream());
                    } catch (IOException e) {
                        throw new RuntimeException("Can't read content of attachment/message", e);
                    }
                }
        ));
    }
}
