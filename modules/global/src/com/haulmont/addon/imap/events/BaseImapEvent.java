package com.haulmont.addon.imap.events;

import com.haulmont.addon.imap.entity.ImapMessage;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.springframework.context.ApplicationEvent;

/**
 * Base class for all IMAP application events, providing reference for affected {@link ImapMessage}
 */
public abstract class BaseImapEvent extends ApplicationEvent {

    @SuppressWarnings("WeakerAccess")
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
