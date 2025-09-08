package apincer.android.mmate.worker;

import android.content.Context;
import android.content.Intent;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.work.Data;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkManager;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import apincer.android.mmate.playback.PlaybackService;
import apincer.android.mmate.repository.database.MusicTag;
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

            } catch (Exception e) {
                Log.e(TAG, "importFile", e);
            }
        });
        // purge previous completed job
        WorkManager.getInstance(getApplicationContext()).pruneWork();

        return Result.success();
    }

    public static void startWorker(Context context, List<MusicTag> files) {
        Gson gson = new Gson();
        String jsonMusicTags = gson.toJson(files);
        if (jsonMusicTags.getBytes(StandardCharsets.UTF_8).length > Data.MAX_DATA_BYTES) {
            Log.e("WorkManager", "MusicTag list is too large to pass as input data.");
            return; // Handle error
        }
        Data inputData = (new Data.Builder())
                .putString("MUSIC_TAGS_JSON", jsonMusicTags)
                .build();

      //  MusixMateApp.addSharedItems(MusixMateApp.SHARED_TYPE.IMPORT, files);
            OneTimeWorkRequest workRequest = new OneTimeWorkRequest.Builder(ImportAudioFileWorker.class)
                    .setInputData(inputData)
                    .build();
            WorkManager.getInstance(context).enqueue(workRequest);
    }

    private List<MusicTag> list() {
        String jsonMusicTags = getInputData().getString("MUSIC_TAGS_JSON");
        if (jsonMusicTags != null) {
            Gson gson = new Gson();
            Type listType = new TypeToken<ArrayList<MusicTag>>() {
            }.getType();
            return gson.fromJson(jsonMusicTags, listType);
        }else {
            return new ArrayList<>();
        }
       // return MusixMateApp.getSharedItems(MusixMateApp.SHARED_TYPE.IMPORT);
    }

    private boolean importFile(MusicTag tag) {
        synchronized(repos) {
            skipToNext(tag);
            return repos.importAudioFile(tag);
        }
    }

    private void skipToNext(MusicTag tag) {
        // Create an Intent for the service
        Intent intent = new Intent(getApplicationContext(), PlaybackService.class);

        // Set the action and put the data as an extra
        intent.setAction(PlaybackService.ACTION_SKIP_TO_NEXT);
        intent.putExtra(PlaybackService.EXTRA_MUSIC_ID, tag.getId());

        // Start the service
        getApplicationContext().startService(intent);
    }
}
