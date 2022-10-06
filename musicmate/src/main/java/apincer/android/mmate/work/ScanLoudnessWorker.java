package apincer.android.mmate.work;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.work.Data;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.ExistingWorkPolicy;
import androidx.work.OneTimeWorkRequest;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.util.List;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import apincer.android.mmate.Constants;
import apincer.android.mmate.objectbox.AudioTag;
import apincer.android.mmate.repository.AudioFileRepository;
import apincer.android.mmate.repository.AudioTagRepository;
import timber.log.Timber;

public class ScanLoudnessWorker extends Worker {
    private static final long MY_SCHEDULE_TIME = 5;
    private static final String TAG = "ScanLoudnessWorker";
    // private static Operation scanOperation;
    private final ThreadPoolExecutor mExecutor;
    AudioFileRepository repos; // = AudioFileRepository.newInstance(getApplicationContext());
    /**
     * Gets the number of available cores
     * (not always the same as the maximum number of cores)
     **/
    private static final int NUMBER_OF_CORES = 2; //Runtime.getRuntime().availableProcessors()/2;
    // Sets the amount of time an idle thread waits before terminating
    private static final int KEEP_ALIVE_TIME = 600; //1000;
    // Sets the Time Unit to Milliseconds
    private static final TimeUnit KEEP_ALIVE_TIME_UNIT = TimeUnit.MILLISECONDS;

    private ScanLoudnessWorker(
            @NonNull Context context,
            @NonNull WorkerParameters parameters) {
        super(context, parameters);
        repos = AudioFileRepository.newInstance(getApplicationContext());
       /* HandlerThread thread = new HandlerThread("ScanFilesWorker");
        thread.start(); */
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
        Data inputData = getInputData();
        if(inputData!=null && inputData.getString(Constants.KEY_MEDIA_TAG)!=null) {
             String s = inputData.getString(Constants.KEY_MEDIA_TAG);
             Gson gson = new Gson();
             Type audioTagType = new TypeToken<AudioTag>(){}.getType();
              AudioTag tag = gson.fromJson(s, audioTagType);
                try {
                    repos.deepScanMediaItem(tag);
                } catch (Exception e) {
                    Timber.e(e);
                }
        }else {
            AudioTagRepository tagrepos = AudioTagRepository.getInstance();
            List<AudioTag> tags = tagrepos.getAudioTagWithoutLoudness();
            for (AudioTag tag : tags) {
                ScanRunnable r = new ScanRunnable(tag);
                mExecutor.execute(r);
            }

            while (!mExecutor.getQueue().isEmpty()) {
                try {
                    Thread.sleep(60000); //1 mins
                } catch (InterruptedException e) {
                }
            }
        }

        return Result.success();
    }


    public static void startScan(Context context, AudioTag tag) {
        Gson gson = new Gson();
        Type audioTagType = new TypeToken<AudioTag>(){}.getType();
        // for(AudioTag tag: files) {
        String s = gson.toJson(tag, audioTagType);
        Data inputData = (new Data.Builder())
                .putString(Constants.KEY_MEDIA_TAG, s)
                .build();
        // if(scanOperation == null || scanOperation.getResult().isDone() || scanOperation.getResult().isCancelled()) {
        OneTimeWorkRequest workRequest = new OneTimeWorkRequest.Builder(ScanLoudnessWorker.class)
                    .setInitialDelay(4, TimeUnit.SECONDS)
                .setInputData(inputData)
                    .build();
        WorkManager instance = WorkManager.getInstance(context);
        instance.enqueueUniqueWork(TAG, ExistingWorkPolicy.KEEP, workRequest);
        //}
    }

    public static void startScan(Context context) {
       // if(scanOperation == null || scanOperation.getResult().isDone() || scanOperation.getResult().isCancelled()) {
       /* OneTimeWorkRequest workRequest = new OneTimeWorkRequest.Builder(ScanLoudnessWorker.class)
                    .setInitialDelay(4, TimeUnit.SECONDS)
                    .build();
         WorkManager.getInstance(context).enqueueUniqueWork("ScanLoudnessWorker", ExistingWorkPolicy.KEEP,workRequest);
       // } */
        PeriodicWorkRequest build = new PeriodicWorkRequest.Builder(ScanLoudnessWorker.class, MY_SCHEDULE_TIME, TimeUnit.MINUTES)
                .addTag(TAG)
               // .setConstraints(constraints)
                .build();

        WorkManager instance = WorkManager.getInstance(context);
        //if (instance != null) {
        instance.enqueueUniquePeriodicWork(TAG, ExistingPeriodicWorkPolicy.KEEP, build);
        //}
    }

    private final class ScanRunnable  implements Runnable {
        private final AudioTag tag;

        private ScanRunnable(AudioTag tag) {
            this.tag = tag;
        }
        @Override
        public void run() {
            try {
               // if(ScanLoudnessWorker.this.)
                repos.deepScanMediaItem(tag);
            } catch (Exception e) {
                Timber.e(e);
            }
        }
    }
}
