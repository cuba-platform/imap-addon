package com.haulmont.components.imap.events;

import com.haulmont.components.imap.entity.ImapMessage;

import java.util.Map;

public class EmailFlagChangedImapEvent extends BaseImapEvent {

    private final Map<String, Boolean> changedFlagsWithNewValue;

    public EmailFlagChangedImapEvent(ImapMessage message, Map<String, Boolean> changedFlagsWithNewValue) {
        super(message);

        this.changedFlagsWithNewValue = changedFlagsWithNewValue;
    }

    public Map<String, Boolean> getChangedFlagsWithNewValue() {
        return changedFlagsWithNewValue;
    }

    @Override
    public String toString() {
        return "EmailFlagChangedImapEvent{" +
                "changedFlagsWithNewValue=" + changedFlagsWithNewValue +
                ", message=" + message +
                '}';
    }
}
