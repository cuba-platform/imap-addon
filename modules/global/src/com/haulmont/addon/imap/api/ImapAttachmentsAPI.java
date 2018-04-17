package com.haulmont.addon.imap.api;

import com.haulmont.addon.imap.entity.ImapMessageAttachment;

import java.io.InputStream;

public interface ImapAttachmentsAPI {
    String NAME = "imap_AttachmentsAPI";

    InputStream openStream(ImapMessageAttachment attachment);

    byte[] loadFile(ImapMessageAttachment attachment);
}
