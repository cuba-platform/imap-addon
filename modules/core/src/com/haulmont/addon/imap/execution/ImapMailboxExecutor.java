package com.haulmont.addon.imap.execution;

import com.haulmont.addon.imap.core.FolderKey;
import com.haulmont.addon.imap.core.ImapFunction;
import com.haulmont.addon.imap.core.MailboxKey;
import com.haulmont.addon.imap.exception.ImapException;
import com.sun.mail.imap.IMAPFolder;
import com.sun.mail.imap.IMAPStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.Assert;

import javax.annotation.Nonnull;
import javax.mail.Folder;
import javax.mail.MessagingException;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;
import java.util.function.Supplier;

import static com.haulmont.addon.imap.core.ImapHelper.canHoldMessages;

public class ImapMailboxExecutor implements TaskExecutor {

    private final static Logger log = LoggerFactory.getLogger(ImapMailboxExecutor.class);

    private static final int MAX_QUEUE_SIZE = 1024;
    private static final int MAX_EXECUTORS_NUM = 10;

    final MailboxKey mailboxKey;
    private final ImapFunction<MailboxKey, IMAPStore> storeBuilder;
    private volatile IMAPStore store;
    private AtomicInteger maxExecutorsNum;
    private final ExecutorService folderBulkExecutor;

    ImapMailboxExecutor(MailboxKey mailboxKey, ImapFunction<MailboxKey, IMAPStore> storeBuilder) {
        this.mailboxKey = mailboxKey;
        this.storeBuilder = storeBuilder;
        maxExecutorsNum = new AtomicInteger(MAX_EXECUTORS_NUM);
        folderBulkExecutor = Executors.newFixedThreadPool(MAX_EXECUTORS_NUM, new ThreadFactory() {
            private final AtomicInteger threadNumber = new AtomicInteger(1);

            @Override
            public Thread newThread(@Nonnull Runnable r) {
                Thread thread = new Thread(
                        r, String.format("[MailboxExec %s]FolderBulk-%d", mailboxKey, threadNumber.getAndIncrement())
                );
                thread.setDaemon(true);
                return thread;
            }
        });
    }

    private final ConcurrentMap<String, ImapFolderExecutor> folderExecutors = new ConcurrentHashMap<>(MAX_EXECUTORS_NUM);
    private final Queue<DelayableTask> taskQueue = new ConcurrentLinkedQueue<>();
    private final AtomicInteger queueSize = new AtomicInteger(0);

    private final Lock lock = new ReentrantLock();
    private final Condition availableExecutor = lock.newCondition();

    void resetStore(Supplier<IMAPStore> newStore) {
        log.debug("[MailboxExec <{}>] resetting IMAP store", mailboxKey);
        lock.lock();
        try {
            doWithFoldersExecutors(ImapFolderExecutor::suspend).get();
            log.debug("[MailboxExec <{}>] all ({}) executors have been suspended, do reset of IMAP store",
                        mailboxKey, folderExecutors.size());
            IMAPStore currentStore = this.store;
            if (currentStore != null) {
                try {
                    currentStore.close();
                } catch (MessagingException e) {
                    log.warn(String.format("[MailboxExec <%s>] do close of IMAP store error", mailboxKey), e);
                }
            }
            this.store = newStore.get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("was interrupted", e);
        } catch (ExecutionException e) {
            String message = e.getMessage() != null ? e.getMessage() : "can't reset store";
            if (e.getCause() != null) {
                throw new RuntimeException(message, e.getCause());
            }
            throw new RuntimeException(message);
        } finally {
            lock.unlock();
        }

        for (ImapFolderExecutor folderExecutor : folderExecutors.values()) {
            log.trace("[MailboxExec <{}>] resuming folder executor for {} after to reset of IMAP store",
                    mailboxKey, folderExecutor.folderKey.getFolderFullName());
            folderExecutor.resume();
        }
    }

