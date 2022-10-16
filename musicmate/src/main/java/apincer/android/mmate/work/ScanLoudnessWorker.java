package apincer.android.mmate.work;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.work.Data;
import androidx.work.ExistingWorkPolicy;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkManager;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import org.greenrobot.eventbus.EventBus;

import java.lang.reflect.Type;
import java.util.List;
import java.util.concurrent.TimeUnit;

import apincer.android.mmate.Constants;
import apincer.android.mmate.broadcast.AudioTagEditResultEvent;
import apincer.android.mmate.objectbox.MusicTag;
import apincer.android.mmate.repository.FileRepository;
import apincer.android.mmate.repository.MusicTagRepository;
import timber.log.Timber;

public class ScanLoudnessWorker extends Worker {
    private static final String TAG = "ScanLoudnessWorker";
    FileRepository repos;

    private ScanLoudnessWorker(
            @NonNull Context context,
            @NonNull WorkerParameters parameters) {
        super(context, parameters);
        repos = FileRepository.newInstance(getApplicationContext());
    }

    @NonNull
    @Override
    public Result doWork() {
        Data inputData = getInputData();
        if(inputData!=null && inputData.getString(Constants.KEY_MEDIA_TAG)!=null) {
             String s = inputData.getString(Constants.KEY_MEDIA_TAG);
             Gson gson = new Gson();
             Type audioTagType = new TypeToken<MusicTag>(){}.getType();
              MusicTag tag = gson.fromJson(s, audioTagType);
              //MusicMateExecutors.main(() -> {
                  try {
                        repos.deepScanMediaItem(tag);
                        AudioTagEditResultEvent message = new AudioTagEditResultEvent(AudioTagEditResultEvent.ACTION_UPDATE, Constants.STATUS_SUCCESS, tag);
                        EventBus.getDefault().postSticky(message);

                  } catch (Exception e) {
                      Timber.e(e);
                  }
             // });
        }else {
            MusicTagRepository repos = MusicTagRepository.getInstance();
            List<MusicTag> tags = repos.getAudioTagWithoutLoudness();
            for (MusicTag tag : tags) {
                MusicMateExecutors.single(new ScanRunnable(tag));
            }
        }

        return Result.success();
    }


    public static void startScan(Context context, MusicTag tag) {
        Gson gson = new Gson();
        Type audioTagType = new TypeToken<MusicTag>(){}.getType();
        String s = gson.toJson(tag, audioTagType);
        Data inputData = (new Data.Builder())
                .putString(Constants.KEY_MEDIA_TAG, s)
                .build();
        OneTimeWorkRequest workRequest = new OneTimeWorkRequest.Builder(ScanLoudnessWorker.class)
                    .setInitialDelay(4, TimeUnit.SECONDS)
                .setInputData(inputData)
                    .build();
        WorkManager instance = WorkManager.getInstance(context);
        instance.enqueueUniqueWork(TAG, ExistingWorkPolicy.KEEP, workRequest);
    }

    private final class ScanRunnable  implements Runnable {
        private final MusicTag tag;

        private ScanRunnable(MusicTag tag) {
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
