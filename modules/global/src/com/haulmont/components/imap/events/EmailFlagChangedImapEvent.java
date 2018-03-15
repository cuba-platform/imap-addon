package com.haulmont.components.imap.events;

import com.haulmont.components.imap.entity.ImapMessageRef;

import java.util.Map;

public class EmailFlagChangedImapEvent extends BaseImapEvent {

    private final Map<String, Boolean> changedFlagsWithNewValue;

    public EmailFlagChangedImapEvent(ImapMessageRef messageRef, Map<String, Boolean> changedFlagsWithNewValue) {
        super(messageRef);

        this.changedFlagsWithNewValue = changedFlagsWithNewValue;
    }

    public Map<String, Boolean> getChangedFlagsWithNewValue() {
        return changedFlagsWithNewValue;
    }

    @Override
    public String toString() {
        return "EmailFlagChangedImapEvent{" +
                "changedFlagsWithNewValue=" + changedFlagsWithNewValue +
                ", messageRef=" + messageRef +
                '}';
    }
}
