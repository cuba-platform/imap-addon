package com.haulmont.components.imap.events;

import com.haulmont.components.imap.dto.MessageRef;
import org.springframework.context.ApplicationEvent;

public abstract class BaseImapEvent extends ApplicationEvent {

    protected final MessageRef messageRef;

    public BaseImapEvent(MessageRef messageRef) {
        super(messageRef);
        this.messageRef = messageRef;
    }

    public MessageRef getMessageRef() {
        return messageRef;
    }

    @Override
    public String toString() {
        return "BaseImapEvent{" +
                "messageRef=" + messageRef +
                '}';
    }
}