    void closeStore() {
        log.debug("[MailboxExec <{}>] close IMAP store", mailboxKey);
        lock.lock();
        try {
            doWithFoldersExecutors(ImapFolderExecutor::close).get();
            log.debug("[MailboxExec <{}>] all ({}) executors have been suspended, do close of IMAP store",
                    mailboxKey, folderExecutors.size());
            IMAPStore currentStore = this.store;
            if (currentStore != null) {
                try {
                    currentStore.close();
                } catch (MessagingException e) {
                    log.warn(String.format("[MailboxExec %s] do close of IMAP store error", mailboxKey), e);
                }
            }
            this.store = null;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("was interrupted", e);
        } catch (ExecutionException e) {
            String message = e.getMessage() != null ? e.getMessage() : "can't reset store";
            if (e.getCause() != null) {
                throw new RuntimeException(message, e.getCause());
            }
            throw new RuntimeException(message);
        } finally {
            lock.unlock();
        }

        try {
            folderBulkExecutor.shutdownNow();
        } catch (Exception e) {
            log.warn("Exception while shutting down executor", e);
        }
    }

    private CompletableFuture<?> doWithFoldersExecutors(Consumer<ImapFolderExecutor> action) {
        if (folderExecutors.isEmpty()) {
            log.debug("[MailboxExec <{}>] no folder executors - do reset of IMAP store", mailboxKey);
            return CompletableFuture.completedFuture(true);
        }
        List<CompletableFuture<?>> suspensions = new ArrayList<>(folderExecutors.size());
        for (ImapFolderExecutor folderExecutor : folderExecutors.values()) {
            log.trace("[MailboxExec <{}>] suspend folder executor for {} due to resetStore request",
                    mailboxKey, folderExecutor.folderKey.getFolderFullName());
            suspensions.add( CompletableFuture.runAsync(() -> action.accept(folderExecutor), folderBulkExecutor) );
        }
        return CompletableFuture.allOf(suspensions.toArray(new CompletableFuture[0]));
    }

    public <T> T invokeImmediate(ImmediateTask<T> task) {
        log.trace("[MailboxExec <{}>] invoke {}", mailboxKey, task.description);
        Assert.isTrue(mailboxKey.equals(task.folder.getMailboxKey()),
                "wrong folder " + task.folder + " for exec of " + mailboxKey);
        TaskExecutor folderExecutor = executorForImmediateTask(task.folder.getFolderFullName());
        if (folderExecutor == null) {
            log.debug("[MailboxExec <{}>] invoking {}: no available executor, wait for it", mailboxKey, task.description);
            TaskExecutor.waitFor(lock, availableExecutor);
            return invokeImmediate(task);
        }

        try {
            log.debug("[MailboxExec <{}>] do invoke {}", mailboxKey, task.description);
            return folderExecutor.invokeImmediate(task);
        } finally {
            lock.lock();
            try {
                log.debug("[MailboxExec <{}>] invoking {}: notify about available executor", mailboxKey, task);
                availableExecutor.signal();
            } finally {
                lock.unlock();
            }
        }
    }

