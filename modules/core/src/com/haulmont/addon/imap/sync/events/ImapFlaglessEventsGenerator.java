package com.haulmont.addon.imap.sync.events;

import com.haulmont.addon.imap.dao.ImapMessageSyncDao;
import com.haulmont.addon.imap.sync.ImapFlaglessSynchronizer;
import com.haulmont.cuba.core.Persistence;
import com.haulmont.cuba.security.app.Authentication;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import javax.inject.Inject;

@Component(ImapFlaglessEventsGenerator.NAME)
public class ImapFlaglessEventsGenerator extends ImapStandardEventsGenerator {

    static final String NAME = "imap_FlaglessEventsGenerator";

    @SuppressWarnings("CdiInjectionPointsInspection")
    @Inject
    public ImapFlaglessEventsGenerator(ImapMessageSyncDao messageSyncDao,
                                       Authentication authentication,
                                       Persistence persistence,
                                       @Qualifier(ImapFlaglessSynchronizer.NAME) ImapFlaglessSynchronizer imapSynchronizer) {
        super(messageSyncDao, authentication, persistence, imapSynchronizer);
    }
}
