package apincer.android.mmate.worker;

import android.content.Context;
import android.util.Log;

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
import apincer.android.mmate.notification.AudioTagEditResultEvent;
import apincer.android.mmate.repository.MusicTag;
import apincer.android.mmate.repository.FileRepository;

public class UpdateAudioFileWorker extends Worker {
    private static final String TAG = UpdateAudioFileWorker.class.getName();
    final FileRepository repos;

    private UpdateAudioFileWorker(
            @NonNull Context context,
            @NonNull WorkerParameters parameters) {
        super(context, parameters);
        repos = FileRepository.newInstance(getApplicationContext());
    }

    @NonNull
    @Override
    public Result doWork() {
        List<MusicTag> list = list();

        list.forEach(tag -> {
            try {
                boolean status = save(tag);

                AudioTagEditResultEvent message = new AudioTagEditResultEvent(
                        AudioTagEditResultEvent.ACTION_UPDATE,
                        status ? Constants.STATUS_SUCCESS : Constants.STATUS_FAIL,
                        tag);
                EventBus.getDefault().postSticky(message);
            } catch (Exception e) {
                Log.e(TAG, "save", e);
            }
        });

        // purge previous completed job
        WorkManager.getInstance(getApplicationContext()).pruneWork();

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
    }

    private List<MusicTag> list() {
        return MusixMateApp.getPendingItems("Update");
    }

    private boolean save(MusicTag tag) {
            try {
                return repos.setMusicTag(tag);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
    }
}
