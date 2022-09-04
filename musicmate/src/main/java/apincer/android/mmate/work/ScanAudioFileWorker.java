package apincer.android.mmate.work;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.work.ExistingWorkPolicy;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkManager;
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
import apincer.android.mmate.repository.AudioTagRepository;
import timber.log.Timber;

public class ScanAudioFileWorker extends Worker {
   // private static Operation scanOperation;
    private final ThreadPoolExecutor mExecutor;
    AudioFileRepository repos; // = AudioFileRepository.newInstance(getApplicationContext());
    /**
     * Gets the number of available cores
     * (not always the same as the maximum number of cores)
     **/
    private static final int NUMBER_OF_CORES = Runtime.getRuntime().availableProcessors();
    // Sets the amount of time an idle thread waits before terminating
    private static final int KEEP_ALIVE_TIME = 600; //1000;
    // Sets the Time Unit to Milliseconds
    private static final TimeUnit KEEP_ALIVE_TIME_UNIT = TimeUnit.MILLISECONDS;

    private ScanAudioFileWorker(
            @NonNull Context context,
            @NonNull WorkerParameters parameters) {
        super(context, parameters);
        repos = AudioFileRepository.newInstance(getApplicationContext());
       // HandlerThread thread = new HandlerThread("ScanFilesWorker");
       // thread.start();
        mExecutor = new ThreadPoolExecutor(
                NUMBER_OF_CORES, // + 5,   // Initial pool size
                NUMBER_OF_CORES, // + 4, //8,   // Max pool size
                KEEP_ALIVE_TIME,       // Time idle thread waits before terminating
                KEEP_ALIVE_TIME_UNIT,  // Sets the Time Unit for KEEP_ALIVE_TIME
                new LinkedBlockingDeque<>());  // Work Queue
    }

    @NonNull
    @Override
    public Result doWork() {
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
            file = new File(DocumentFileCompat.buildAbsolutePath(getApplicationContext(), sid, "IDMP"));
            if(file.exists()) {
                ScanRunnable r = new ScanRunnable(file);
                mExecutor.execute(r);
            }
        }
        AudioTagRepository.cleanMusicMate();
        ScanLoudnessWorker.startScan(getApplicationContext());

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
        }else if(ext.equalsIgnoreCase("aiff")) {
            return true;
        }else if(ext.equalsIgnoreCase("dsf")) {
            return true;
        }else return ext.equalsIgnoreCase("dff");
    }

    public static void startScan(Context context) {
       // if(scanOperation == null || scanOperation.getResult().isDone() || scanOperation.getResult().isCancelled()) {
        OneTimeWorkRequest workRequest = new OneTimeWorkRequest.Builder(ScanAudioFileWorker.class)
                    .setInitialDelay(4, TimeUnit.SECONDS)
                    .build();
         WorkManager.getInstance(context).enqueueUniqueWork("ScanWorker", ExistingWorkPolicy.KEEP,workRequest);
       // }
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
                        repos.scanFileAndSaveTag(f);
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
