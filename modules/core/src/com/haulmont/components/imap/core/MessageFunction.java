package com.haulmont.components.imap.core;

import javax.mail.MessagingException;

@FunctionalInterface
public interface MessageFunction<INPUT, OUTPUT> {
    OUTPUT apply(INPUT input) throws MessagingException;
}
