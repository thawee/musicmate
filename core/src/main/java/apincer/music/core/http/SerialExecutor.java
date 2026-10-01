package apincer.music.core.http;

import androidx.annotation.NonNull;

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;

/**
 * Runs tasks one at a time, in submission order, on the shared worker pool. Gives each
 * WebSocket connection ordered callbacks without a thread of its own. Tasks submitted after
 * the pool shuts down are dropped.
 */
final class SerialExecutor implements java.util.concurrent.Executor {
    private final Supplier<ExecutorService> pool;
    private final Queue<Runnable> tasks = new ConcurrentLinkedQueue<>();
    private final AtomicBoolean scheduled = new AtomicBoolean(false);

    /** @param pool the current shared worker pool (it is replaced when the server restarts) */
    SerialExecutor(Supplier<ExecutorService> pool) {
        this.pool = pool;
    }

    @Override
    public void execute(@NonNull Runnable task) {
        tasks.add(task);
        schedule();
    }

    private void schedule() {
        if (!scheduled.compareAndSet(false, true)) return;
        ExecutorService workers = pool.get();
        try {
            if (workers == null || workers.isShutdown()) throw new java.util.concurrent.RejectedExecutionException();
            workers.execute(this::drain);
        } catch (java.util.concurrent.RejectedExecutionException e) {
            tasks.clear();
            scheduled.set(false);
        }
    }

    private void drain() {
        try {
            Runnable task;
            while ((task = tasks.poll()) != null) {
                try {
                    task.run();
                } catch (RuntimeException ignored) {
                    // The task reports its own errors (onError); keep draining
                }
            }
        } finally {
            scheduled.set(false);
            if (!tasks.isEmpty()) schedule(); // a task arrived after the last poll
        }
    }
}
