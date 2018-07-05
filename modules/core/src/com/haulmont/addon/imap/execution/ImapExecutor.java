package com.haulmont.addon.imap.execution;

import com.haulmont.addon.imap.core.ImapHelper;
import com.haulmont.addon.imap.core.MailboxKey;
import com.haulmont.addon.imap.entity.ImapMailBox;
import com.haulmont.addon.imap.entity.events.ImapPasswordChangedEvent;
import com.haulmont.addon.imap.exception.ImapException;
import com.haulmont.cuba.security.app.Authentication;
import com.sun.mail.imap.IMAPStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import javax.annotation.PreDestroy;
import javax.inject.Inject;
import javax.mail.MessagingException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicReference;

@Component("imap_Executor")
public class ImapExecutor implements TaskExecutor {

    private final static Logger log = LoggerFactory.getLogger(ImapExecutor.class);

    private final ImapHelper imapHelper;
    private final Authentication authentication;

    @Inject
    public ImapExecutor(ImapHelper imapHelper, Authentication authentication) {
        this.imapHelper = imapHelper;
        this.authentication = authentication;
    }

    private ConcurrentMap<MailboxKey, ImapMailboxExecutor> mailboxExecutors = new ConcurrentHashMap<>();

    public <T> T invokeImmediate(ImmediateTask<T> task) {
        log.trace("invoke {}", task.description);
        return mailboxExecutor(task.folder.getMailboxKey()).invokeImmediate(task);
    }

    public <T> boolean submitDelayable(DelayableTask<T> task) {
        log.trace("submit {}", task.description);
        return mailboxExecutor(task.folder.getMailboxKey()).submitDelayable(task);
    }

    public <T> T invokeGlobal(GlobalMailboxTask<T> task) {
        log.trace("invoke {}", task.description);
        ImapMailBox mailbox = task.mailbox;
        ImapMailboxExecutor executor = mailboxExecutor( new MailboxKey(mailbox) );
        AtomicReference<T> result = new AtomicReference<>();
        executor.resetStore(() -> {
            try {
                log.debug("Get fresh IMAP store while performing {}", task.description);
                IMAPStore newStore = imapHelper.getStore(mailbox);
                log.debug("Invoke task {} with fresh IMAP store", task.description);
                result.set(task.action.apply(newStore));
                if (log.isDebugEnabled()) {
                    log.debug("Result of task {} with fresh IMAP store is {}", task.description, result.get());
                }
                return newStore;
            } catch (MessagingException e) {
                throw new ImapException(e);
            }
        });

        return result.get();
    }

    @EventListener
    public void handlePasswordChangeEvent(ImapPasswordChangedEvent event) {
        MailboxKey mailboxKey = new MailboxKey(event.mailBox);
        log.info("Reset IMAP store for {} due to password change", mailboxKey);

        mailboxExecutors.computeIfPresent(mailboxKey, (key, executor) -> {
            log.debug("Apply new password for IMAP store of {}", key);
            executor.resetStore(() -> {
                authentication.begin();
                try {
                    return imapHelper.store(key, event.rawPassword);
                } catch (MessagingException e) {
                    log.warn("can't reset store of " + mailboxKey, e);
                    return null;
                } finally {
                    authentication.end();
                }
            });
            return executor;
        });
    }

    @PreDestroy
    public void closeStores() {
        for (ImapMailboxExecutor executor : mailboxExecutors.values()) {
            try {
                executor.closeStore();
            } catch (Exception e) {
                log.warn("Failed to close store for " + executor.mailboxKey, e);
            }
        }
    }

    private ImapMailboxExecutor mailboxExecutor(MailboxKey mailboxKey) {
        log.trace("get executor for mailbox {}", mailboxKey);
        mailboxExecutors.computeIfAbsent(
                mailboxKey,
                key -> {
                    log.debug("constructing new executor for mailbox {}", key);
                    authentication.begin();
                    try {
                        return new ImapMailboxExecutor(key, imapHelper::store);
                    } finally {
                        authentication.end();
                    }
                }
        );
        return mailboxExecutors.get(mailboxKey);
    }
}