    private TaskExecutor executorForImmediateTask(String folderFullName) {
        log.debug("[MailboxExec <{}>] find executor for folder {} for immediate task", mailboxKey, folderFullName);
        ImapFolderExecutor folderExecutor = readyToWorkFolderExecutor(folderFullName);
        if (folderExecutor != null) {
            log.debug("[MailboxExec <{}>] finding executor for folder {} for immediate task: there is one ready to work",
                    mailboxKey, folderFullName);

            return folderExecutor;
        }

        log.debug("[MailboxExec <{}>] finding executor for folder {} for immediate task: trying to suspend least busy",
                mailboxKey, folderFullName);
        lock.lock();
        try {
            ImapFolderExecutor suspendingExecutor = null;
            Integer blockNum = Integer.MAX_VALUE;
            for (ImapFolderExecutor executor : folderExecutors.values()) {
                log.debug(
                        "[MailboxExec <{}>] finding executor for folder {} for immediate task->trying to suspend least" +
                        " busy->probing exec for < {} >", mailboxKey, folderFullName, executor.folderKey);
                if (executor.suspended.get()) {
                    log.trace(
                            "[MailboxExec <{}>] finding executor for folder {} for immediate task->trying to suspend least" +
                            " busy->exec for < {} > is already suspended", mailboxKey, folderFullName, executor.folderKey);
                    continue;
                }
                int currentBlockNum;
                if ((currentBlockNum = executor.blockingNumber.get()) == 0) {
                    log.debug(
                            "[MailboxExec <{}>] finding executor for folder {} for immediate task->trying to suspend least" +
                            " busy->exec for < {} > has no blocking, use it", mailboxKey, folderFullName, executor.folderKey);

                    suspendingExecutor = executor;
                    break;
                } else if (currentBlockNum < blockNum) {
                    log.trace(
                            "[MailboxExec <{}>] finding executor for folder {} for immediate task->trying to suspend least" +
                            " busy->exec for < {} > has less blocks ({}) than {}",
                            mailboxKey, folderFullName, executor.folderKey, currentBlockNum, blockNum);

                    blockNum = currentBlockNum;
                    suspendingExecutor = executor;
                }
            }
            if (suspendingExecutor == null) {
                log.debug("[MailboxExec <{}>] finding executor for folder {} for immediate task: no executor to suspend",
                        mailboxKey, folderFullName);
                return null;
            }
            log.debug("[MailboxExec <{}>] finding executor for folder {} for immediate task: suspending exec for < {} >",
                    mailboxKey, folderFullName, suspendingExecutor.folderKey);
            suspendingExecutor.suspend();
            ImapFolderExecutor finalSuspendingExecutor = suspendingExecutor;
            return new TaskExecutor() {
                @Override
                public <T> T invokeImmediate(ImmediateTask<T> task) {
                    ImapFolderExecutor folderExecutor = null;
                    try {
                        folderExecutor = buildExecutor(folderFullName);
                        log.debug("[MailboxExec <{}>] invoke task {} while suspending exec for < {} >",
                                mailboxKey, task.description, finalSuspendingExecutor.folderKey);
                        return folderExecutor.invokeImmediate(task);
                    } finally {
                        try {
                            log.trace("[MailboxExec <{}>] resume exec for < {} > after invocation of task {} ",
                                    mailboxKey, finalSuspendingExecutor.folderKey, task.description);
                            finalSuspendingExecutor.resume();
                        } finally {
                            if (folderExecutor != null) {
                                log.debug("[MailboxExec <{}>] suspend dedicated exec for task {} after resuming exec of < {} >",
                                        mailboxKey, task.description, finalSuspendingExecutor.folderKey);
                                folderExecutor.suspend();
                            }
                        }
                    }
                }

                @Override
                public <T> boolean submitDelayable(DelayableTask<T> task) {
                    return false;
                }
            };

        } finally {
            lock.unlock();
        }
    }

    public <T> boolean submitDelayable(DelayableTask<T> task) {
        log.trace("[MailboxExec <{}>] submit {}", mailboxKey, task.description);
        ImapFolderExecutor folderExecutor = readyToWorkFolderExecutor(task.folder.getFolderFullName());
        if (folderExecutor != null) {
            log.debug(task.description);
            return folderExecutor.submitDelayable(task);
        }

        synchronized (queueSize) {
            if (queueSize.get() >= MAX_QUEUE_SIZE) {
                log.debug("[MailboxExec <{}>] submitting {}: queue is full, reject the task",
                        mailboxKey, task.description);
                return false;
            }

            log.debug("[MailboxExec <{}>] submitting {}: queue has space, put the task", mailboxKey, task.description);
            queueSize.incrementAndGet();
            taskQueue.add(task);
        }

        return true;
    }

