package apincer.android.mmate.work;

import static de.esoco.coroutine.Coroutine.first;
import static de.esoco.coroutine.CoroutineScope.launch;
import static de.esoco.coroutine.step.CodeExecution.consume;
import static de.esoco.coroutine.step.CodeExecution.supply;
import static de.esoco.coroutine.step.Iteration.forEach;

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
import de.esoco.coroutine.Coroutine;
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
       /* List<MusicTag> tags = MusixMateApp.getPendingItems("Update");

     //   Profiler profiler = new Profiler();
        //Thread
        for (MusicTag tag:tags) {
            MusicMateExecutors.update(new UpdateRunnable(tag, artworkPath));
        }*/
        /* executor = (ThreadPoolExecutor) MusicMateExecutors.getInstance().update();
        while (executor.getActiveCount() != 0 || !executor.getQueue().isEmpty()){
            try {
                Thread.sleep(500);
            } catch (InterruptedException e) {
            }
        } */
       // profiler.measure(String.format("%,9d Threads", 8));
       // profiler.printSummary();

        // not support save coverart yet
        //int COROUTINE_COUNT = 4;

        Coroutine<?, ?> cIterating =
                first(supply(this::list)).then(
                        forEach(consume(this::save)));

        launch(
                scope ->
                {
                    //for (int i = 0; i < COROUTINE_COUNT; i++)
                   // {
                        cIterating.runAsync(scope, null);
                  //  }
                });

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

    private List<MusicTag> list() {
        return MusixMateApp.getPendingItems("Update");
    }

    private void save(MusicTag tag) {
        try {
            boolean status = repos.setMusicTag(tag, null);
            // String txt = status?getApplicationContext().getString(R.string.alert_write_tag_success, tag.getTitle()):getApplicationContext().getString(R.string.alert_write_tag_fail, tag.getTitle());
            AudioTagEditResultEvent message = new AudioTagEditResultEvent(AudioTagEditResultEvent.ACTION_UPDATE, status?Constants.STATUS_SUCCESS:Constants.STATUS_FAIL, tag);
            EventBus.getDefault().postSticky(message);
        } catch (Exception e) {
            Timber.e(e);
        }
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
                boolean status = repos.setMusicTag(tag, artworkPath);
               // String txt = status?getApplicationContext().getString(R.string.alert_write_tag_success, tag.getTitle()):getApplicationContext().getString(R.string.alert_write_tag_fail, tag.getTitle());
                AudioTagEditResultEvent message = new AudioTagEditResultEvent(AudioTagEditResultEvent.ACTION_UPDATE, status?Constants.STATUS_SUCCESS:Constants.STATUS_FAIL, tag);
                EventBus.getDefault().postSticky(message);
            } catch (Exception e) {
                Timber.e(e);
            }
        }
    }
}
