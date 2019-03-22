package com.haulmont.addon.imap.sync.events;

import com.haulmont.addon.imap.api.ImapEventsGenerator;
import com.haulmont.addon.imap.entity.ImapFolder;
import com.haulmont.addon.imap.events.BaseImapEvent;

import java.util.Collection;

public abstract class ImapEventsBatchedGenerator implements ImapEventsGenerator {

    protected int batchSize;

    @Override
    public final Collection<? extends BaseImapEvent> generateForNewMessages(ImapFolder cubaFolder) {
        return generateForNewMessages(cubaFolder, batchSize);
    }

    @Override
    public final Collection<? extends BaseImapEvent> generateForChangedMessages(ImapFolder cubaFolder) {
        return generateForChangedMessages(cubaFolder, batchSize);
    }

    @Override
    public final Collection<? extends BaseImapEvent> generateForMissedMessages(ImapFolder cubaFolder) {
        return generateForMissedMessages(cubaFolder, batchSize);
    }

    protected abstract Collection<? extends BaseImapEvent> generateForNewMessages(ImapFolder cubaFolder, int batchSize);
    protected abstract Collection<? extends BaseImapEvent> generateForChangedMessages(ImapFolder cubaFolder, int batchSize);
    protected abstract Collection<? extends BaseImapEvent> generateForMissedMessages(ImapFolder cubaFolder, int batchSize);
}
