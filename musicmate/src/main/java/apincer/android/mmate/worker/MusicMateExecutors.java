package apincer.android.mmate.worker;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.NonNull;

import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public class MusicMateExecutors {
    private static final String TAG = MusicMateExecutors.class.getName();
    private final ExecutorService mFastThread; // from editor page
    private final ScheduledExecutorService mScheduleThread;
    private final ExecutorService mScanThread; // for file scanning
    private final ExecutorService mMainThread; // for main page
    private final Executor mUIThread; // for ui

    // Use NUMBER_OF_CORES more effectively
    private static final int NUMBER_OF_CORES = Runtime.getRuntime().availableProcessors();
    private static final int MIN_POOL_SIZE = Math.max(2, NUMBER_OF_CORES / 2);
    private static final int MAX_POOL_SIZE = NUMBER_OF_CORES;

    // Extract constants for sleep times
    private static final long FAST_THREAD_DELAY_MS = 10;
    private static final long MAIN_THREAD_DELAY_MS = 20;

    private static volatile MusicMateExecutors mInstance;

    private MusicMateExecutors(ExecutorService mFastThread, ExecutorService scanThread, ExecutorService mMainThread) {
        this.mFastThread = mFastThread;
        this.mScanThread = scanThread;
        this.mMainThread = mMainThread;
        this.mUIThread = new UIThreadExecutor();
        this.mScheduleThread = Executors.newSingleThreadScheduledExecutor();
    }

    public static MusicMateExecutors getInstance() {
        if (mInstance == null) {
            synchronized (MusicMateExecutors.class) {
                mInstance = new MusicMateExecutors();
            }
        }
        return mInstance;
    }

    private MusicMateExecutors() {
        this(
               // new ThreadPoolExecutor(2, NUMBER_OF_CORES,0L, TimeUnit.MILLISECONDS, new LinkedBlockingQueue<>()) {
                new ThreadPoolExecutor(MIN_POOL_SIZE, MAX_POOL_SIZE,0L, TimeUnit.MILLISECONDS, new LinkedBlockingQueue<>()) {
                 protected void afterExecute(Runnable r, Throwable t) {
                     try {
                         Thread.sleep(FAST_THREAD_DELAY_MS); // wait 0.1 second
                     } catch (InterruptedException e) {
                         Log.e(TAG, "Thread interrupted during throttling delay", e);
                         // Restore the interrupted status
                         Thread.currentThread().interrupt();
                     }
                 }},
                new ThreadPoolExecutor(MIN_POOL_SIZE, MAX_POOL_SIZE,600L, TimeUnit.MILLISECONDS, new LinkedBlockingQueue<>()) {},
                new ThreadPoolExecutor(MIN_POOL_SIZE, MAX_POOL_SIZE,0L, TimeUnit.MILLISECONDS, new LinkedBlockingQueue<>()) {
                    protected void afterExecute(Runnable r, Throwable t) {
                        try {
                            Thread.sleep(MAIN_THREAD_DELAY_MS); // wait 0.2 second
                        } catch (InterruptedException e) {
                            Log.e(TAG, "Thread interrupted during throttling delay", e);
                            // Restore the interrupted status
                            Thread.currentThread().interrupt();
                        }
                    }
                });
    }

    public static void executeUI(@NonNull Runnable command) {
        getInstance().mUIThread.execute(command);
    }
    /**
     * Executes a task on the parallel processing thread pool.
     * Suitable for CPU-intensive tasks that should not block the main thread.
     *
     * @param task The task to execute
     */
    public static void executeParallel(@NonNull Runnable task) {
        getInstance().mFastThread.execute(task);
    }
    public static void execute(@NonNull Runnable command) {
        getInstance().mMainThread.execute(command);
    }
    public static void scan(@NonNull Runnable command) {
        getInstance().mScanThread.execute(command);
    }

    public static void schedule(@NonNull Runnable command, long seconds) {
        getInstance().mScheduleThread.schedule(command, seconds, TimeUnit.SECONDS);
    }

    // Add a static shutdown method
    public static void shutdownAll() {
        if (mInstance != null) {
            mInstance.shutdown();
            mInstance = null;
        }
    }

    public void shutdown() {
        try {
            // Attempt graceful shutdown with timeout
            this.mScanThread.shutdown();
            this.mScheduleThread.shutdown();
            this.mFastThread.shutdown();
            this.mMainThread.shutdown();

            // Wait a reasonable time for tasks to complete
            if (!this.mScanThread.awaitTermination(2, TimeUnit.SECONDS)) {
                this.mScanThread.shutdownNow();
            }
            // Similar for other thread pools
        } catch (InterruptedException e) {
            // Force shutdown if interrupted
            this.mScanThread.shutdownNow();
            // Force shutdown for other pools
            Thread.currentThread().interrupt();
        }
    }

    private static class UIThreadExecutor implements Executor {
        private final Handler mainThreadHandler = new Handler(Looper.getMainLooper());

        @Override
        public void execute(@NonNull Runnable command) {
            mainThreadHandler.post(command);
        }
    }
}