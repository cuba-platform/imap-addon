package com.haulmont.components.imap.security;

import com.haulmont.components.imap.entity.ImapMailBox;

public interface Encryptor {

    String getEncryptedPassword(ImapMailBox mailBox);

    String getPlainPassword(ImapMailBox mailBox);
}
