package com.haulmont.addon.imap.sync;

import com.haulmont.addon.imap.dao.ImapDao;
import com.haulmont.addon.imap.entity.ImapMailBox;
import com.haulmont.cuba.security.app.Authentication;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import javax.annotation.Nonnull;
import javax.inject.Inject;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

@Component(ImapScheduler.NAME)
public class ImapSchedulerBean implements ImapScheduler {

    private final static Logger log = LoggerFactory.getLogger(ImapSchedulerBean.class);

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
        Collection<ImapMailBox> mailBoxes = dao.findMailBoxes();
        Map<ImapMailBox, Future> tasks = new HashMap<>(mailBoxes.size());
        for (ImapMailBox mailBox : mailBoxes) {
            tasks.put(mailBox, executor.submit(() -> {
                authentication.begin();
                try {
                    syncMailBox(mailBox);
                } finally {
                    authentication.end();
                }
            }));
        }
        tasks.keySet().forEach(mailBox -> {
            try {
                tasks.get(mailBox).get();
            } catch (Exception e) {
                log.error(String.format("Error on %s[%s] mailbox sync",
                        mailBox.getName(), mailBox.getId()), e);
            }
        });
    }

    private void syncMailBox(ImapMailBox mailBox) {
        getImapSynchronizer(mailBox).synchronize(mailBox.getId());
        imapSyncManager.runEventsEmitter(mailBox.getId());
    }

    protected ImapSynchronizer getImapSynchronizer(ImapMailBox mailBox) {
        return Boolean.TRUE.equals(mailBox.getFlagsSupported()) ? imapSynchronizer : imapFlaglessSynchronizer;
    }
}
