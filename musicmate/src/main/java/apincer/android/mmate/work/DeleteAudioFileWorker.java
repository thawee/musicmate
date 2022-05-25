package apincer.android.mmate.work;

import android.content.Context;
import android.content.Intent;

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

import org.greenrobot.eventbus.EventBus;

import java.lang.reflect.Type;
import java.util.List;

import apincer.android.mmate.Constants;
import apincer.android.mmate.R;
import apincer.android.mmate.broadcast.AudioTagEditResultEvent;
import apincer.android.mmate.broadcast.BroadcastData;
import apincer.android.mmate.objectbox.AudioTag;
import apincer.android.mmate.repository.AudioFileRepository;

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
        Data inputData = getInputData();
        String s =inputData.getString(Constants.KEY_MEDIA_TAG);
        Gson gson = new Gson();
        Type audioTagType = new TypeToken<AudioTag>(){}.getType();
        AudioTag tag = gson.fromJson(s, audioTagType);
            boolean status = repos.deleteMediaItem(tag);
            String txt = status?getApplicationContext().getString(R.string.alert_delete_success, tag.getTitle()):getApplicationContext().getString(R.string.alert_delete_fail, tag.getTitle());

        AudioTagEditResultEvent message = new AudioTagEditResultEvent(AudioTagEditResultEvent.ACTION_DELETE, status?Constants.STATUS_SUCCESS:Constants.STATUS_FAIL, tag);
        EventBus.getDefault().postSticky(message);

            /*
            BroadcastData data = new BroadcastData()
                    .setAction(BroadcastData.Action.DELETE)
                    .setStatus(status?BroadcastData.Status.COMPLETED: BroadcastData.Status.ERROR)
                    .setTagInfo(tag)
                    .setMessage(txt);
            sendBroadcast(data); */

        return Result.success();
    }

    protected void sendBroadcast(final BroadcastData data){
        Intent intent = data.getIntent();
        LocalBroadcastManager.getInstance(getApplicationContext()).sendBroadcast(intent);
    }

    public static void startWorker(Context context, List<AudioTag> files) {
        Gson gson = new Gson();
        Type audioTagType = new TypeToken<AudioTag>(){}.getType();
        for(AudioTag tag: files) {
            String s = gson.toJson(tag, audioTagType);
            Data inputData = (new Data.Builder())
                    .putString(Constants.KEY_MEDIA_TAG, s)
                    .build();
            WorkRequest workRequest = new OneTimeWorkRequest.Builder(DeleteAudioFileWorker.class)
                    .setInputData(inputData).build();
            WorkManager.getInstance(context).enqueue(workRequest);
        }
    }
}
