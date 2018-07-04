package com.haulmont.addon.imap.core;

import javax.mail.MessagingException;

@FunctionalInterface
public interface ImapConsumer<T> {
    void accept (T input) throws MessagingException;
}
