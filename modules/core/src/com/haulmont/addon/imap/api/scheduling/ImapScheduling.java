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
import org.springframework.util.CollectionUtils;

import javax.annotation.Nonnull;
import javax.annotation.PreDestroy;
import javax.inject.Inject;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

@Component(ImapSchedulingAPI.NAME)
@SuppressWarnings({"CdiInjectionPointsInspection", "SpringJavaAutowiredFieldsWarningInspection", "SpringJavaInjectionPointsAutowiringInspection"})
public class ImapScheduling implements ImapSchedulingAPI {

    private final static Logger log = LoggerFactory.getLogger(ImapScheduling.class);

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

    private ConcurrentMap<ImapMailBox, Long> runningTasks = new ConcurrentHashMap<>();
    private Map<ImapMailBox, Long> lastStartCache = new ConcurrentHashMap<>();
    private Map<ImapMailBox, Long> lastFinishCache = new ConcurrentHashMap<>();
    private ConcurrentMap<ImapMailBox, ExecutorService> executors = new ConcurrentHashMap<>();

    @Override
    public void processMailBoxes() {
        authentication.begin();
        try (Transaction ignored = persistence.createTransaction()) {
            EntityManager em = persistence.getEntityManager();
            TypedQuery<ImapMailBox> query = em.createQuery(
                    "select distinct b from imapcomponent$ImapMailBox b",
                    ImapMailBox.class
            ).setViewName("imap-mailbox-edit");
            query.getResultList().forEach(this::processMailBox);
        } finally {
            authentication.end();
        }
    }

    @PreDestroy
    public void shutdownExecutors() {
        executors.forEach((mailBox, executor) -> {
            try {
                executor.shutdownNow();
            } catch (Exception e) {
                log.warn("Exception while shutting down executor for " + mailBox, e);
            }
        });
    }

