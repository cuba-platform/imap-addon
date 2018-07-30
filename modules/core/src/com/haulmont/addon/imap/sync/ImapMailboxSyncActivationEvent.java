package com.haulmont.addon.imap.sync;

import com.haulmont.addon.imap.entity.ImapMailBox;
import org.springframework.context.ApplicationEvent;

public class ImapMailboxSyncActivationEvent extends ApplicationEvent {

    private final ImapMailBox mailBox;
    private final Type type;

    public ImapMailboxSyncActivationEvent(ImapMailBox mailBox, Type type) {
        super(mailBox);
        this.mailBox = mailBox;
        this.type = type;
    }

    public ImapMailBox getMailBox() {
        return mailBox;
    }

    public Type getType() {
        return type;
    }

    public enum Type {
        ACTIVATE, DEACTIVATE
    }
}