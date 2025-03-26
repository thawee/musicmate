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
import apincer.android.mmate.repository.FileRepository;
import apincer.android.mmate.repository.MusicTag;

public class DeleteAudioFileWorker extends Worker {
    private static final String TAG = DeleteAudioFileWorker.class.getName();
    final FileRepository repos;

    private DeleteAudioFileWorker(
            @NonNull Context context,
            @NonNull WorkerParameters parameters) {
        super(context, parameters);
        repos = FileRepository.newInstance(getApplicationContext());
    }

    @NonNull
    @Override
    public Result doWork() {
       List<MusicTag> list = list();
       //list.parallelStream().forEach(this::delete);

        list.forEach(tag -> {
            try {
                boolean status = delete(tag);

                AudioTagEditResultEvent message = new AudioTagEditResultEvent(
                        AudioTagEditResultEvent.ACTION_DELETE,
                        status ? Constants.STATUS_SUCCESS : Constants.STATUS_FAIL,
                        tag);
                EventBus.getDefault().postSticky(message);
            } catch (Exception e) {
                Log.e(TAG, "delete", e);
            }
        });
        // purge previous completed job
        WorkManager.getInstance(getApplicationContext()).pruneWork();

        return Result.success();
    }

    public static void startWorker(Context context, List<MusicTag> files) {
        MusixMateApp.putPendingItems("Delete", files);
            OneTimeWorkRequest workRequest = new OneTimeWorkRequest.Builder(DeleteAudioFileWorker.class).build();
            WorkManager.getInstance(context).enqueue(workRequest);
    }

    private List<MusicTag> list() {
        return MusixMateApp.getPendingItems("Delete");
    }

    private boolean delete(MusicTag tag) {
       /* try {
            boolean status = repos.deleteMediaItem(tag);

            AudioTagEditResultEvent message = new AudioTagEditResultEvent(AudioTagEditResultEvent.ACTION_DELETE, status?Constants.STATUS_SUCCESS:Constants.STATUS_FAIL, tag);
            EventBus.getDefault().postSticky(message);
        } catch (Exception e) {
            Log.e(TAG, "delete",e);
        }
        return false; */
        // Synchronize on repos if needed
        synchronized(repos) {
            return repos.deleteMediaItem(tag);
        }
    }
}
