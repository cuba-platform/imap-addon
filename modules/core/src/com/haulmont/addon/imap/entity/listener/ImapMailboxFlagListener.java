package com.haulmont.addon.imap.entity.listener;

import com.haulmont.addon.imap.entity.ImapMailBox;
import com.haulmont.addon.imap.sync.listener.ImapFolderEvent;
import com.haulmont.cuba.core.PersistenceTools;
import com.haulmont.cuba.core.global.Events;
import com.haulmont.cuba.core.listener.AfterUpdateEntityListener;
import org.springframework.stereotype.Component;

import javax.inject.Inject;
import java.sql.Connection;

@Component("imap_MailboxFlagListener")
public class ImapMailboxFlagListener implements AfterUpdateEntityListener<ImapMailBox> {

    private final PersistenceTools persistenceTools;

    private final Events events;

    @SuppressWarnings("CdiInjectionPointsInspection")
    @Inject
    public ImapMailboxFlagListener(PersistenceTools persistenceTools, Events events) {
        this.persistenceTools = persistenceTools;
        this.events = events;
    }

    @Override
    public void onAfterUpdate(ImapMailBox entity, Connection connection) {
        if (persistenceTools.isDirty(entity, "cubaFlag")) {
            publishEvents(entity);
        }
    }

    private void publishEvents(ImapMailBox mailBox) {
        mailBox.getProcessableFolders().forEach(folder ->
                events.publish(new ImapFolderEvent(folder, ImapFolderEvent.Type.ADDED))
        );
    }

}