package com.haulmont.components.imap.events;

import com.haulmont.components.imap.entity.ImapMessageRef;
import org.springframework.context.ApplicationEvent;

public abstract class BaseImapEvent extends ApplicationEvent {

    protected final ImapMessageRef messageRef;

    public BaseImapEvent(ImapMessageRef messageRef) {
        super(messageRef);
        this.messageRef = messageRef;
    }

    public ImapMessageRef getMessageRef() {
        return messageRef;
    }

    @Override
    public String toString() {
        return "BaseImapEvent{" +
                "messageRef=" + messageRef +
                '}';
    }
}
