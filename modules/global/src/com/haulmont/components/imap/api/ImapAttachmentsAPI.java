package com.haulmont.components.imap.api;

import com.haulmont.components.imap.entity.ImapMessageAttachmentRef;

import javax.mail.MessagingException;
import java.io.InputStream;

public interface ImapAttachmentsAPI {
    String NAME = "imapcomponent_ImapAttachmentsAPI";

    InputStream openStream(ImapMessageAttachmentRef attachmentRef) throws MessagingException;

    byte[] loadFile(ImapMessageAttachmentRef attachmentRef) throws MessagingException;
}
