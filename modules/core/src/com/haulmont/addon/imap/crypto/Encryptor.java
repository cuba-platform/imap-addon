package com.haulmont.addon.imap.crypto;

import com.haulmont.addon.imap.entity.ImapMailBox;

public interface Encryptor {

    String getEncryptedPassword(ImapMailBox mailBox);

    String getPlainPassword(ImapMailBox mailBox);
}
