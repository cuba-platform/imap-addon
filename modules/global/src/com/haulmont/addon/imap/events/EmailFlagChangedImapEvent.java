package com.haulmont.addon.imap.events;

import com.haulmont.addon.imap.api.ImapFlag;
import com.haulmont.addon.imap.entity.ImapMessage;

import java.util.Map;

public class EmailFlagChangedImapEvent extends BaseImapEvent {

    private final Map<ImapFlag, Boolean> changedFlagsWithNewValue;

    public EmailFlagChangedImapEvent(ImapMessage message, Map<ImapFlag, Boolean> changedFlagsWithNewValue) {
        super(message);

        this.changedFlagsWithNewValue = changedFlagsWithNewValue;
    }

    public Map<ImapFlag, Boolean> getChangedFlagsWithNewValue() {
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
