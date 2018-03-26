package com.haulmont.addon.imap.api.scheduling;

import com.haulmont.addon.imap.api.ImapAPI;
import com.haulmont.addon.imap.config.ImapConfig;
import com.haulmont.addon.imap.core.ImapHelper;
import com.haulmont.addon.imap.dto.ImapFolderDto;
import com.haulmont.addon.imap.entity.*;
import com.haulmont.addon.imap.events.*;
import com.haulmont.cuba.core.EntityManager;
import com.haulmont.cuba.core.Persistence;
import com.haulmont.cuba.core.Transaction;
import com.haulmont.cuba.core.TypedQuery;
import com.haulmont.cuba.core.global.AppBeans;
import com.haulmont.cuba.core.global.Events;
import com.haulmont.cuba.core.global.Metadata;
import com.haulmont.cuba.core.global.TimeSource;
import com.haulmont.cuba.security.app.Authentication;
import com.sun.mail.imap.IMAPFolder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import javax.inject.Inject;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;

@Component(ImapSchedulingAPI.NAME)
public class ImapScheduling implements ImapSchedulingAPI {

    private final static Logger LOG = LoggerFactory.getLogger(ImapScheduling.class);

    @Inject
    Persistence persistence;

    @Inject
    private Events events;

    @Inject
    Authentication authentication;

    @Inject
    ImapHelper imapHelper;

    @Inject
    ImapConfig config;

    @Inject
    Metadata metadata;

    @Inject
    private TimeSource timeSource;

    @Inject
    private ImapAPI imapAPI;

    private ForkJoinPool forkJoinPool = new ForkJoinPool();
    private ConcurrentMap<ImapMailBox, Long> runningTasks = new ConcurrentHashMap<>();
    private Map<ImapMailBox, Long> lastStartCache = new ConcurrentHashMap<>();
    private Map<ImapMailBox, Long> lastFinishCache = new ConcurrentHashMap<>();

    @Override
    public void processMailBoxes() {
        authentication.begin();
        try (Transaction tx = persistence.createTransaction()) {
            EntityManager em = persistence.getEntityManager();
            TypedQuery<ImapMailBox> query = em.createQuery(
                    "select distinct b from imapcomponent$ImapMailBox b " +
                            "left join fetch b.rootCertificate " +
                            "join fetch b.authentication " +
                            "left join fetch b.folders",
                    ImapMailBox.class
            );
            query.getResultList().stream().flatMap(mb -> mb.getFolders().stream()).forEach(f -> f.getEvents().size());
            query.getResultList().stream().flatMap(mb -> mb.getFolders().stream()).forEach(f -> f.getMailBox().getPollInterval());
            query.getResultList().forEach(this::processMailBox);
        } finally {
            authentication.end();
        }
    }

    private void processMailBox(ImapMailBox mailBox) {
        if (isRunning(mailBox)) {
            LOG.trace("{} is running", mailBox);
            return;
        }

        long now = timeSource.currentTimeMillis();

        Long lastStart = lastStartCache.getOrDefault(mailBox, 0L);
        Long lastFinish = lastFinishCache.getOrDefault(mailBox, 0L);

        LOG.trace("{}\n now={} lastStart={} lastFinish={}", mailBox, now, lastStart, lastFinish);
        if ((lastStart == 0 || lastStart < lastFinish) && now >= lastFinish + mailBox.getPollInterval() * 1000L) {
            lastStartCache.put(mailBox, now);
            LOG.info("Fire mailbox processing task for {}", mailBox);
            forkJoinPool.execute(new MailBoxProcessingTask(mailBox));
        } else {
            LOG.trace("{}\n time has not come", mailBox);
        }
    }

    private class MailBoxProcessingTask extends RecursiveAction {

        private final ImapMailBox mailBox;

        MailBoxProcessingTask(ImapMailBox mailBox) {
            this.mailBox = mailBox;
        }

