package com.haulmont.addon.imap.entity.events;

import com.haulmont.addon.imap.entity.ImapMailBox;
import org.springframework.context.ApplicationEvent;

public class ImapPasswordChangedEvent extends ApplicationEvent {

    public final ImapMailBox mailBox;
    public final String rawPassword; //todo: secure ?

    public ImapPasswordChangedEvent(ImapMailBox mailBox, String rawPassword) {
        super(mailBox);
        this.mailBox = mailBox;
        this.rawPassword = rawPassword;
    }


}
