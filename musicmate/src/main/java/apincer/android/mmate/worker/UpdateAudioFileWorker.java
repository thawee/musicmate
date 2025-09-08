package apincer.android.mmate.worker;

import android.content.Context;
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

import apincer.android.mmate.Constants;
import apincer.android.mmate.repository.database.MusicTag;
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

            } catch (Exception e) {
                Log.e(TAG, "save", e);
            }
        });

        // purge previous completed job
        WorkManager.getInstance(getApplicationContext()).pruneWork();

        return Result.success();
    }

    public static void startWorker(Context context, List<MusicTag> files, String artworkPath) {

       // new ViewModelProvider(context.).get(SharedViewModel.class).addSharedItems(Constants.SHARED_TYPE.UPDATE, files);
           // MusixMateApp.addSharedItems(MusixMateApp.SHARED_TYPE.UPDATE, files);

        Gson gson = new Gson();
        String jsonMusicTags = gson.toJson(files);
        if (jsonMusicTags.getBytes(StandardCharsets.UTF_8).length > Data.MAX_DATA_BYTES) {
            Log.e("WorkManager", "MusicTag list is too large to pass as input data.");
            return; // Handle error
        }
        Data inputData = (new Data.Builder())
                .putString(Constants.KEY_COVER_ART_PATH, artworkPath)
                .putString("MUSIC_TAGS_JSON", jsonMusicTags)
                .build();

        OneTimeWorkRequest workRequest = new OneTimeWorkRequest.Builder(UpdateAudioFileWorker.class)
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
       // return MusixMateApp.getSharedItems(MusixMateApp.SHARED_TYPE.UPDATE);
    }

    private boolean save(MusicTag tag) {
            try {
                return repos.setMusicTag(tag);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
    }
}
