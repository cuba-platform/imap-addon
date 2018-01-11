package com.haulmon.components.imap.scheduling;

import com.haulmon.components.imap.core.ImapBase;
import com.haulmon.components.imap.entity.MailBox;
import com.haulmon.components.imap.entity.PredefinedEventType;
import com.haulmon.components.imap.entity.MailFolder;
import com.haulmon.components.imap.events.NewEmailEvent;
import com.haulmont.cuba.core.EntityManager;
import com.haulmont.cuba.core.Persistence;
import com.haulmont.cuba.core.Transaction;
import com.haulmont.cuba.core.TypedQuery;
import com.haulmont.cuba.core.global.Events;
import com.haulmont.cuba.core.global.TimeSource;
import com.haulmont.cuba.security.app.Authentication;
import com.sun.mail.imap.IMAPFolder;
import com.sun.mail.imap.IMAPMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import javax.inject.Inject;
import javax.mail.Flags;
import javax.mail.Folder;
import javax.mail.Store;
import javax.mail.search.AndTerm;
import javax.mail.search.FlagTerm;
import javax.mail.search.NotTerm;
import javax.mail.search.SearchTerm;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;
import java.util.stream.Collectors;

@Component(ImapSchedulingAPI.NAME)
public class ImapScheduling extends ImapBase implements ImapSchedulingAPI {

    private final static Logger log = LoggerFactory.getLogger(ImapScheduling.class);

    private volatile String userFlag = "cuba-imap";

    @Inject
    private Persistence persistence;

    @Inject
    private TimeSource timeSource;

    @Inject
    private Events events;

    @Inject
    private Authentication authentication;

    protected ForkJoinPool forkJoinPool = new ForkJoinPool();

    protected ConcurrentMap<MailBox, Long> runningTasks = new ConcurrentHashMap<>();

    protected Map<MailBox, Long> lastStartCache = new ConcurrentHashMap<>();

    protected Map<MailBox, Long> lastFinishCache = new ConcurrentHashMap<>();

    @Override
    public void processMailBoxes() {
        authentication.begin();
        try (Transaction tx = persistence.createTransaction()) {
            EntityManager em = persistence.getEntityManager();
            TypedQuery<MailBox> query = em.createQuery(
                    "select distinct b from mailcomponent$MailBox b " +
                            "join fetch b.rootCertificate " +
                            "join fetch b.authentication " +
                            "left join fetch b.folders f " +
                            "left join fetch f.events",
                    MailBox.class
            );
            query.getResultList().forEach(this::processMailBox);
        } finally {
            authentication.end();
        }
    }

    private void processMailBox(MailBox mailBox) {
        if (isRunning(mailBox)) {
            log.trace("{} is running", mailBox);
            return;
        }

        long now = timeSource.currentTimeMillis();

        Long lastStart = lastStartCache.getOrDefault(mailBox, 0L);
        Long lastFinish = lastFinishCache.getOrDefault(mailBox, 0L);

        log.trace("{}\n now={} lastStart={} lastFinish={}", mailBox, now, lastStart, lastFinish);
        if ((lastStart == 0 || lastStart < lastFinish) && now >= lastFinish + mailBox.getPollInterval() * 1000L) {
            lastStartCache.put(mailBox, now);
            forkJoinPool.execute(new MailBoxProcessingTask(mailBox));
        } else {
            log.trace("{}\n time has not come", mailBox);
        }
    }

    private class MailBoxProcessingTask extends RecursiveAction {

        private final MailBox mailBox;

        MailBoxProcessingTask(MailBox mailBox) {
            this.mailBox = mailBox;
        }

        @Override
        protected void compute() {
            log.debug("{}: running", mailBox);

            try {
                Store store = getStore(mailBox);
                List<String> listenedFolders = mailBox.getFolders().stream()
                        .filter(f -> f.hasEvent(PredefinedEventType.NEW_EMAIL.name()))
                        .map(MailFolder::getName)
                        .collect(Collectors.toList());
                List<FolderProcessingTask> folderSubtasks = new LinkedList<>();
                for (String folderName : listenedFolders) {
                    IMAPFolder folder = (IMAPFolder) store.getFolder(folderName);
                    FolderProcessingTask subtask = new FolderProcessingTask(mailBox, folder);
                    folderSubtasks.add(subtask);
                    subtask.fork();
                }
                folderSubtasks.forEach(ForkJoinTask::join);
            } catch (Exception e) {
                throw new RuntimeException(
                        String.format("error processing mailbox %s:%d", mailBox.getHost(), mailBox.getPort()), e
                );
            } finally {
                lastFinishCache.put(mailBox, timeSource.currentTimeMillis());
            }
        }
    }

    private class FolderProcessingTask extends RecursiveAction {

        private final MailBox mailBox;
        private final IMAPFolder folder;

        public FolderProcessingTask(MailBox mailBox, IMAPFolder folder) {
            this.mailBox = mailBox;
            this.folder = folder;
        }

