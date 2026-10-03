package apincer.music.core.http;

import androidx.annotation.NonNull;

import java.util.Queue;
import java.util.ArrayDeque;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;

/**
 * Runs bounded, ordered WebSocket callbacks on the callback pool. Overflow closes the session;
 * one terminal callback is reserved after previously admitted messages.
 */
final class SerialExecutor implements java.util.concurrent.Executor {
    private final Supplier<ExecutorService> pool;
    private final Queue<Runnable> tasks = new ArrayDeque<>();
    private final AtomicBoolean scheduled = new AtomicBoolean(false);
    private final Runnable onRejected;
    private boolean closing;
    private static final int MAX_PENDING = 256;

    /** @param pool the callback pool for this server */
    SerialExecutor(Supplier<ExecutorService> pool, Runnable onRejected) {
        this.pool = pool;
        this.onRejected = onRejected;
    }

    @Override
    public void execute(@NonNull Runnable task) {
        boolean rejected = false;
        synchronized (tasks) {
            if (closing) return;
            if (tasks.size() < MAX_PENDING) {
                tasks.add(task);
            } else {
                closing = true;
                rejected = true;
            }
        }
        if (rejected) onRejected.run();
        schedule();
    }

    /** A single terminal callback is reserved even when the message queue is full. */
    void executeClose(Runnable task) {
        synchronized (tasks) {
            closing = true;
            tasks.add(task);
        }
        schedule();
    }

    private void schedule() {
        if (!scheduled.compareAndSet(false, true)) return;
        ExecutorService workers = pool.get();
        try {
            if (workers == null || workers.isShutdown()) throw new java.util.concurrent.RejectedExecutionException();
            workers.execute(this::drain);
        } catch (java.util.concurrent.RejectedExecutionException e) {
            synchronized (tasks) { tasks.clear(); }
            scheduled.set(false);
            onRejected.run();
        }
    }

    private void drain() {
        try {
            Runnable task;
            while (true) {
                synchronized (tasks) { task = tasks.poll(); }
                if (task == null) break;
                try {
                    task.run();
                } catch (RuntimeException ignored) {
                    // The task reports its own errors (onError); keep draining
                }
            }
        } finally {
            scheduled.set(false);
            boolean pending;
            synchronized (tasks) { pending = !tasks.isEmpty(); }
            if (pending) schedule(); // a task arrived after the last poll
        }
    }
}
