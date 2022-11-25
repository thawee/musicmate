package apincer.android.mmate.work;

import static de.esoco.coroutine.Coroutine.first;
import static de.esoco.coroutine.CoroutineScope.launch;
import static de.esoco.coroutine.step.CodeExecution.consume;
import static de.esoco.coroutine.step.CodeExecution.supply;
import static de.esoco.coroutine.step.Iteration.forEach;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.work.Constraints;
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

import apincer.android.mmate.Constants;
import apincer.android.mmate.broadcast.AudioTagEditResultEvent;
import apincer.android.mmate.objectbox.MusicTag;
import apincer.android.mmate.repository.FileRepository;
import apincer.android.mmate.repository.MusicTagRepository;
import de.esoco.coroutine.Coroutine;
import timber.log.Timber;

@Deprecated
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
        MusicTag tag;
        if (inputData.getString(Constants.KEY_MEDIA_TAG) != null) {
            String s = inputData.getString(Constants.KEY_MEDIA_TAG);
            Gson gson = new Gson();
            Type audioTagType = new TypeToken<MusicTag>() {}.getType();
            tag = gson.fromJson(s, audioTagType);
            try {
                if(scan(tag)) {
                    AudioTagEditResultEvent message = new AudioTagEditResultEvent(AudioTagEditResultEvent.ACTION_UPDATE, Constants.STATUS_SUCCESS, tag);
                    EventBus.getDefault().postSticky(message);
                }

                // purge previous completed job
                //WorkManager.getInstance(getApplicationContext()).pruneWork();
            } catch (Exception e) {
                Timber.e(e);
            }
        } else {
       //     MusicMateExecutors.loudness(new ScanRunnable());
      //  }
          /*  MusicTagRepository repos = MusicTagRepository.getInstance();
            List<MusicTag> tags = repos.getAudioTagWithoutLoudness();
            for (MusicTag tag : tags) {
                MusicMateExecutors.loudness(new ScanRunnable(tag));
            } */
           // int COROUTINE_COUNT = 1;

            Coroutine<?, ?> cIterating =
                    first(supply(this::list)).then(
                            forEach( consume (this::scan)));

            launch(
                    scope ->
                    {
                            cIterating.runBlocking(scope, null);
                    });
        }

        return Result.success();
    }

    private boolean scan(MusicTag tag) {
        return repos.deepScanMediaItem(tag);
    }

    private List<MusicTag> list() {
        return MusicTagRepository.getAudioTagWithoutLoudness();
    }

    public static void startScan(Context context, MusicTag tag) {
        Gson gson = new Gson();
        Type audioTagType = new TypeToken<MusicTag>(){}.getType();
        String s = gson.toJson(tag, audioTagType);
        Data inputData = (new Data.Builder())
                .putString(Constants.KEY_MEDIA_TAG, s)
                .build();
        Constraints constraints = new Constraints.Builder()
                .setRequiresBatteryNotLow(true)
                .setRequiresStorageNotLow(true)
                .build();
        OneTimeWorkRequest workRequest = new OneTimeWorkRequest.Builder(ScanLoudnessWorker.class)
                //.setInitialDelay(600, TimeUnit.MILLISECONDS)
                .setConstraints(constraints)
                .setInputData(inputData)
                .build();

        WorkManager instance = WorkManager.getInstance(context);
        instance.enqueueUniqueWork(TAG, ExistingWorkPolicy.KEEP, workRequest);
    }

    private final class ScanRunnable  implements Runnable {
        private ScanRunnable() {
        }
        @Override
        public void run() {
           /* try {
                repos.deepScanMediaItem(tag);
            } catch (Exception e) {
                Timber.e(e);
            }*/
            Coroutine<?, ?> cIterating =
                    first(supply(this::list)).then(
                            forEach( consume (this::scan)));

            launch(
                    scope ->
                    {
                        // for (int i = 0; i < COROUTINE_COUNT; i++) {
                        cIterating.runBlocking(scope, null);
                        // }
                    });
        }
        private void scan(MusicTag tag) {
            repos.deepScanMediaItem(tag);
        }

        private List<MusicTag> list() {
            return MusicTagRepository.getAudioTagWithoutLoudness();
        }
    }
}
