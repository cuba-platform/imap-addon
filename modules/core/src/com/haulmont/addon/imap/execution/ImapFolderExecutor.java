package com.haulmont.addon.imap.execution;

import com.haulmont.addon.imap.core.FolderKey;
import com.haulmont.addon.imap.exception.ImapException;
import com.haulmont.bali.datastruct.Pair;
import com.haulmont.cuba.core.global.AppBeans;
import com.haulmont.cuba.security.app.Authentication;
import com.sun.mail.imap.IMAPFolder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.mail.MessagingException;
import java.util.Queue;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;
import java.util.function.Function;

public class ImapFolderExecutor implements TaskExecutor {

    private final static Logger log = LoggerFactory.getLogger(ImapFolderExecutor.class);

    private static final int MAX_QUEUE_SIZE = 1024;

    final FolderKey folderKey;
    private final Function<FolderKey, IMAPFolder> folderBuilder;
    private final Consumer<ImapFolderExecutor> iamDone;
    private volatile IMAPFolder folder;

    private final Queue<DelayableTask> taskQueue = new ConcurrentLinkedQueue<>();
    private final AtomicInteger queueSize = new AtomicInteger(0);
    private volatile DelayableTask currentDelayable;
    private final ExecutorService delayablesExecutor;
    private final ExecutorService callbackExecutor;

    final AtomicInteger blockingNumber = new AtomicInteger(0);

    private final Lock lock = new ReentrantLock();
    private final Condition isFree = lock.newCondition();
    private final Condition isReleased = lock.newCondition();
    private final Condition isSuspended = lock.newCondition();
    private final Condition isResumed = lock.newCondition();

    final AtomicBoolean suspended = new AtomicBoolean(false);

    ImapFolderExecutor(FolderKey folderKey, Function<FolderKey, IMAPFolder> folderBuilder, Consumer<ImapFolderExecutor> iamDone) {
        this.folderKey = folderKey;
        this.folderBuilder = folderBuilder;
        this.iamDone = iamDone;

        this.folder = folderBuilder.apply(folderKey);
        this.delayablesExecutor = Executors.newSingleThreadExecutor(r -> {
            Thread thread = new Thread(r, String.format("[FolderExec %s][Delayble]", folderKey));
            thread.setDaemon(true);
            return thread;
        });
        this.callbackExecutor = Executors.newSingleThreadExecutor(r -> {
            Thread thread = new Thread(r, String.format("[FolderExec %s][CallBack]", folderKey));
            thread.setDaemon(true);
            return thread;
        });
    }

    public <T> T invokeImmediate(ImmediateTask<T> task) {
        log.trace("[FolderExec <{}>] invoke {}", folderKey, task.description);
        if (blockingNumber.incrementAndGet() == 1) {
            try {
                log.trace("[FolderExec <{}>] invoking {}: wait for resume", folderKey, task.description);
                waitForResumeIfNeed();
                log.debug("[FolderExec <{}>] do invoke {}", folderKey, task.description);
                return task.action.apply(folder());
            } catch (MessagingException e) {
                throw new ImapException(e);
            } finally {
                finishTask();
            }
        } else {
            if (blockingNumber.decrementAndGet() > 0) {
                log.debug("[FolderExec <{}>] invoking {}: waiting for other blocking tasks", folderKey, task.description);
                wait(lock, isFree);
            }
            return invokeImmediate(task);
        }
    }

    public <T> boolean submitDelayable(DelayableTask<T> task) {
        log.trace("[FolderExec <{}>] submit {}", folderKey, task.description);
        if (queueSize.get() >= MAX_QUEUE_SIZE) {
            log.debug("[FolderExec <{}>] submitting {}: no room for task in queue", folderKey, task.description);
            return false;
        }

        log.debug("[FolderExec <{}>] submitting {}: add to queue and trigger invocation", folderKey, task.description);
        queueSize.incrementAndGet();
        taskQueue.add(task);
        maybeInvokeDelayable();
        return true;
    }

    boolean suspend() {
        log.debug("[FolderExec <{}>] suspend", folderKey);
        boolean acquired = suspended.compareAndSet(false, true);
        log.debug("[FolderExec <{}>] suspending: {}", folderKey, acquired);
        if (acquired) {
            if (blockingNumber.get() > 0) {
                log.debug("[FolderExec <{}>] suspending: wait for suspension", folderKey);
                wait(lock, isSuspended);
            }
            if (folder != null) {
                try {
                    log.trace("[FolderExec <{}>] suspending: close folder", folderKey);
                    folder.close(false);
                } catch (MessagingException e) {
                    log.warn(String.format("[FolderExec <%s>] suspending: close folder failure", folderKey), e);
                }
            }
            folder = null;
        }
        return acquired;
    }

