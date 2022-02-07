package apincer.android.mmate.work;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import androidx.work.Data;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkManager;
import androidx.work.WorkRequest;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.util.List;

import apincer.android.mmate.Constants;
import apincer.android.mmate.R;
import apincer.android.mmate.objectbox.AudioTag;
import apincer.android.mmate.repository.AudioFileRepository;
import apincer.android.mmate.service.BroadcastData;

public class UpdateAudioFileWorker extends Worker {
    private UpdateAudioFileWorker(
            @NonNull Context context,
            @NonNull WorkerParameters parameters) {
        super(context, parameters);
    }

    @NonNull
    @Override
    public Result doWork() {
        Data inputData = getInputData();
        String s = inputData.getString(Constants.KEY_MEDIA_TAG);
        String artworkPath = inputData.getString(Constants.KEY_COVER_ART_PATH);
        Gson gson = new Gson();
        Type audioTagType = new TypeToken<AudioTag>(){}.getType();
        AudioTag tag = gson.fromJson(s, audioTagType);
            boolean status = AudioFileRepository.getInstance(getApplicationContext()).saveAudioFile(tag, artworkPath);
            String txt = status?getApplicationContext().getString(R.string.alert_write_tag_success, tag.getTitle()):getApplicationContext().getString(R.string.alert_write_tag_fail, tag.getTitle());

            BroadcastData data = new BroadcastData()
                    .setAction(BroadcastData.Action.UPDATE)
                    .setStatus(status?BroadcastData.Status.COMPLETED: BroadcastData.Status.ERROR)
                    .setTagInfo(tag)
                    .setMessage(txt);
            sendBroadcast(data);

        return Result.success();
    }

    protected void sendBroadcast(final BroadcastData data){
        Intent intent = data.getIntent();
        LocalBroadcastManager.getInstance(getApplicationContext()).sendBroadcast(intent);
    }

    public static void startWorker(Context context, List<AudioTag> files, String artworkPath) {
        Gson gson = new Gson();
        Type audioTagType = new TypeToken<AudioTag>(){}.getType();
        for(AudioTag tag: files) {
            String s = gson.toJson(tag, audioTagType);
            Data inputData = (new Data.Builder())
                    .putString(Constants.KEY_MEDIA_TAG, s)
                    .putString(Constants.KEY_COVER_ART_PATH, artworkPath)
                    .build();
            WorkRequest workRequest = new OneTimeWorkRequest.Builder(UpdateAudioFileWorker.class)
                    .setInputData(inputData).build();
            WorkManager.getInstance(context).enqueue(workRequest);
        }
    }
}
