package com.haulmont.components.imap.events;

import com.haulmont.components.imap.dto.MessageRef;

import java.util.Map;

public class EmailFlagChangedEvent extends BaseImapEvent {

    private final Map<String, Boolean> changedFlagsWithNewValue;

    public EmailFlagChangedEvent(MessageRef messageRef, Map<String, Boolean> changedFlagsWithNewValue) {
        super(messageRef);

        this.changedFlagsWithNewValue = changedFlagsWithNewValue;
    }

    public Map<String, Boolean> getChangedFlagsWithNewValue() {
        return changedFlagsWithNewValue;
    }

    @Override
    public String toString() {
        return "EmailFlagChangedEvent{" +
                "changedFlagsWithNewValue=" + changedFlagsWithNewValue +
                ", messageRef=" + messageRef +
                '}';
    }
}
