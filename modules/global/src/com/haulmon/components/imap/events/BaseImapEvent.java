package com.haulmon.components.imap.events;

import org.springframework.context.ApplicationEvent;

public abstract class BaseImapEvent extends ApplicationEvent {

    public BaseImapEvent(Object source) {
        super(source);
    }
}
