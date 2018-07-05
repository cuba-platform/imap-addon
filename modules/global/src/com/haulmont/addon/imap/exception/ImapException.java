package com.haulmont.addon.imap.exception;

import com.haulmont.cuba.core.global.SupportedByClient;

import javax.mail.MessagingException;

@SupportedByClient
public class ImapException extends RuntimeException {

    public ImapException(MessagingException cause) {
        super(causeDescription(cause));
        addSuppressed(cause);
    }

    public ImapException(String message, MessagingException cause) {
        super(String.format("%s caused by: %s", message, causeDescription(cause)));
        addSuppressed(cause);
    }

    private static String causeDescription(MessagingException e) {
        String message = e.getMessage();
        return String.format("[%s]%s", e.getClass().getName(), message != null ? message : e.toString() );
    }
}
