package com.haulmont.addon.imap.sync;

import com.haulmont.addon.imap.dao.ImapDao;
import com.haulmont.addon.imap.entity.ImapMailBox;
import com.haulmont.cuba.security.app.Authentication;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import javax.annotation.Nonnull;
import javax.inject.Inject;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

@Component(ImapScheduler.NAME)
public class ImapSchedulerBean implements ImapScheduler {

    @Inject
    private ImapDao dao;

    @Inject
    private ImapSyncManager imapSyncManager;

    @Inject
    @Qualifier(ImapFlaglessSynchronizer.NAME)
    private ImapFlaglessSynchronizer imapFlaglessSynchronizer;

    @Inject
    @Qualifier(ImapSynchronizer.NAME)
    private ImapSynchronizer imapSynchronizer;

    @Inject
    private Authentication authentication;

    private final ExecutorService executor = Executors.newCachedThreadPool(new ThreadFactory() {
        private final AtomicInteger threadNumber = new AtomicInteger(1);

        @Override
        public Thread newThread(@Nonnull Runnable r) {
            Thread thread = new Thread(
                    r, "ImapMailBoxSync-" + threadNumber.getAndIncrement()
            );
            thread.setDaemon(true);
            return thread;
        }
    });

    @Override
    public void syncImap() {
        for (ImapMailBox mailBox : dao.findMailBoxes()) {
            executor.submit(() -> {
                authentication.begin();
                try {
                    syncMailBox(mailBox);
                } finally {
                    authentication.end();
                }
            });
        }
    }

    private void syncMailBox(ImapMailBox mailBox) {
        getImapSynchronizer(mailBox).synchronize(mailBox.getId());
        imapSyncManager.runEventsEmitter(mailBox.getId());
    }

    protected ImapSynchronizer getImapSynchronizer(ImapMailBox mailBox) {
        return Boolean.TRUE.equals(mailBox.getFlagsSupported()) ? imapSynchronizer : imapFlaglessSynchronizer;
    }
}