    private ImapFolderExecutor readyToWorkFolderExecutor(String folderName) {
        log.trace("[MailboxExec <{}>] find folder executor ready to work for {}", mailboxKey, folderName);
        lock.lock();
        try {
            ImapFolderExecutor executor = folderExecutors.get(folderName);
            if (executor != null) {
                log.debug("[MailboxExec <{}>] finding folder executor ready to work for {}: exist already",
                        mailboxKey, folderName);
                return executor;
            }
            log.debug("[MailboxExec <{}>] finding folder executor ready to work for {}: " +
                            "try to create new, current pool size {}, max pool size {}",
                    mailboxKey, folderName, folderExecutors.size(), maxExecutorsNum.get());
            if (folderExecutors.size() < maxExecutorsNum.get()) {
                log.debug("[MailboxExec <{}>] finding folder executor ready to work for {}: there is space to create new",
                        mailboxKey, folderName);
                try {
                    ImapFolderExecutor oldVal = folderExecutors.put(folderName, executor = buildExecutor(folderName));
                    Assert.isNull(oldVal, "already has executor for " + folderName);
                    return executor;
                } catch (ImapException e) {
                    log.warn(String.format("[MailboxExec %s] Can't build folder executor due to error, will decrease pool size", mailboxKey), e);
                    maxExecutorsNum.decrementAndGet();
                    return null;
                }
            }

            log.debug("[MailboxExec <{}>] finding folder executor ready to work for {}: there is no available",
                    mailboxKey, folderName);
            maxExecutorsNum.incrementAndGet();
            return null;
        } finally {
            lock.unlock();
        }
    }

    private ImapFolderExecutor buildExecutor(String folderName) {
        log.debug("[MailboxExec <{}>] build folder executor for {}", mailboxKey, folderName);
        return new ImapFolderExecutor(new FolderKey(mailboxKey, folderName), this::folder, thisFolderExecutor -> {
            log.trace("[MailboxExec <{}>] finish? exec of < {} > ", mailboxKey, folderName);
            if (folderExecutors.remove(folderName) != null) {
                log.debug("[MailboxExec <{}>] finishing? exec of < {} >: trying to distribute tasks from queue after" +
                        " removing exec from pool",  mailboxKey, folderName);
                synchronized (queueSize) {
                    List<DelayableTask> rejected = new LinkedList<>();

                    DelayableTask<?> task;
                    while ((task = taskQueue.poll()) != null) {
                        if (!submitDelayable(task)) {
                            rejected.add(task);
                        }
                    }
                    if (log.isDebugEnabled()) {
                        log.debug("[MailboxExec <{}>] finishing? exec of < {} >: trying to distribute tasks from queue" +
                                        " after removing exec from pool: {} have been submitted",
                                mailboxKey, folderName, queueSize.get() - rejected.size());
                    }
                    taskQueue.addAll(rejected);
                    queueSize.set(rejected.size());
                }
            }

            lock.lock();
            try {
                if (folderExecutors.size() >= maxExecutorsNum.get()) {
                    log.debug("[MailboxExec <{}>] finishing? exec of < {} >: pool is full, finish the executor ",
                            mailboxKey, folderName);
                    return;
                }

                Assert.isNull(folderExecutors.put(folderName, thisFolderExecutor),
                        "already has executor for " + folderName);
                log.debug("[MailboxExec <{}>] finishing? exec of < {} >: pool is full, no other tasks to do, remain executor ",
                        mailboxKey, folderName);
                thisFolderExecutor.resume();
            } finally {
                lock.unlock();
            }
        });
    }

    private IMAPFolder folder(FolderKey folderKey) {
        log.trace("build IMAP folder for {}", folderKey);
        Assert.isTrue(folderKey.getMailboxKey().equals(mailboxKey),
                "wrong folder " + folderKey + " for exec of " + mailboxKey);
        lock.lock();
        try {
            if (store == null) {
                store = storeBuilder.apply(mailboxKey);
            }
            IMAPFolder folder = (IMAPFolder) store.getFolder(folderKey.getFolderFullName());
            if (!folder.isOpen() && canHoldMessages(folder)) {
                folder.open(Folder.READ_WRITE);
            }
            return folder;
        } catch (MessagingException e) {
            throw new ImapException(e);
        } finally {
            lock.unlock();
        }
    }

}
