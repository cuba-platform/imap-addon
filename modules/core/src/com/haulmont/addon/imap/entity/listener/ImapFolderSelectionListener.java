package com.haulmont.addon.imap.entity.listener;

import com.haulmont.addon.imap.sync.listener.ImapFolderEvent;
import com.haulmont.cuba.core.PersistenceTools;
import com.haulmont.cuba.core.global.Events;
import org.springframework.stereotype.Component;
import com.haulmont.cuba.core.listener.AfterDeleteEntityListener;
import java.sql.Connection;
import com.haulmont.addon.imap.entity.ImapFolder;
import com.haulmont.cuba.core.listener.AfterInsertEntityListener;
import com.haulmont.cuba.core.listener.AfterUpdateEntityListener;

import javax.inject.Inject;

@Component("imap_FolderSelectionListener")
public class ImapFolderSelectionListener implements AfterDeleteEntityListener<ImapFolder>,
                                                    AfterInsertEntityListener<ImapFolder>,
                                                    AfterUpdateEntityListener<ImapFolder> {

    private final PersistenceTools persistenceTools;

    private final Events events;

    @SuppressWarnings("CdiInjectionPointsInspection")
    @Inject
    public ImapFolderSelectionListener(PersistenceTools persistenceTools, Events events) {
        this.persistenceTools = persistenceTools;
        this.events = events;
    }

    @Override
    public void onAfterDelete(ImapFolder entity, Connection connection) {
        events.publish(new ImapFolderEvent(entity, ImapFolderEvent.Type.REMOVED));
    }

    @Override
    public void onAfterInsert(ImapFolder entity, Connection connection) {
        publishEvent(entity);
    }

    @Override
    public void onAfterUpdate(ImapFolder entity, Connection connection) {
        if (persistenceTools.isDirty(entity, "selected")
                || persistenceTools.isDirty(entity, "disabled")) {

            publishEvent(entity);
        }
    }

    private void publishEvent(ImapFolder folder) {
        boolean subscribe = Boolean.TRUE.equals(folder.getSelected()) && !Boolean.TRUE.equals(folder.getDisabled());
        events.publish(new ImapFolderEvent(folder, subscribe ? ImapFolderEvent.Type.ADDED : ImapFolderEvent.Type.REMOVED));
    }

}