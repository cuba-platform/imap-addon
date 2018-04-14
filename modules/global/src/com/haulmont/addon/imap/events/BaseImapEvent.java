package com.haulmont.addon.imap.events;

import com.haulmont.addon.imap.entity.ImapMessage;
import org.apache.commons.lang.builder.ToStringBuilder;
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
        return new ToStringBuilder(this).
                append("message", message).
                toString();
    }
}
