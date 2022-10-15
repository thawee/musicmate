package apincer.android.mmate.work;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.work.Data;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkManager;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import org.greenrobot.eventbus.EventBus;

import java.util.List;

import apincer.android.mmate.Constants;
import apincer.android.mmate.MusixMateApp;
import apincer.android.mmate.broadcast.AudioTagEditResultEvent;
import apincer.android.mmate.objectbox.MusicTag;
import apincer.android.mmate.repository.FileRepository;
import timber.log.Timber;

public class UpdateAudioFileWorker extends Worker {
    FileRepository repos;
   // private final ThreadPoolExecutor mExecutor;
    /**
     * Gets the number of available cores
     * (not always the same as the maximum number of cores)
     **/
   // private static final int NUMBER_OF_CORES = Runtime.getRuntime().availableProcessors();
    // Sets the amount of time an idle thread waits before terminating
   // private static final int KEEP_ALIVE_TIME = 600; //1000;
    // Sets the Time Unit to Milliseconds
   // private static final TimeUnit KEEP_ALIVE_TIME_UNIT = TimeUnit.MILLISECONDS;

    private UpdateAudioFileWorker(
            @NonNull Context context,
            @NonNull WorkerParameters parameters) {
        super(context, parameters);
        repos = FileRepository.newInstance(getApplicationContext());
       /* mExecutor = new ThreadPoolExecutor(
                NUMBER_OF_CORES/2, // + 5,   // Initial pool size
                NUMBER_OF_CORES, // + 4, //8,   // Max pool size
                KEEP_ALIVE_TIME,       // Time idle thread waits before terminating
                KEEP_ALIVE_TIME_UNIT,  // Sets the Time Unit for KEEP_ALIVE_TIME
                new LinkedBlockingDeque<>());  // Work Queue

        */
    }

    @NonNull
    @Override
    public Result doWork() {
        Data inputData = getInputData();
        String artworkPath = inputData.getString(Constants.KEY_COVER_ART_PATH);
        List<MusicTag> tags = MusixMateApp.getPendingItems("Update");

        for (MusicTag tag:tags) {
            MusicMateExecutors.maintain(new UpdateRunnable(tag, artworkPath));
            //UpdateRunnable r = new UpdateRunnable(tag, artworkPath);
            //mExecutor.execute(r);
        }
/*
        while (!mExecutor.getQueue().isEmpty()){
            try {
                Thread.sleep(5000);
            } catch (InterruptedException e) {
            }
        }

 */

        return Result.success();
    }

    public static void startWorker(Context context, List<MusicTag> files, String artworkPath) {
            Data inputData = (new Data.Builder())
                    .putString(Constants.KEY_COVER_ART_PATH, artworkPath)
                    .build();
            MusixMateApp.putPendingItems("Update", files);
            OneTimeWorkRequest workRequest = new OneTimeWorkRequest.Builder(UpdateAudioFileWorker.class)
                    .setInputData(inputData).build();
            WorkManager.getInstance(context).enqueue(workRequest);
           // WorkManager.getInstance(context).enqueueUniqueWork("UpdateWorker", ExistingWorkPolicy.APPEND, workRequest);
        //}
    }

    private final class UpdateRunnable  implements Runnable {
        private final MusicTag tag;
        private final String artworkPath;

        private UpdateRunnable(MusicTag tag, String artworkPath) {
            this.tag = tag;
            this.artworkPath = artworkPath;
        }
        @Override
        public void run() {
            try {
                boolean status = repos.saveAudioFile(tag, artworkPath);
               // String txt = status?getApplicationContext().getString(R.string.alert_write_tag_success, tag.getTitle()):getApplicationContext().getString(R.string.alert_write_tag_fail, tag.getTitle());
                AudioTagEditResultEvent message = new AudioTagEditResultEvent(AudioTagEditResultEvent.ACTION_UPDATE, status?Constants.STATUS_SUCCESS:Constants.STATUS_FAIL, tag);
                EventBus.getDefault().postSticky(message);
            } catch (Exception e) {
                Timber.e(e);
            }
        }
    }
}
