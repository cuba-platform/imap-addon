package com.haulmont.components.imap.api;

import com.haulmont.components.imap.core.ImapHelper;
import com.haulmont.components.imap.core.Task;
import com.haulmont.components.imap.entity.ImapMailBox;
import com.haulmont.components.imap.entity.ImapMessageAttachmentRef;
import com.haulmont.components.imap.entity.ImapMessageRef;
import com.sun.mail.imap.IMAPFolder;
import com.sun.mail.imap.IMAPMessage;
import org.apache.commons.io.IOUtils;
import org.springframework.stereotype.Component;

import javax.inject.Inject;
import javax.mail.*;
import java.io.IOException;
import java.io.InputStream;

@Component(ImapAttachmentsAPI.NAME)
public class ImapAttachments implements ImapAttachmentsAPI {

    @Inject
    private ImapHelper imapHelper;

    @Override
    public InputStream openStream(ImapMessageAttachmentRef attachmentRef) throws MessagingException {
        ImapMessageRef ref = attachmentRef.getImapMessageRef();
        ImapMailBox mailBox = ref.getFolder().getMailBox();
        String folderName = ref.getFolder().getName();

        Store store = imapHelper.getStore(mailBox);
        IMAPFolder folder = (IMAPFolder) store.getFolder(folderName);
        Task<ImapMessageRef, InputStream> task = new Task<>(
                "fetch attachment conent", true, msgRef -> {
            IMAPMessage msg = (IMAPMessage) folder.getMessageByUID(msgRef.getMsgUid()); //todo: this fetching can be optimised to fetch only attachments data
            msg.setPeek(true);
            try {
                Multipart multipart = (Multipart) msg.getContent();

                BodyPart attachment = multipart.getBodyPart(attachmentRef.getOrderNumber());

                return attachment.getInputStream();
            } catch (IOException e) {
                throw new RuntimeException("Can't read content of attachment/message", e);
            }

        });
        return imapHelper.doWithMsg(ref, folder, task);
    }

    @Override
    public byte[] loadFile(ImapMessageAttachmentRef attachmentRef) throws MessagingException {
        try (InputStream is = openStream(attachmentRef)) {
            return IOUtils.toByteArray(is);
        } catch (IOException e) {
            throw new RuntimeException("Can't fetch bytes for attachment", e);
        }
    }
}
