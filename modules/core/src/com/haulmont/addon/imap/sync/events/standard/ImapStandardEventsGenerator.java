package com.haulmont.addon.imap.sync.events.standard;

import com.haulmont.addon.imap.entity.ImapFolder;
import com.haulmont.addon.imap.events.BaseImapEvent;
import com.haulmont.addon.imap.sync.events.ImapEventsGenerator;
import com.sun.mail.imap.IMAPMessage;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import javax.inject.Inject;
import java.util.Collection;

@Component(ImapStandardEventsGenerator.NAME)
public class ImapStandardEventsGenerator implements ImapEventsGenerator {

    public static final String NAME = "imap_StandardEventsGenerator";

    private final ImapNewMessagesEvents newEvents;
    private final ImapMissedMessagesEvents missedEvents;
    private final ImapChangedMessagesEvents changedEvents;

    @Inject
    public ImapStandardEventsGenerator(@Qualifier("imap_NewMessagesEvents") ImapNewMessagesEvents newEvents,
                                       @Qualifier("imap_MissedMessagesEvents") ImapMissedMessagesEvents missedEvents,
                                       @Qualifier("imap_ChangedMessagesEvents") ImapChangedMessagesEvents changedEvents) {

        this.newEvents = newEvents;
        this.missedEvents = missedEvents;
        this.changedEvents = changedEvents;
    }

    @Override
    public Collection<? extends BaseImapEvent> generateForNewMessages(ImapFolder cubaFolder) {
        return newEvents.generate(cubaFolder);
    }

    @Override
    public Collection<? extends BaseImapEvent> generateForChangedMessages(ImapFolder cubaFolder) {
        return changedEvents.generate(cubaFolder);
    }

    @Override
    public Collection<? extends BaseImapEvent> generateForMissedMessages(ImapFolder cubaFolder) {
        return missedEvents.generate(cubaFolder);
    }

    @Override
    public Collection<? extends BaseImapEvent> generateForNewMessages(ImapFolder cubaFolder,
                                                                      Collection<IMAPMessage> newMessages) {
        return newEvents.generate(cubaFolder, newMessages);
    }

    @Override
    public Collection<? extends BaseImapEvent> generateForChangedMessages(ImapFolder cubaFolder,
                                                                          Collection<IMAPMessage> changedMessages) {
        return changedEvents.generate(cubaFolder, changedMessages);
    }

    @Override
    public Collection<? extends BaseImapEvent> generateForMissedMessages(ImapFolder cubaFolder,
                                                                         Collection<IMAPMessage> missedMessages) {
        return missedEvents.generate(cubaFolder, missedMessages);
    }
}
