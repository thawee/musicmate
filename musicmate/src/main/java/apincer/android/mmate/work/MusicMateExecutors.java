package apincer.android.mmate.work;

import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;

import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import timber.log.Timber;

public class MusicMateExecutors {
    private final Executor mMaintainThread;
    private final Executor mLoudnessThread;
    private final Executor mScanThread;
    private final Executor mImportThread;
    private final Executor mMainThread;
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

    private MusicMateExecutors(Executor loudnessThread, Executor maintainThread, Executor scanThread, Executor importThread) {
        this.mLoudnessThread = loudnessThread;
        this.mMaintainThread = maintainThread;
        this.mScanThread = scanThread;
        this.mImportThread = importThread;
        this.mMainThread = new MainThreadExecutor();
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
        this(new ThreadPoolExecutor(1, 1,0L, TimeUnit.MILLISECONDS, new LinkedBlockingQueue<Runnable>()) {
                protected void afterExecute(Runnable r, Throwable t) {
                    try {
                        Thread.sleep(1000); // wait 1 second
                    } catch (InterruptedException e) {
                        Timber.e(e);
                    }
                }}, new ThreadPoolExecutor(1, NUMBER_OF_CORES,0L, TimeUnit.MILLISECONDS, new LinkedBlockingQueue<Runnable>()) {
                 protected void afterExecute(Runnable r, Throwable t) {
                     try {
                         Thread.sleep(30); // wait 0.1 second
                     } catch (InterruptedException e) {
                         Timber.e(e);
                     }
                 }}, new ThreadPoolExecutor(1, NUMBER_OF_CORES,0L, TimeUnit.MILLISECONDS, new LinkedBlockingQueue<Runnable>()) {
                    protected void afterExecute(Runnable r, Throwable t) {
                        try {
                            Thread.sleep(100); // wait 0.1 second
                        } catch (InterruptedException e) {
                            Timber.e(e);
                        }
                    }}, new ThreadPoolExecutor(1, 2,0L, TimeUnit.MILLISECONDS, new LinkedBlockingQueue<Runnable>()) {
                    protected void afterExecute(Runnable r, Throwable t) {
                        try {
                            Thread.sleep(200); // wait 0.2 second
                        } catch (InterruptedException e) {
                            Timber.e(e);
                        }
                    }
                });
    }

    public Executor loudness() {
        return mLoudnessThread;
    }

    public Executor update() {
        return mMaintainThread;
    }
    public Executor main() {
        return mMainThread;
    }

    public Executor scan() {
        return mScanThread;
    }

    public Executor move() {
        return mImportThread;
    }

    public static void main(@NonNull Runnable command) {
        getInstance().main().execute(command);
    }

    public static void loudness(@NonNull Runnable command) {
        getInstance().loudness().execute(command);
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

    private static class MainThreadExecutor implements Executor {
        private Handler mainThreadHandler = new Handler(Looper.getMainLooper());

        @Override
        public void execute(@NonNull Runnable command) {
            mainThreadHandler.post(command);
        }
    }
    public void init() {

    }
}