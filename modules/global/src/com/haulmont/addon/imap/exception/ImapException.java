package com.haulmont.addon.imap.exception;

import com.haulmont.cuba.core.global.SupportedByClient;

import javax.mail.MessagingException;

@SupportedByClient
public class ImapException extends RuntimeException {

    public ImapException(MessagingException cause) {
        super(cause.getMessage());
    }

    public ImapException(String message, MessagingException cause) {
        super(String.format("%s causes by: %s", message, cause.getMessage()));
    }
}
