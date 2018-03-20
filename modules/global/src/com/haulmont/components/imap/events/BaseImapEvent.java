package com.haulmont.components.imap.events;

import com.haulmont.components.imap.entity.ImapMessage;
import org.springframework.context.ApplicationEvent;

public abstract class BaseImapEvent extends ApplicationEvent {

    protected final ImapMessage message;

    public BaseImapEvent(ImapMessage message) {
        super(message);
        this.message = message;
    }

    public ImapMessage getMessage() {
        return message;
    }

    @Override
    public String toString() {
        return "BaseImapEvent{" +
                "message=" + message +
                '}';
    }
}