        @Override
        protected void compute() {
            LOG.debug("{}: running", mailBox);
            try {
                List<ImapFolder> listenedFolders = mailBox.getFolders().stream()
                        .filter(f -> f.getEvents() != null && !f.getEvents().isEmpty())
                        .collect(Collectors.toList());
                List<RecursiveAction> folderSubtasks = new ArrayList<>(listenedFolders.size() * 2);
                Collection<ImapFolderDto> allFolders = imapAPI.fetchFolders(mailBox);
                for (ImapFolder cubaFolder : mailBox.getFolders()) {
                    IMAPFolder imapFolder = allFolders.stream()
                            .filter(f -> f.getName().equals(cubaFolder.getName()))
                            .findFirst()
                            .map(ImapFolderDto::getImapFolder)
                            .orElse(null);

                    if (imapFolder == null) {
                        LOG.info("Can't find folder {}. Probably it was removed", cubaFolder.getName());
                        continue;
                    }
                    if (cubaFolder.hasEvent(ImapEventType.NEW_EMAIL)) {
                        LOG.info("Search new messages for {}", cubaFolder);

                        NewMessagesInFolderTask subtask = new NewMessagesInFolderTask(
                                mailBox, cubaFolder, imapFolder, ImapScheduling.this
                        );
                        folderSubtasks.add(subtask);
                        subtask.fork();
                    }
                    if (cubaFolder.hasEvent(ImapEventType.EMAIL_SEEN) ||
                            cubaFolder.hasEvent(ImapEventType.FLAGS_UPDATED) ||
                            cubaFolder.hasEvent(ImapEventType.NEW_ANSWER) ||
                            cubaFolder.hasEvent(ImapEventType.NEW_THREAD)) {
                        LOG.info("Update messages for {}", cubaFolder);

                        UpdateMessagesInFolderTask updateSubtask = new UpdateMessagesInFolderTask(
                                mailBox, cubaFolder, imapFolder, ImapScheduling.this
                        );
                        folderSubtasks.add(updateSubtask);
                        updateSubtask.fork();
                    }
                    if (cubaFolder.hasEvent(ImapEventType.EMAIL_DELETED) ||
                            cubaFolder.hasEvent(ImapEventType.EMAIL_MOVED)) {
                        LOG.info("Track deleted/moved messages for {}", cubaFolder);

                        MissedMessagesInFolderTask missedMessagesTask = new MissedMessagesInFolderTask(
                                mailBox, cubaFolder, imapFolder, ImapScheduling.this, allFolders
                        );
                        folderSubtasks.add(missedMessagesTask);
                        missedMessagesTask.fork();
                    }
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

    void fireEvents(ImapFolder folder, Collection<? extends BaseImapEvent> imapEvents) {
        LOG.debug("Fire events {} for {}", imapEvents, folder);
        imapEvents.forEach(event -> {
            ImapEventType.getByEventType(event.getClass()).stream()
                    .map(folder::getEvent)
                    .filter(Objects::nonNull)
                    .filter(folderEvent -> folderEvent.getMethodName() != null)
                    .forEach(folderEvent -> invokeAttachedHandler(event, folderEvent));

            events.publish(event);
        });
    }

    private void invokeAttachedHandler(BaseImapEvent event, ImapFolderEvent folderEvent) {
        LOG.trace("{}: invoking bean", folderEvent);
        Object bean = AppBeans.get(folderEvent.getBeanName());
        Class<? extends BaseImapEvent> eventClass = folderEvent.getEvent().getEventClass();
        try {
            authentication.begin();
            List<Method> methods = Arrays.stream(bean.getClass().getMethods())
                    .filter(m -> m.getName().equals(folderEvent.getMethodName()))
                    .filter(m -> m.getParameterTypes().length == 1 && m.getParameterTypes()[0].isAssignableFrom(eventClass))
                    .collect(Collectors.toList());
            LOG.trace("{}: methods to invoke: {}", folderEvent, methods);
            for (Method method : methods) {
                method.invoke(bean, event);
            }
        } catch (InvocationTargetException | IllegalAccessException e) {
            throw new RuntimeException("Can't invoke bean for imap folder event", e);
        } finally {
            authentication.end();
        }
    }

    private boolean isRunning(ImapMailBox mailBox) {
        Long startTime = runningTasks.get(mailBox);
        if (startTime != null) {
            boolean timedOut = startTime + mailBox.getProcessingTimeout() * 1000 > timeSource.currentTimeMillis();
            if (timedOut) {
                runningTasks.remove(mailBox);
            } else {
                return true;
            }
        }
        return false;
    }
}