    void close() {
        log.info("[FolderExec <{}>] close", folderKey);
        suspend();

        try {
            delayablesExecutor.shutdownNow();
        } catch (Exception e) {
            log.warn("Exception while shutting down delayable executor", e);
        }
        try {
            callbackExecutor.shutdownNow();
        } catch (Exception e) {
            log.warn("Exception while shutting down callback executor", e);
        }
    }

    void resume() {
        log.debug("[FolderExec <{}>] resume", folderKey);
        suspended.set(false);
        notifyAll(isResumed);
    }

    private void maybeInvokeDelayable() {
        log.trace("[FolderExec <{}>] try to invoke task from queue", folderKey);
        if (currentDelayable == null && (currentDelayable = taskQueue.poll()) != null) {
            log.debug("[FolderExec <{}>] trying to invoke task from queue: perform {}",
                    folderKey, currentDelayable.description);
            queueSize.decrementAndGet();

            //noinspection unchecked
            submitDelayableTask(currentDelayable);
        }
    }

    private <T> void submitDelayableTask(DelayableTask<T> task) {
        CompletableFuture.supplyAsync(
                () -> doInvokeDelayable(task), delayablesExecutor
        ).thenAcceptAsync(result -> {
            if (Boolean.TRUE.equals(result.getFirst())) {
                task.callback.accept(result.getSecond());
            }
        }, callbackExecutor);
    }

    private <T> Pair<Boolean, T> doInvokeDelayable(DelayableTask<T> task) {
        log.debug("[FolderExec <{}>] invoke task from queue {}", folderKey, currentDelayable.description);
        if (blockingNumber.incrementAndGet() == 1) {
            Authentication authentication = AppBeans.get(Authentication.class);
            authentication.begin();
            try {
                log.trace("[FolderExec <{}>] invoking task from queue {}: wait for resume", folderKey, task.description);
                waitForResumeIfNeed();
                log.debug("[FolderExec <{}>] do invoke task from queue {}", folderKey, task.description);
                T result = task.action.apply(folder());
                log.debug("[FolderExec <{}>] invoking task from queue {}: result is {}", folderKey, task.description, result);
                return new Pair<>( true, result);
            } catch (/*MessagingException*/Exception e) {
                log.error(
                        String.format("[FolderExec %s] do invoke task from queue %s failed with error",
                                folderKey, task.description),
                        e
                );
                return new Pair<>(false, null);
            } finally {
                finishTask();
                currentDelayable = null;
                maybeInvokeDelayable();
                authentication.end();
            }
        } else {
            if (blockingNumber.decrementAndGet() > 0) {
                log.debug("[FolderExec <{}>] invoking task from queue {}: waiting for other blocking tasks",
                        folderKey, task.description);
                wait(lock, isReleased);
            }
            return doInvokeDelayable(task);
        }
    }

    private void waitForResumeIfNeed() {
        if (suspended.get()) {
            log.trace("[FolderExec <{}>] do wait for resume", folderKey);
            notifyAll(isSuspended);
            wait(lock, isResumed);
        }
    }

    private void notifyAll(Condition condition) {
        lock.lock();
        try {
            condition.signalAll();
        } finally {
            lock.unlock();
        }
    }

    private void finishTask() {
        log.trace("[FolderExec <{}>] finish task", folderKey);
        lock.lock();
        boolean finished = false;
        try {
            if (blockingNumber.decrementAndGet() == 0) {
                log.debug("[FolderExec <{}>] finishing task: there is no other blocking tasks", folderKey);
                if (queueSize.get() == 0) {
                    suspend();
                    finished = true;
                }
                isReleased.signal();
            } else {
                log.debug("[FolderExec <{}>] finishing task: there are other blocking tasks to do", folderKey);
            }
            isFree.signalAll();
        } finally {
            lock.unlock();
        }
        if (finished) {
            log.debug("[FolderExec <{}>] finishing task: no work to do- I'm done", folderKey);
            iamDone.accept(this);
        }
    }

    private IMAPFolder folder() {
        if (this.folder != null) {
            return this.folder;
        }
        log.debug("[FolderExec <{}>] building folder", folderKey);
        lock.lock();
        try {
            return this.folder = folderBuilder.apply(folderKey);
        } finally {
            lock.unlock();
        }
    }

}
