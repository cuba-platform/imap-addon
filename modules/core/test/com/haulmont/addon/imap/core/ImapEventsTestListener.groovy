package com.haulmont.addon.imap.core

import com.haulmont.addon.imap.events.BaseImapEvent
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Component

@Component
class ImapEventsTestListener {

    public Collection<BaseImapEvent> events = new ArrayList<>()

    @EventListener
    void handle(BaseImapEvent event) {
        events.add(event)
    }
}
