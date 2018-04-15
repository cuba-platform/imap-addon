package com.haulmont.addon.imap.sync.events.standard;

import com.haulmont.addon.imap.entity.ImapFolder;
import com.haulmont.addon.imap.events.BaseImapEvent;
import com.haulmont.addon.imap.sync.events.ImapEventsGenerator;
import com.sun.mail.imap.IMAPMessage;
import org.springframework.stereotype.Component;

import javax.inject.Inject;
import java.util.Collection;

@Component(ImapStandardEventsGenerator.NAME)
public class ImapStandardEventsGenerator implements ImapEventsGenerator {

    public static final String NAME = "imapcomponent_ImapStandardEventsGenerator";

    @Inject
    private ImapNewMessagesEventsGenerator newMessagesEventsGenerator;

    @Inject
    private ImapMissedMessagesEventsGenerator missedMessagesEventsGenerator;

    @Inject
    private ImapChangedMessagesEventsGenerator changedMessagesEventsGenerator;

    @Override
    public Collection<? extends BaseImapEvent> generateForNewMessages(ImapFolder cubaFolder) {
        return newMessagesEventsGenerator.generate(cubaFolder);
    }

    @Override
    public Collection<? extends BaseImapEvent> generateForChangedMessages(ImapFolder cubaFolder) {
        return changedMessagesEventsGenerator.generate(cubaFolder);
    }

    @Override
    public Collection<? extends BaseImapEvent> generateForMissedMessages(ImapFolder cubaFolder) {
        return missedMessagesEventsGenerator.generate(cubaFolder);
    }

    @Override
    public Collection<? extends BaseImapEvent> generateForNewMessages(ImapFolder cubaFolder,
                                                                      Collection<IMAPMessage> newMessages) {
        return newMessagesEventsGenerator.generate(cubaFolder, newMessages);
    }

    @Override
    public Collection<? extends BaseImapEvent> generateForChangedMessages(ImapFolder cubaFolder,
                                                                          Collection<IMAPMessage> changedMessages) {
        return changedMessagesEventsGenerator.generate(cubaFolder, changedMessages);
    }

    @Override
    public Collection<? extends BaseImapEvent> generateForMissedMessages(ImapFolder cubaFolder,
                                                                         Collection<IMAPMessage> missedMessags) {
        return missedMessagesEventsGenerator.generate(cubaFolder, missedMessags);
    }
}
