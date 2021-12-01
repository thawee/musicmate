package apincer.android.mmate.work;

import android.content.Context;
import android.os.HandlerThread;
import android.os.Looper;

import androidx.annotation.NonNull;
import androidx.work.Data;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkManager;
import androidx.work.WorkRequest;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.anggrayudi.storage.file.DocumentFileCompat;

import org.jaudiotagger.audio.generic.Utils;

import java.io.File;
import java.util.List;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import apincer.android.mmate.repository.AudioFileRepository;
import timber.log.Timber;

public class ScanAudioFileWorker extends Worker {
   // private volatile Looper serviceLooper;
    private ThreadPoolExecutor mExecutor;
    /**
     * Gets the number of available cores
     * (not always the same as the maximum number of cores)
     **/
    private static final int NUMBER_OF_CORES = Runtime.getRuntime().availableProcessors();
    // Sets the amount of time an idle thread waits before terminating
    private static final int KEEP_ALIVE_TIME = 1000;
    // Sets the Time Unit to Milliseconds
    private static final TimeUnit KEEP_ALIVE_TIME_UNIT = TimeUnit.MILLISECONDS;

    private ScanAudioFileWorker(
            @NonNull Context context,
            @NonNull WorkerParameters parameters) {
        super(context, parameters);
        HandlerThread thread = new HandlerThread("ScanFilesWorker");
        thread.start();

       // serviceLooper = thread.getLooper();
        mExecutor = new ThreadPoolExecutor(
                NUMBER_OF_CORES + 5,   // Initial pool size
                NUMBER_OF_CORES + 8,   // Max pool size
                KEEP_ALIVE_TIME,       // Time idle thread waits before terminating
                KEEP_ALIVE_TIME_UNIT,  // Sets the Time Unit for KEEP_ALIVE_TIME
                new LinkedBlockingDeque<Runnable>());  // Work Queue
    }

    @NonNull
    @Override
    public Result doWork() {
        //Data inputData = getInputData();
        // Mark the Worker as important/
        //setForegroundAsync(createForegroundInfo(progress));
        List<String> storageIds = DocumentFileCompat.getStorageIds(getApplicationContext());
        for (String sid : storageIds) {
            File file = new File(DocumentFileCompat.buildAbsolutePath(getApplicationContext(), sid, "Music"));
            if(file.exists()) {
                ScanRunnable r = new ScanRunnable(file);
                mExecutor.execute(r);
            }
            file = new File(DocumentFileCompat.buildAbsolutePath(getApplicationContext(), sid, "Download"));
            if(file.exists()) {
                ScanRunnable r = new ScanRunnable(file);
                mExecutor.execute(r);
            }
        }

        return Result.success();
    }

    private boolean isValidMediaFile(File file) {

        if(!file.exists()) return false;

        String ext = Utils.getExtension(file);
        if(ext.equalsIgnoreCase("mp3")) {
            return true;
        }else if(ext.equalsIgnoreCase("m4a")) {
            return true;
        }else if(ext.equalsIgnoreCase("flac")) {
            return true;
        }else if(ext.equalsIgnoreCase("wav")) {
            return true;
        }else if(ext.equalsIgnoreCase("aif")) {
            return true;
        }else if(ext.equalsIgnoreCase("dsf")) {
            return true;
      /*  }else if(ext.equalsIgnoreCase("dff")) {
            return true;
        }else if(ext.equalsIgnoreCase("iso")) {
            return true; */
        }
        return false;
    }

    public static void startScan(Context context) {
        WorkRequest workRequest = new OneTimeWorkRequest.Builder(ScanAudioFileWorker.class).build();
        WorkManager.getInstance(context).enqueue(workRequest);
    }

    private final class ScanRunnable  implements Runnable {
        private final File dir;

        private ScanRunnable(File dir) {
            this.dir = dir;
        }
        @Override
        public void run() {
            try {
                //Timber.i("scanning"+ dir+":"+ new Date());
                File[] files = dir.listFiles();
                if(files == null) return;
                for (File f : files) {
                    if(!f.exists()) continue;
                    if(isValidMediaFile(f)) {
                        AudioFileRepository.getInstance(getApplicationContext()).scanFileAndSaveTag(f);
                    } else if(f.isDirectory()) {
                        ScanRunnable r = new ScanRunnable(f);
                        mExecutor.execute(r);
                    }
                }
            } catch (Exception e) {
                Timber.e(e);
            }
        }
    }
}
