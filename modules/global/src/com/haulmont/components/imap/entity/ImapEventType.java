package com.haulmont.components.imap.entity;

import com.haulmont.chile.core.datatypes.impl.EnumClass;
import com.haulmont.components.imap.events.*;

import javax.annotation.Nullable;
import java.util.Arrays;
import java.util.Collection;
import java.util.stream.Collectors;


public enum ImapEventType implements EnumClass<String> {

    NEW_EMAIL("new_email", NewEmailEvent.class),
    EMAIL_SEEN("seen", EmailSeenEvent.class),
    NEW_ANSWER("new_answer", EmailAnsweredEvent.class),
    EMAIL_MOVED("moved", EmailMovedEvent.class),
    FLAGS_UPDATED("flags_updated", EmailFlagChangedEvent.class),
    EMAIL_DELETED("deleted", EmailDeletedEvent.class),
    NEW_THREAD("new_thread", NewThreadEvent.class);

    private final String id;
    private final Class<? extends BaseImapEvent> eventClass;

    ImapEventType(String id, Class<? extends BaseImapEvent> eventClass) {
        this.id = id;
        this.eventClass = eventClass;
    }

    public String getId() {
        return id;
    }

    public Class<? extends BaseImapEvent> getEventClass() {
        return eventClass;
    }

    @Nullable
    public static ImapEventType fromId(String id) {
        for (ImapEventType at : ImapEventType.values()) {
            if (at.getId().equals(id)) {
                return at;
            }
        }
        return null;
    }

    public static Collection<ImapEventType> getByEventType(Class<? extends BaseImapEvent> eventClass) {
        return Arrays.stream(ImapEventType.values())
                .filter(event -> eventClass.isAssignableFrom(event.getEventClass()))
                .collect(Collectors.toList());
    }
}