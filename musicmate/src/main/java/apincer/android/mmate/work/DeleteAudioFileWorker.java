package apincer.android.mmate.work;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.work.Data;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkManager;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import org.greenrobot.eventbus.EventBus;

import java.lang.reflect.Type;
import java.util.List;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import apincer.android.mmate.Constants;
import apincer.android.mmate.broadcast.AudioTagEditResultEvent;
import apincer.android.mmate.objectbox.AudioTag;
import apincer.android.mmate.repository.AudioFileRepository;
import timber.log.Timber;

public class DeleteAudioFileWorker extends Worker {
    AudioFileRepository repos;
    private final ThreadPoolExecutor mExecutor;
    /**
     * Gets the number of available cores
     * (not always the same as the maximum number of cores)
     **/
    private static final int NUMBER_OF_CORES = 2; //Runtime.getRuntime().availableProcessors();
    // Sets the amount of time an idle thread waits before terminating
    private static final int KEEP_ALIVE_TIME = 600; //1000;
    // Sets the Time Unit to Milliseconds
    private static final TimeUnit KEEP_ALIVE_TIME_UNIT = TimeUnit.MILLISECONDS;
    private DeleteAudioFileWorker(
            @NonNull Context context,
            @NonNull WorkerParameters parameters) {
        super(context, parameters);
        repos = AudioFileRepository.newInstance(getApplicationContext());
        mExecutor = new ThreadPoolExecutor(
                1, // + 5,   // Initial pool size
                NUMBER_OF_CORES, // + 4, //8,   // Max pool size
                KEEP_ALIVE_TIME,       // Time idle thread waits before terminating
                KEEP_ALIVE_TIME_UNIT,  // Sets the Time Unit for KEEP_ALIVE_TIME
                new LinkedBlockingDeque<>());  // Work Queue
    }

    @NonNull
    @Override
    public Result doWork() {
        Data inputData = getInputData();
        String s =inputData.getString(Constants.KEY_MEDIA_TAG);
        Gson gson = new Gson();
        Type audioTagType = new TypeToken<List<AudioTag>>(){}.getType();
        List<AudioTag> tags = gson.fromJson(s, audioTagType);
        for (AudioTag tag:tags) {
            DeleteRunnable r = new DeleteRunnable(tag);
            mExecutor.execute(r);
        }

        while (!mExecutor.getQueue().isEmpty()){
            try {
                Thread.sleep(5000);
            } catch (InterruptedException e) {
            }
        }
        return Result.success();
    }

    public static void startWorker(Context context, List<AudioTag> files) {
        Gson gson = new Gson();
        Type audioTagType = new TypeToken<List<AudioTag>>(){}.getType();
        //for(AudioTag tag: files) {
            String s = gson.toJson(files, audioTagType);
            Data inputData = (new Data.Builder())
                    .putString(Constants.KEY_MEDIA_TAG, s)
                    .build();
            OneTimeWorkRequest workRequest = new OneTimeWorkRequest.Builder(DeleteAudioFileWorker.class)
                    .setInputData(inputData).build();
            WorkManager.getInstance(context).enqueue(workRequest);
           // WorkManager.getInstance(context).enqueueUniqueWork("DeleteWorker", ExistingWorkPolicy.APPEND, workRequest);
       // }
    }

    private final class DeleteRunnable  implements Runnable {
        private final AudioTag tag;

        private DeleteRunnable(AudioTag tag) {
            this.tag = tag;
        }
        @Override
        public void run() {
            try {
                boolean status = repos.deleteMediaItem(tag);
                //String txt = status?getApplicationContext().getString(R.string.alert_delete_success, tag.getTitle()):getApplicationContext().getString(R.string.alert_delete_fail, tag.getTitle());

                AudioTagEditResultEvent message = new AudioTagEditResultEvent(AudioTagEditResultEvent.ACTION_DELETE, status?Constants.STATUS_SUCCESS:Constants.STATUS_FAIL, tag);
                EventBus.getDefault().postSticky(message);
            } catch (Exception e) {
                Timber.e(e);
            }
        }
    }
}
