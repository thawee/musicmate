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
    private final ExecutorService mMaintainThread;
    private final ScheduledExecutorService mScheduleThread;
    private final ExecutorService mScanThread;
    private final ExecutorService mImportThread;
    private final Executor mMainThread;
    private final ExecutorService mDbThread;
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

    private MusicMateExecutors(ExecutorService maintainThread, ExecutorService scanThread, ExecutorService importThread) {
        this.mMaintainThread = maintainThread;
        this.mScanThread = scanThread;
        this.mImportThread = importThread;
        this.mDbThread = new ThreadPoolExecutor(2, 4,300L, TimeUnit.MILLISECONDS, new LinkedBlockingQueue<>()) {};
        this.mMainThread = new MainThreadExecutor();
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
        this(new ThreadPoolExecutor(1, NUMBER_OF_CORES,0L, TimeUnit.MILLISECONDS, new LinkedBlockingQueue<>()) {
                 protected void afterExecute(Runnable r, Throwable t) {
                     try {
                         Thread.sleep(30); // wait 0.1 second
                     } catch (InterruptedException e) {
                         Log.e(TAG, "MusicMateExecutors",e);
                     }
                 }},
                new ThreadPoolExecutor(2, NUMBER_OF_CORES,300L, TimeUnit.MILLISECONDS, new LinkedBlockingQueue<>()) {},
                new ThreadPoolExecutor(1, 2,0L, TimeUnit.MILLISECONDS, new LinkedBlockingQueue<>()) {
                    protected void afterExecute(Runnable r, Throwable t) {
                        try {
                            Thread.sleep(20); // wait 0.2 second
                        } catch (InterruptedException e) {
                            Log.e(TAG, "MusicMateExecutors",e);
                        }
                    }
                });
    }

    public Executor db() {
        return mDbThread;
    }

    public static void db(@NonNull Runnable command) {
        getInstance().db().execute(command);
    }

    public Executor update() {
        return mMaintainThread;
    }
    public Executor main() {
        return mMainThread;
    }

    public ExecutorService scan() {
        return mScanThread;
    }

    public Executor move() {
        return mImportThread;
    }

    public static void main(@NonNull Runnable command) {
        getInstance().main().execute(command);
    }
    public static void update(@NonNull Runnable command) {
        getInstance().update().execute(command);
    }
    public static void move(@NonNull Runnable command) {
        getInstance().move().execute(command);
    }
    public static void scan(@NonNull Runnable command) {
        getInstance().scan().execute(command);
    }

    public static void schedule(@NonNull Runnable command, long seconds) {
        getInstance().mScheduleThread.schedule(command, seconds, TimeUnit.SECONDS);
    }

    public void shutdown() {
        this.mScanThread.shutdown();
        this.mScheduleThread.shutdown();
        this.mMaintainThread.shutdown();
        this.mScanThread.shutdown();
        this.mImportThread.shutdown();
        this.mDbThread.shutdown();
    }

    private static class MainThreadExecutor implements Executor {
        private final Handler mainThreadHandler = new Handler(Looper.getMainLooper());

        @Override
        public void execute(@NonNull Runnable command) {
            mainThreadHandler.post(command);
        }
    }
    public void init() {

    }
}