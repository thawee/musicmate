package apincer.android.mmate.work;

import android.content.Context;

import androidx.annotation.NonNull;
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

public class DeleteAudioFileWorker extends Worker {
    FileRepository repos;

    private DeleteAudioFileWorker(
            @NonNull Context context,
            @NonNull WorkerParameters parameters) {
        super(context, parameters);
        repos = FileRepository.newInstance(getApplicationContext());
    }

    @NonNull
    @Override
    public Result doWork() {
       List<MusicTag> tags = MusixMateApp.getPendingItems("Delete");
        for (MusicTag tag:tags) {
            MusicMateExecutors.update(new DeleteRunnable(tag));
        }
        return Result.success();
    }

    public static void startWorker(Context context, List<MusicTag> files) {
        MusixMateApp.putPendingItems("Delete", files);
            OneTimeWorkRequest workRequest = new OneTimeWorkRequest.Builder(DeleteAudioFileWorker.class).build();
                  //  .setInputData(inputData).build();
            WorkManager.getInstance(context).enqueue(workRequest);
    }

    private final class DeleteRunnable  implements Runnable {
        private final MusicTag tag;

        private DeleteRunnable(MusicTag tag) {
            this.tag = tag;
        }
        @Override
        public void run() {
            try {
                boolean status = repos.deleteMediaItem(tag);
                //String txt = status?getApplicationContext().getString(R.string.alert_delete_success, tag.getTitle()):getApplicationContext().getString(R.string.alert_delete_fail, tag.getTitle());

                AudioTagEditResultEvent message = new AudioTagEditResultEvent(AudioTagEditResultEvent.ACTION_DELETE, status?Constants.STATUS_SUCCESS:Constants.STATUS_FAIL, tag);
                //AudioTagEditResultEvent message = new AudioTagEditResultEvent(AudioTagEditResultEvent.ACTION_DELETE, status?Constants.STATUS_SUCCESS:Constants.STATUS_FAIL, null);
                EventBus.getDefault().postSticky(message);
            } catch (Exception e) {
                Timber.e(e);
            }
        }
    }
}