        @Override
        protected void compute() {
            try {
                if ((folder.getType() & Folder.HOLDS_MESSAGES) != 0) {
                    folder.open(Folder.READ_WRITE);
                    Flags supportedFlags = folder.getPermanentFlags();
                    SearchTerm searchTerm = generateSearchTerm(supportedFlags, folder);
                    IMAPMessage[] messages = nullSafeMessages((IMAPMessage[]) (searchTerm != null ? folder.search(searchTerm) : folder.getMessages()));

                    List<RecursiveTask<String>> uidSubtasks = new LinkedList<>();

                    for (IMAPMessage message : messages) {
                        RecursiveTask<String> uidFetch = new RecursiveTask<String>() {
                            @Override
                            protected String compute() {
                                try {
                                    return "" + folder.getUID(message);
                                } catch (Exception e) {
                                    throw new RuntimeException(
                                            String.format("error retrieving uid of message in folder %s of mailbox %s:%d",
                                                    folder.getFullName(), mailBox.getHost(), mailBox.getPort()),
                                            e
                                    );
                                }
                            }
                        };
                        uidSubtasks.add(uidFetch);

                    }

                    for (RecursiveTask<String> uid : uidSubtasks) {
                        events.publish(new NewEmailEvent(mailBox, folder.getFullName(), uid.join()));
                    }
                } else if ((folder.getType() & Folder.HOLDS_FOLDERS) != 0) {
                    List<FolderProcessingTask> subTasks = new LinkedList<>();

                    for (Folder childFolder : folder.list()) {
                        FolderProcessingTask childFolderTask = new FolderProcessingTask(mailBox, (IMAPFolder) childFolder);
                        subTasks.add(childFolderTask);
                        childFolderTask.fork();
                    }

                    subTasks.forEach(ForkJoinTask::join);
                }
            } catch (Exception e) {
                throw new RuntimeException(
                        String.format("error processing folder %s of mailbox %s:%d", folder.getFullName(), mailBox.getHost(), mailBox.getPort()), e
                );
            }
        }
    }

    private IMAPMessage[] nullSafeMessages(IMAPMessage[] messageArray) {
        boolean hasNulls = false;
        for (IMAPMessage message : messageArray) {
            if (message == null) {
                hasNulls = true;
                break;
            }
        }
        if (!hasNulls) {
            return messageArray;
        }
        else {
            List<IMAPMessage> messages = new ArrayList<>();
            for (IMAPMessage message : messageArray) {
                if (message != null) {
                    messages.add(message);
                }
            }
            return messages.toArray(new IMAPMessage[messages.size()]);
        }
    }

    private SearchTerm generateSearchTerm(Flags supportedFlags, Folder folder) {
        SearchTerm searchTerm = null;
        boolean recentFlagSupported = false;
        if (supportedFlags != null) {
            recentFlagSupported = supportedFlags.contains(Flags.Flag.RECENT);
            if (recentFlagSupported) {
                searchTerm = new FlagTerm(new Flags(Flags.Flag.RECENT), true);
            }
            if (supportedFlags.contains(Flags.Flag.ANSWERED)) {
                NotTerm notAnswered = new NotTerm(new FlagTerm(new Flags(Flags.Flag.ANSWERED), true));
                if (searchTerm == null) {
                    searchTerm = notAnswered;
                }
                else {
                    searchTerm = new AndTerm(searchTerm, notAnswered);
                }
            }
            if (supportedFlags.contains(Flags.Flag.DELETED)) {
                NotTerm notDeleted = new NotTerm(new FlagTerm(new Flags(Flags.Flag.DELETED), true));
                if (searchTerm == null) {
                    searchTerm = notDeleted;
                }
                else {
                    searchTerm = new AndTerm(searchTerm, notDeleted);
                }
            }
            if (supportedFlags.contains(Flags.Flag.SEEN)) {
                NotTerm notSeen = new NotTerm(new FlagTerm(new Flags(Flags.Flag.SEEN), true));
                if (searchTerm == null) {
                    searchTerm = notSeen;
                }
                else {
                    searchTerm = new AndTerm(searchTerm, notSeen);
                }
            }
        }

        if (!recentFlagSupported) {
            NotTerm notFlagged;
            if (folder.getPermanentFlags().contains(Flags.Flag.USER)) {
                log.debug("This email server does not support RECENT flag, but it does support " +
                        "USER flags which will be used to prevent duplicates during email fetch." +
                        " This receiver instance uses flag: " + userFlag);
                Flags siFlags = new Flags();
                siFlags.add(userFlag);
                notFlagged = new NotTerm(new FlagTerm(siFlags, true));
            }
            else {
                log.debug("This email server does not support RECENT or USER flags. " +
                        "System flag 'Flag.FLAGGED' will be used to prevent duplicates during email fetch.");
                notFlagged = new NotTerm(new FlagTerm(new Flags(Flags.Flag.FLAGGED), true));
            }
            if (searchTerm == null) {
                searchTerm = notFlagged;
            }
            else {
                searchTerm = new AndTerm(searchTerm, notFlagged);
            }
        }
        return searchTerm;
    }

    private boolean isRunning(MailBox mailBox) {
        Long startTime = runningTasks.get(mailBox);
        if (startTime != null) {
            boolean timedOut = startTime + 15 * 1000 > timeSource.currentTimeMillis();
            if (timedOut) {
                runningTasks.remove(mailBox);
            } else {
                return true;
            }
        }
        return false;
    }
}
