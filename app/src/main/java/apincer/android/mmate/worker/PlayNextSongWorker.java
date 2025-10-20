package apincer.android.mmate.worker;

import android.content.Context;
import android.content.Intent;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import apincer.android.mmate.service.MusicMateServiceImpl;
import apincer.music.core.playback.spi.PlaybackService;

public class PlayNextSongWorker extends Worker {

    public PlayNextSongWorker(@NonNull Context context, @NonNull WorkerParameters workerParams) {
        super(context, workerParams);
    }

    @NonNull
    @Override
    public Result doWork() {
        // This is the code that will run in the background
        Log.d("PlayNextSongWorker", "play next song.");
        // Create an intent targeted at your MusicMateService
        Intent intent = new Intent(getApplicationContext(), MusicMateServiceImpl.class);

        // Set the unique action (the command)
        intent.setAction(PlaybackService.ACTION_PLAY_NEXT);

        // Start the service with this command
        getApplicationContext().startService(intent);
        return Result.success();
    }
}