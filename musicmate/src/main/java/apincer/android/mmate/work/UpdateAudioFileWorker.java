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

    private UpdateAudioFileWorker(
            @NonNull Context context,
            @NonNull WorkerParameters parameters) {
        super(context, parameters);
        repos = FileRepository.newInstance(getApplicationContext());
    }

    @NonNull
    @Override
    public Result doWork() {
        Data inputData = getInputData();
        String artworkPath = inputData.getString(Constants.KEY_COVER_ART_PATH);
        List<MusicTag> tags = MusixMateApp.getPendingItems("Update");

        for (MusicTag tag:tags) {
            MusicMateExecutors.update(new UpdateRunnable(tag, artworkPath));
        }

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
