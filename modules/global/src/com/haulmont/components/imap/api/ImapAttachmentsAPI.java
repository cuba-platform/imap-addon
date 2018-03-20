package com.haulmont.components.imap.api;

import com.haulmont.components.imap.entity.ImapMessageAttachment;

import javax.mail.MessagingException;
import java.io.InputStream;

public interface ImapAttachmentsAPI {
    String NAME = "imapcomponent_ImapAttachmentsAPI";

    InputStream openStream(ImapMessageAttachment attachment) throws MessagingException;

    byte[] loadFile(ImapMessageAttachment attachment) throws MessagingException;
}