    private void processMailBox(ImapMailBox mailBox) {
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
            log.info("Fire mailbox processing task for {}", mailBox);
            executors.putIfAbsent(mailBox, Executors.newCachedThreadPool(new ThreadFactory() {
                private final AtomicInteger threadNumber = new AtomicInteger(1);
                @Override
                public Thread newThread(@Nonnull Runnable r) {
                    Thread thread = new Thread(r, "ImapMailBoxSync-" + mailBox.getId() + "-" + threadNumber.getAndIncrement());
                    thread.setDaemon(true);
                    return thread;
                }
            }));
            executors.get(mailBox).submit(new MailBoxProcessingTask(mailBox));
        } else {
            log.trace("{}\n time has not come", mailBox);
        }
    }

    private class MailBoxProcessingTask implements Runnable {

        private final ImapMailBox mailBox;

        MailBoxProcessingTask(ImapMailBox mailBox) {
            this.mailBox = mailBox;
        }

        @Override
        public void run() {
            log.debug("{}: running", mailBox);
            List<ImapFolder> listenedFolders = mailBox.getFolders().stream()
                    .filter(f -> f.getEvents() != null && !f.getEvents().isEmpty())
                    .collect(Collectors.toList());
            List<Future<?>> folderSubtasks = new ArrayList<>(listenedFolders.size() * 3);
            try {
                Collection<ImapFolderDto> allFolders = imapAPI.fetchFolders(mailBox);
                for (ImapFolder cubaFolder : mailBox.getProcessableFolders()) {
                    IMAPFolder imapFolder = allFolders.stream()
                            .filter(f -> f.getName().equals(cubaFolder.getName()))
                            .findFirst()
                            .map(ImapFolderDto::getImapFolder)
                            .orElse(null);

                    if (imapFolder == null) {
                        log.info("Can't find folder {}. Probably it was removed", cubaFolder.getName());
                        continue;
                    }
                    if (cubaFolder.hasEvent(ImapEventType.NEW_EMAIL)) {
                        log.info("Search new messages for {}", cubaFolder);

                        NewMessagesInFolderTask subtask = new NewMessagesInFolderTask(
                                mailBox, cubaFolder, ImapScheduling.this
                        );
                        folderSubtasks.add(executors.get(mailBox).submit(subtask));
                    }
                    if (cubaFolder.hasEvent(ImapEventType.EMAIL_SEEN) ||
                            cubaFolder.hasEvent(ImapEventType.FLAGS_UPDATED) ||
                            cubaFolder.hasEvent(ImapEventType.NEW_ANSWER) ||
                            cubaFolder.hasEvent(ImapEventType.NEW_THREAD)) {
                        log.info("Update messages for {}", cubaFolder);

                        UpdateMessagesInFolderTask updateSubtask = new UpdateMessagesInFolderTask(
                                mailBox, cubaFolder, ImapScheduling.this
                        );
                        folderSubtasks.add(executors.get(mailBox).submit(updateSubtask));
                    }
                    if (cubaFolder.hasEvent(ImapEventType.EMAIL_DELETED) ||
                            cubaFolder.hasEvent(ImapEventType.EMAIL_MOVED)) {
                        log.info("Track deleted/moved messages for {}", cubaFolder);

                        MissedMessagesInFolderTask missedMessagesTask = new MissedMessagesInFolderTask(
                                mailBox, cubaFolder, ImapScheduling.this, allFolders
                        );
                        folderSubtasks.add(executors.get(mailBox).submit(missedMessagesTask));
                    }
                }
                long timeoutTs = timeSource.currentTimeMillis() + mailBox.getProcessingTimeout() * 1000 + 50;
                for (Future<?> task : folderSubtasks) {
                    task.get(timeoutTs - timeSource.currentTimeMillis(), TimeUnit.MILLISECONDS);
                }
            } catch (InterruptedException interruped) {
                log.info("Processing of {} was interrupted", mailBox);
                folderSubtasks.forEach(task -> {
                    if (!task.isDone() && !task.isCancelled()) {
                        task.cancel(true);
                    }
                });
                Thread.currentThread().interrupt();
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
        log.debug("Fire events {} for {}", imapEvents, folder);
        imapEvents.forEach(event -> {
            events.publish(event);

            ImapEventType.getByEventType(event.getClass()).stream()
                    .map(folder::getEvent)
                    .filter(Objects::nonNull)
                    .map(ImapFolderEvent::getEventHandlers)
                    .filter(handlers -> !CollectionUtils.isEmpty(handlers))
                    .forEach(handlers -> invokeAttachedHandlers(event, folder, handlers));

        });
    }

    private void invokeAttachedHandlers(BaseImapEvent event, ImapFolder folder, List<ImapEventHandler> handlers) {
        log.trace("{}: invoking handlers {} for event {}", folder.getName(), handlers, event);

        handlers.forEach(handler -> {
            Object bean = AppBeans.get(handler.getBeanName());
            if (bean == null) {
                log.warn("No bean {} is available, check the folder {} configuration", handler.getBeanName(), folder);
                return;
            }
            Class<? extends BaseImapEvent> eventClass = event.getClass();
            try {
                authentication.begin();
                List<Method> methods = Arrays.stream(bean.getClass().getMethods())
                        .filter(m -> m.getName().equals(handler.getMethodName()))
                        .filter(m -> m.getParameterTypes().length == 1 && m.getParameterTypes()[0].isAssignableFrom(eventClass))
                        .collect(Collectors.toList());
                log.trace("{}: methods to invoke: {}", handler, methods);
                if (methods.isEmpty()) {
                    log.warn("No method {} for bean {} is available, check the folder {} configuration",
                            handler.getMethodName(), handler.getBeanName(), folder);
                }
                for (Method method : methods) {
                    method.invoke(bean, event);
                }
            } catch (InvocationTargetException | IllegalAccessException e) {
                throw new RuntimeException("Can't invoke bean for imap folder event", e);
            } finally {
                authentication.end();
            }
        });

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
