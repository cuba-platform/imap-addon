package com.haulmont.addon.imap.entity;

import com.haulmont.chile.core.datatypes.impl.EnumClass;
import com.haulmont.addon.imap.events.*;

import javax.annotation.Nullable;
import java.util.Arrays;
import java.util.Collection;
import java.util.stream.Collectors;


public enum ImapEventType implements EnumClass<String> {

    NEW_EMAIL("new_email", NewEmailImapEvent.class),
    EMAIL_SEEN("seen", EmailSeenImapEvent.class),
    NEW_ANSWER("new_answer", EmailAnsweredImapEvent.class),
    EMAIL_MOVED("moved", EmailMovedImapEvent.class),
    FLAGS_UPDATED("flags_updated", EmailFlagChangedImapEvent.class),
    EMAIL_DELETED("deleted", EmailDeletedImapEvent.class),
    NEW_THREAD("new_thread", NewThreadImapEvent.class);

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