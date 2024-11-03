package apincer.android.mmate.worker;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.NonNull;

import org.greenrobot.eventbus.EventBus;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import apincer.android.mmate.Constants;
import apincer.android.mmate.notification.AudioTagEditResultEvent;
import apincer.android.mmate.repository.MusicTag;

public class MusicMateExecutors {
    private static final String TAG = MusicMateExecutors.class.getName();
    private final ExecutorService mFastThread; // from editor page
    private final ScheduledExecutorService mScheduleThread;
    private final ExecutorService mScanThread; // for file scanning
    private final ExecutorService mMainThread; // for main page
    private final Executor mUIThread; // for ui
    /**
     * Gets the number of available cores
     * (not always the same as the maximum number of cores)
     **/
    private static final int NUMBER_OF_CORES = Runtime.getRuntime().availableProcessors();
    // Sets the amount of time an idle thread waits before terminating
   // private static final int KEEP_ALIVE_TIME = 60000;
    // Sets the Time Unit to Milliseconds
  //  private static final TimeUnit KEEP_ALIVE_TIME_UNIT = TimeUnit.MILLISECONDS;

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
                new ThreadPoolExecutor(2, NUMBER_OF_CORES,0L, TimeUnit.MILLISECONDS, new LinkedBlockingQueue<>()) {
                 protected void afterExecute(Runnable r, Throwable t) {
                     try {
                         Thread.sleep(20); // wait 0.1 second
                     } catch (InterruptedException e) {
                         Log.e(TAG, "MusicMateExecutors",e);
                     }
                 }},
                new ThreadPoolExecutor(2, 4,600L, TimeUnit.MILLISECONDS, new LinkedBlockingQueue<>()) {},
                new ThreadPoolExecutor(2, 2,0L, TimeUnit.MILLISECONDS, new LinkedBlockingQueue<>()) {
                    protected void afterExecute(Runnable r, Throwable t) {
                        try {
                            Thread.sleep(20); // wait 0.2 second
                        } catch (InterruptedException e) {
                            Log.e(TAG, "MusicMateExecutors",e);
                        }
                    }
                });
    }

    public static void ui(@NonNull Runnable command) {
        getInstance().mUIThread.execute(command);
    }
    public static void fast(@NonNull Runnable command) {
        getInstance().mFastThread.execute(command);
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

    public static void executeParallels(List<MusicTag> items, int threadNo, Consumer<MusicTag> task) {
        ExecutorService executor = Executors.newFixedThreadPool(threadNo);
        List<CompletableFuture<Void>> futures = items.stream()
                .map(item -> CompletableFuture.runAsync(() -> task.accept(item), executor))
                .toList();

        CompletableFuture<Void> allOf = CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]));
        allOf.join(); // Wait for all tasks to complete
        executor.shutdown();
    }

    public void shutdown() {
        this.mScanThread.shutdown();
        this.mScheduleThread.shutdown();
        this.mFastThread.shutdown();
        this.mScanThread.shutdown();
        this.mMainThread.shutdown();
    }

    private static class UIThreadExecutor implements Executor {
        private final Handler mainThreadHandler = new Handler(Looper.getMainLooper());

        @Override
        public void execute(@NonNull Runnable command) {
            mainThreadHandler.post(command);
        }
    }
}