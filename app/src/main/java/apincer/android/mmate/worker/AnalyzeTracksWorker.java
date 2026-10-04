package apincer.android.mmate.worker;

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.work.Constraints;
import androidx.work.Data;
import androidx.work.ExistingWorkPolicy;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkManager;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

import apincer.android.mmate.MusixMateApp;
import apincer.music.core.codec.TagReader;
import apincer.music.core.model.Track;
import apincer.music.core.repository.FileRepository;
import apincer.music.core.repository.TagRepository;
import apincer.music.core.utils.LogHelper;

/**
 * Measures dynamic range and other details that need a full decode, for tracks that do not
 * have them yet. Runs as its own work after a scan: the system stops long jobs after a few
 * minutes, and a stopped run resumes from the tracks still missing DR instead of rescanning folders.
 */
public class AnalyzeTracksWorker extends Worker {
    private static final String TAG = LogHelper.getTag(AnalyzeTracksWorker.class);
    public static final String WORK_NAME = "MusicAnalyzeWork";

    public AnalyzeTracksWorker(@NonNull Context context, @NonNull WorkerParameters parameters) {
        super(context, parameters);
    }

    @NonNull
    @Override
    public Result doWork() {
        MusixMateApp app = (MusixMateApp) getApplicationContext();
        FileRepository repos = app.getFileRepository();
        TagRepository tagRepos = app.getTagRepository();

        List<Track> pending = tagRepos.findMyNoDRMeterSongs();
        if (pending == null || pending.isEmpty()) return Result.success();

        int total = pending.size();
        int updateStep = Math.max(10, total / 200);
        AtomicInteger done = new AtomicInteger();
        reportProgress(0, total);
        // Each track is an independent, CPU-bound decode: use half the cores, at most 4
        int threads = Math.max(1, Math.min(4, Runtime.getRuntime().availableProcessors() / 2));
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        try {
            List<Future<?>> tasks = new ArrayList<>(total);
            for (Track track : pending) {
                tasks.add(pool.submit(() -> {
                    if (isStopped()) return;
                    analyze(repos, tagRepos, track);
                    int n = done.incrementAndGet();
                    if (n % updateStep == 0 || n == total) reportProgress(n, total);
                }));
            }
            for (Future<?> task : tasks) {
                task.get();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (ExecutionException e) {
            Log.e(TAG, "Track analysis failed", e.getCause());
        } finally {
            pool.shutdownNow();
        }
        return Result.success();
    }

    private void analyze(FileRepository repos, TagRepository tagRepos, Track track) {
        try {
            TagReader.readExtras(getApplicationContext(), track);
            track.setIsManaged(FileRepository.isManagedInLibrary(getApplicationContext(), track));
            // re-try to extract embedded album art
            repos.saveCoverartToCache(track);
            tagRepos.saveTag(track);
        } catch (Exception | LinkageError e) {
            Log.e(TAG, "Failed to analyze " + track.getPath(), e);
        }
    }

    private void reportProgress(int done, int total) {
        setProgressAsync(new Data.Builder()
                .putInt("progress_value", done)
                .putInt("total_files", total)
                .build());
    }

    static void enqueue(Context context) {
        OneTimeWorkRequest request = new OneTimeWorkRequest.Builder(AnalyzeTracksWorker.class)
                .setConstraints(new Constraints.Builder().setRequiresStorageNotLow(true).build())
                .build();
        WorkManager.getInstance(context).enqueueUniqueWork(WORK_NAME, ExistingWorkPolicy.REPLACE, request);
    }

    /** A scan rewrites the same rows; stop analysis until the scan finishes and queues it again. */
    static void cancel(Context context) {
        WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME);
    }
}
