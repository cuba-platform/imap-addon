package com.haulmont.addon.imap.api;

import com.haulmont.addon.imap.core.FolderTask;
import com.haulmont.addon.imap.core.ImapHelper;
import com.haulmont.addon.imap.entity.ImapMailBox;
import com.haulmont.addon.imap.entity.ImapMessage;
import com.haulmont.addon.imap.entity.ImapMessageAttachment;
import com.sun.mail.imap.IMAPMessage;
import org.apache.commons.io.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import javax.inject.Inject;
import javax.mail.BodyPart;
import javax.mail.Multipart;
import java.io.IOException;
import java.io.InputStream;

@Component(ImapAttachmentsAPI.NAME)
@SuppressWarnings({"CdiInjectionPointsInspection", "SpringJavaAutowiredFieldsWarningInspection"})
public class ImapAttachments implements ImapAttachmentsAPI {
    private final static Logger LOG = LoggerFactory.getLogger(ImapAttachments.class);

    @Inject
    private ImapHelper imapHelper;

    @Override
    public InputStream openStream(ImapMessageAttachment attachment) {
        LOG.info("Open stream for attachment {}", attachment);
        ImapMessage msg = attachment.getImapMessage();
        ImapMailBox mailBox = msg.getFolder().getMailBox();
        String folderName = msg.getFolder().getName();

        return imapHelper.doWithFolder(mailBox, folderName, new FolderTask<>(
                "fetch attachment content",
                true,
                false,
                f -> {
                    //todo: this fetching can be optimised to fetch only attachments data
                    IMAPMessage imapMessage = (IMAPMessage) f.getMessageByUID(msg.getMsgUid());
                    imapMessage.setPeek(true);
                    try {
                        Multipart multipart = (Multipart) imapMessage.getContent();

                        BodyPart imapAttachment = multipart.getBodyPart(attachment.getOrderNumber());

                        return imapAttachment.getInputStream();
                    } catch (IOException e) {
                        throw new RuntimeException("Can't read content of attachment/message", e);
                    }
                }
        ));
    }

    @Override
    public byte[] loadFile(ImapMessageAttachment attachment) {
        LOG.info("load attachment {}", attachment);
        try (InputStream is = openStream(attachment)) {
            return IOUtils.toByteArray(is);
        } catch (IOException e) {
            throw new RuntimeException("Can't fetch bytes for attachment", e);
        }
    }
}
