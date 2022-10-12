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
import apincer.android.mmate.objectbox.AudioTag;
import apincer.android.mmate.repository.AudioFileRepository;
import timber.log.Timber;

public class DeleteAudioFileWorker extends Worker {
    AudioFileRepository repos;

    private DeleteAudioFileWorker(
            @NonNull Context context,
            @NonNull WorkerParameters parameters) {
        super(context, parameters);
        repos = AudioFileRepository.newInstance(getApplicationContext());
    }

    @NonNull
    @Override
    public Result doWork() {
       List<AudioTag> tags = MusixMateApp.getPendingItems("Delete");
        for (AudioTag tag:tags) {
            MusicMateExecutors.maintain(new DeleteRunnable(tag));
        }
/*
        while (!mExecutor.getQueue().isEmpty()){
            try {
                Thread.sleep(5000);
            } catch (InterruptedException e) {
            }
        }
        for (AudioTag tag:tags) {
            try {
                boolean status = repos.deleteMediaItem(tag);
            } catch (Exception e) {
                Timber.e(e);
            }
        } */
       // AudioTagEditResultEvent message = new AudioTagEditResultEvent(AudioTagEditResultEvent.ACTION_DELETE, Constants.STATUS_SUCCESS, null);
       // EventBus.getDefault().post(message);

        return Result.success();
    }

    public static void startWorker(Context context, List<AudioTag> files) {
        MusixMateApp.putPendingItems("Delete", files);
            OneTimeWorkRequest workRequest = new OneTimeWorkRequest.Builder(DeleteAudioFileWorker.class).build();
                  //  .setInputData(inputData).build();
            WorkManager.getInstance(context).enqueue(workRequest);
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
                //AudioTagEditResultEvent message = new AudioTagEditResultEvent(AudioTagEditResultEvent.ACTION_DELETE, status?Constants.STATUS_SUCCESS:Constants.STATUS_FAIL, null);
                EventBus.getDefault().postSticky(message);
            } catch (Exception e) {
                Timber.e(e);
            }
        }
    }
}
