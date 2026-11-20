package apincer.android.mmate.worker;

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.work.Constraints;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import apincer.android.mmate.MusixMateApp;
import apincer.music.core.Settings;
import apincer.music.core.model.Track;
import apincer.music.core.playback.LoudnessTagger;
import apincer.music.core.playback.ReplayGainManager;
import apincer.music.core.utils.LogHelper;

/**
 * Writes ReplayGain tags into tracks that have no track gain, while the phone charges. Works album
 * by album and remembers every path it has handled, tagged or failed, so the next run resumes
 * where a stopped one left off instead of re-reading the whole library.
 */
public class LoudnessWorker extends Worker {
    private static final String TAG = LogHelper.getTag(LoudnessWorker.class);
    public static final String WORK_NAME = "MeasureLoudnessWork";
    private static final String CHECKED_FILE = "loudness_checked.txt";

    public LoudnessWorker(@NonNull Context context, @NonNull WorkerParameters parameters) {
        super(context, parameters);
    }

    @NonNull
    @Override
    public Result doWork() {
        Context context = getApplicationContext();
        if (!Settings.isMeasureLoudnessWhileCharging(context)) return Result.success();

        MusixMateApp app = (MusixMateApp) context;
        List<Track> library = app.getTagRepository().getAllMusics();
        if (library == null || library.isEmpty()) return Result.success();

        Set<String> checked = readChecked();
        LoudnessTagger tagger = LoudnessTagger.create(app.getTagRepository());
        ReplayGainManager replayGain = ReplayGainManager.getInstance();
        for (List<Track> album : LoudnessTagger.byAlbum(library)) {
            if (isStopped()) break;
            List<Track> pending = new ArrayList<>(album.size());
            boolean untagged = false;
            for (Track track : album) {
                if (checked.contains(track.getPath())) continue;
                pending.add(track);
                ReplayGainManager.ReplayGainInfo info = replayGain.getReplayGain(track.getPath());
                untagged |= info == null || !info.hasTrackGain;
            }
            if (pending.isEmpty()) continue;

            if (untagged) {
                int[] written = {0};
                tagger.tag(pending, (track, ok) -> {
                    if (ok) written[0]++;
                });
                if (written[0] > 0) Settings.markReplayGainTagsChanged(context);
            }
            List<String> paths = new ArrayList<>(pending.size());
            for (Track track : pending) paths.add(track.getPath());
            checked.addAll(paths);
            appendChecked(paths);
        }
        return Result.success();
    }

    private Set<String> readChecked() {
        File file = new File(getApplicationContext().getFilesDir(), CHECKED_FILE);
        if (!file.exists()) return new HashSet<>();
        try {
            return new HashSet<>(Files.readAllLines(file.toPath(), StandardCharsets.UTF_8));
        } catch (IOException e) {
            Log.w(TAG, "Cannot read " + CHECKED_FILE, e);
            return new HashSet<>();
        }
    }

    private void appendChecked(List<String> paths) {
        File file = new File(getApplicationContext().getFilesDir(), CHECKED_FILE);
        try {
            Files.write(file.toPath(), paths, StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException e) {
            Log.w(TAG, "Cannot write " + CHECKED_FILE, e);
        }
    }

    /** Queues an hourly run while charging when the setting is on, and cancels it when off. */
    public static void schedule(Context context) {
        WorkManager workManager = WorkManager.getInstance(context);
        if (!Settings.isMeasureLoudnessWhileCharging(context)) {
            workManager.cancelUniqueWork(WORK_NAME);
            return;
        }
        PeriodicWorkRequest request = new PeriodicWorkRequest.Builder(LoudnessWorker.class, 1, TimeUnit.HOURS)
                .setConstraints(new Constraints.Builder()
                        .setRequiresCharging(true)
                        .setRequiresBatteryNotLow(true)
                        .setRequiresStorageNotLow(true)
                        .build())
                .build();
        workManager.enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.UPDATE, request);
    }
}
