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

public class ImportAudioFileWorker extends Worker {
    FileRepository repos;

    private ImportAudioFileWorker(
            @NonNull Context context,
            @NonNull WorkerParameters parameters) {
        super(context, parameters);
        repos = FileRepository.newInstance(getApplicationContext());
    }

    @NonNull
    @Override
    public Result doWork() {
        List<MusicTag> tags = MusixMateApp.getPendingItems("Import");
        for (MusicTag tag:tags) {
            MusicMateExecutors.update(new ImportRunnable(tag));
        }

        return Result.success();
    }

    public static void startWorker(Context context, List<MusicTag> files) {
        MusixMateApp.putPendingItems("Import", files);
            OneTimeWorkRequest workRequest = new OneTimeWorkRequest.Builder(ImportAudioFileWorker.class).build();
            WorkManager.getInstance(context).enqueue(workRequest);
    }

    private final class ImportRunnable  implements Runnable {
        private final MusicTag tag;

        private ImportRunnable(MusicTag tag) {
            this.tag = tag;
        }
        @Override
        public void run() {
            try {
                boolean status = repos.importAudioFile(tag);

                AudioTagEditResultEvent message = new AudioTagEditResultEvent(AudioTagEditResultEvent.ACTION_MOVE, status?Constants.STATUS_SUCCESS:Constants.STATUS_FAIL, tag);
                EventBus.getDefault().postSticky(message);
            } catch (Exception e) {
                Timber.e(e);
            }
        }
    }
}
