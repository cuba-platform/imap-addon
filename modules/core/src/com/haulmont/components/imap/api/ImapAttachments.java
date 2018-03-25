package com.haulmont.components.imap.api;

import com.haulmont.components.imap.core.ImapHelper;
import com.haulmont.components.imap.core.Task;
import com.haulmont.components.imap.entity.ImapMailBox;
import com.haulmont.components.imap.entity.ImapMessageAttachment;
import com.haulmont.components.imap.entity.ImapMessage;
import com.sun.mail.imap.IMAPFolder;
import com.sun.mail.imap.IMAPMessage;
import org.apache.commons.io.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import javax.inject.Inject;
import javax.mail.*;
import java.io.IOException;
import java.io.InputStream;

@Component(ImapAttachmentsAPI.NAME)
public class ImapAttachments implements ImapAttachmentsAPI {
    private final static Logger log = LoggerFactory.getLogger(ImapAttachments.class);

    @Inject
    private ImapHelper imapHelper;

    @Override
    public InputStream openStream(ImapMessageAttachment attachment) throws MessagingException {
        log.info("Open stream for attachment {}", attachment);
        ImapMessage msg = attachment.getImapMessage();
        ImapMailBox mailBox = msg.getFolder().getMailBox();
        String folderName = msg.getFolder().getName();

        Store store = imapHelper.getStore(mailBox);
        IMAPFolder folder = (IMAPFolder) store.getFolder(folderName);
        Task<ImapMessage, InputStream> task = new Task<>(
                "fetch attachment conent", true, msgRef -> {
            IMAPMessage imapMessage = (IMAPMessage) folder.getMessageByUID(msgRef.getMsgUid()); //todo: this fetching can be optimised to fetch only attachments data
            imapMessage.setPeek(true);
            try {
                Multipart multipart = (Multipart) imapMessage.getContent();

                BodyPart imapAttachment = multipart.getBodyPart(attachment.getOrderNumber());

                return imapAttachment.getInputStream();
            } catch (IOException e) {
                throw new RuntimeException("Can't read content of attachment/message", e);
            }

        });
        return imapHelper.doWithMsg(msg, folder, task);
    }

    @Override
    public byte[] loadFile(ImapMessageAttachment attachment) throws MessagingException {
        log.info("load attachment {}", attachment);
        try (InputStream is = openStream(attachment)) {
            return IOUtils.toByteArray(is);
        } catch (IOException e) {
            throw new RuntimeException("Can't fetch bytes for attachment", e);
        }
    }
}
