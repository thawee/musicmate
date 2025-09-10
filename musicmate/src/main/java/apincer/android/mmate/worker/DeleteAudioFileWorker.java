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

import com.google.gson.ExclusionStrategy;
import com.google.gson.FieldAttributes;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import apincer.android.mmate.playback.PlaybackService;
import apincer.android.mmate.repository.FileRepository;
import apincer.android.mmate.repository.database.MusicTag;

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
                boolean status = delete(tag);
        });
        // purge previous completed job
        WorkManager.getInstance(getApplicationContext()).pruneWork();

        return Result.success();
    }

    public static void startWorker(Context context, List<MusicTag> files) {
       // Gson gson = new Gson();
        Gson gson = new GsonBuilder()
                .setExclusionStrategies(new ExclusionStrategy(){
                    @Override
                    public boolean shouldSkipField(FieldAttributes f) {
                        return !f.getName().equals("id");
                        //return f.getName().equals("waveformData") || f.getName().equals("simpleName");
                    }

                    @Override
                    public boolean shouldSkipClass(Class<?> clazz) {
                        return false;
                    }
                })
                .create();

        String jsonMusicTags = gson.toJson(files);
        if (jsonMusicTags.getBytes(StandardCharsets.UTF_8).length > Data.MAX_DATA_BYTES) {
            Log.e("WorkManager", "MusicTag list is too large to pass as input data.");
            return; // Handle error
        }
        Data inputData = (new Data.Builder())
                .putString("MUSIC_TAGS_JSON", jsonMusicTags)
                .build();

            OneTimeWorkRequest workRequest = new OneTimeWorkRequest.Builder(DeleteAudioFileWorker.class)
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
       // return MusixMateApp.getSharedItems(MusixMateApp.SHARED_TYPE.DELETE);
    }

    private synchronized boolean delete(MusicTag tag) {
        // Synchronize on repos if needed
       // synchronized(repos) {
            skipToNext(tag);
            return repos.deleteMediaItem(tag);
      //  }
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
