package com.haulmont.addon.imap.events;

import com.haulmont.addon.imap.api.ImapFlag;
import com.haulmont.addon.imap.entity.ImapMessage;
import org.apache.commons.lang3.builder.ToStringBuilder;

import java.util.Map;

/**
 * Event triggered when any IMAP flag of message was changed,
 * {@link #getChangedFlagsWithNewValue()} specifies only modified flags with actual values
 */
public class EmailFlagChangedImapEvent extends BaseImapEvent {

    private final Map<ImapFlag, Boolean> changedFlagsWithNewValue;

    @SuppressWarnings("WeakerAccess")
    public EmailFlagChangedImapEvent(ImapMessage message, Map<ImapFlag, Boolean> changedFlagsWithNewValue) {
        super(message);

        this.changedFlagsWithNewValue = changedFlagsWithNewValue;
    }

    @SuppressWarnings("unused")
    public Map<ImapFlag, Boolean> getChangedFlagsWithNewValue() {
        return changedFlagsWithNewValue;
    }

    @Override
    public String toString() {
        return new ToStringBuilder(this).
                append("changedFlagsWithNewValue", changedFlagsWithNewValue).
                append("message", message).
                toString();
    }
}
