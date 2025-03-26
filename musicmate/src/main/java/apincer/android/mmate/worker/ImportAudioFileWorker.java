package apincer.android.mmate.worker;

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;
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

public class ImportAudioFileWorker extends Worker {
    private static final String TAG = ImportAudioFileWorker.class.getName();
    private final FileRepository repos;

    private ImportAudioFileWorker(
            @NonNull Context context,
            @NonNull WorkerParameters parameters) {
        super(context, parameters);
        repos = FileRepository.newInstance(getApplicationContext());
    }

    @NonNull
    @Override
    public Result doWork() {
        List<MusicTag> list = list();
       // list.parallelStream().forEach(this::importFile);
        list.forEach(tag -> {
            try {
                boolean status = importFile(tag);

                AudioTagEditResultEvent message = new AudioTagEditResultEvent(
                        AudioTagEditResultEvent.ACTION_MOVE,
                        status ? Constants.STATUS_SUCCESS : Constants.STATUS_FAIL,
                        tag);
                EventBus.getDefault().postSticky(message);
            } catch (Exception e) {
                Log.e(TAG, "importFile", e);
            }
        });
        // purge previous completed job
        WorkManager.getInstance(getApplicationContext()).pruneWork();

        return Result.success();
    }

    public static void startWorker(Context context, List<MusicTag> files) {
        MusixMateApp.putPendingItems("Import", files);
            OneTimeWorkRequest workRequest = new OneTimeWorkRequest.Builder(ImportAudioFileWorker.class).build();
            WorkManager.getInstance(context).enqueue(workRequest);
    }

    private List<MusicTag> list() {
        return MusixMateApp.getPendingItems("Import");
    }

    private boolean importFile(MusicTag tag) {
       /* try {
            boolean status = repos.importAudioFile(tag);

            AudioTagEditResultEvent message = new AudioTagEditResultEvent(AudioTagEditResultEvent.ACTION_MOVE, status?Constants.STATUS_SUCCESS:Constants.STATUS_FAIL, tag);
            EventBus.getDefault().postSticky(message);
        } catch (Exception e) {
            Log.e(TAG,"importFile",e);
        }
        return false; */
        synchronized(repos) {
            return repos.importAudioFile(tag);
        }
    }
}
