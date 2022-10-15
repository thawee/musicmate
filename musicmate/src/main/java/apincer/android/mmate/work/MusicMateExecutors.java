package apincer.android.mmate.work;
import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;

import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class MusicMateExecutors {
    private final Executor mMaintainThread;
    private final Executor mScannerThread;
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

    private MusicMateExecutors(Executor mScannerThread, Executor mMaintainThread, Executor mainThread) {
        this.mScannerThread = mScannerThread;
        this.mMaintainThread = mMaintainThread;
        this.mMainThread = mainThread;
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
        this(Executors.newFixedThreadPool(2), Executors.newFixedThreadPool(NUMBER_OF_CORES),
                new MainThreadExecutor());
        /*mExecutor = new ThreadPoolExecutor(
                1, // + 5,   // Initial pool size
                NUMBER_OF_CORES, // + 4, //8,   // Max pool size
                KEEP_ALIVE_TIME,       // Time idle thread waits before terminating
                KEEP_ALIVE_TIME_UNIT,  // Sets the Time Unit for KEEP_ALIVE_TIME
                new LinkedBlockingDeque<>());  // Work Queue */
    }

    public Executor single() {
        return mScannerThread;
    }

    public Executor update() {
        return mMaintainThread;
    }

    public Executor main() {
        return mMainThread;
    }

    public static void single(@NonNull Runnable command) {
        getInstance().single().execute(command);
    }
    public static void update(@NonNull Runnable command) {
        getInstance().update().execute(command);
    }
    public static void main(@NonNull Runnable command) {
        getInstance().main().execute(command);
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