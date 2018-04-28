package com.haulmont.addon.imap.entity;

import com.haulmont.chile.core.datatypes.impl.EnumClass;
import com.haulmont.addon.imap.events.*;

import javax.annotation.Nullable;
import java.util.Arrays;
import java.util.Collection;
import java.util.stream.Collectors;

public enum ImapEventType implements EnumClass<String> {

    /**
     * Event type to capture new message in folder
     */
    NEW_EMAIL("new_email", NewEmailImapEvent.class),
    /**
     * Event type to capture mark message as read
     */
    EMAIL_SEEN("seen", EmailSeenImapEvent.class),
    /**
     * Event type to capture new reply for message
     */
    NEW_ANSWER("new_answer", EmailAnsweredImapEvent.class),
    /**
     * Event type to capture move message to different folder
     */
    EMAIL_MOVED("moved", EmailMovedImapEvent.class),
    /**
     * Event type to capture any change in IMAP flags of message
     */
    FLAGS_UPDATED("flags_updated", EmailFlagChangedImapEvent.class),
    /**
     * Event type to capture message removal
     */
    EMAIL_DELETED("deleted", EmailDeletedImapEvent.class),
    /**
     * Event type to capture new message thread in folder
     */
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