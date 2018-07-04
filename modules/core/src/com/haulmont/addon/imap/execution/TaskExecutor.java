package com.haulmont.addon.imap.execution;

import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;

public interface TaskExecutor {
    <T> T invokeImmediate(ImmediateTask<T> task);
    <T> boolean submitDelayable(DelayableTask<T> task);

    default void wait(Lock lock, Condition condition) {
        TaskExecutor.waitFor(lock, condition);
    }

    static void waitFor(Lock lock, Condition condition) {
        lock.lock();
        try {
            condition.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("was interrupted"); //todo
        } finally {
            lock.unlock();
        }
    }
}
