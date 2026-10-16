package apincer.android.mmate.worker;

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.work.Constraints;
import androidx.work.Data;
import androidx.work.OneTimeWorkRequest;
import androidx.work.OutOfQuotaPolicy;
import androidx.work.WorkManager;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import apincer.android.mmate.MusixMateApp;
import apincer.music.core.utils.MusicMateExecutors;
import apincer.music.core.codec.TagReader;
import apincer.music.core.provider.FileSystem;
import apincer.music.core.repository.FileRepository;
import apincer.music.core.repository.TagRepository;
import apincer.music.core.utils.LogHelper;

public class ScanAudioFileWorker extends Worker {
    private static final String TAG = LogHelper.getTag(ScanAudioFileWorker.class);
    private static final String WORKER_TAG = "apincer.android.mmate.work.ScanAudioFileWorker";

    // These values will be determined dynamically
    private final int optimalThreadCount;
    private final int optimalBatchSize;

    protected final FileRepository repos;
    protected final TagRepository tagRepos;

    public ScanAudioFileWorker(
            @NonNull Context context,
            @NonNull WorkerParameters parameters) {
        super(context, parameters);

        this.repos = ((MusixMateApp)getApplicationContext()).getFileRepository();
        this.tagRepos = ((MusixMateApp)getApplicationContext()).getTagRepository();

        // Get dynamic configuration based on device capabilities
        optimalThreadCount = getOptimalThreadCount();
        optimalBatchSize = getOptimalBatchSize();

        // Get scan mode (full or incremental)
       // isFullScan = parameters.getInputData().getBoolean("fullScan", false);

        // Get last scan time for incremental scan
        //lastScanTime = Settings.getLastScanTime(context);
    }

    @NonNull
    @Override
    public Result doWork() {
        int processedFiles;

        try {
            boolean isFullScan = getInputData().getBoolean("isFullScan", false);

            if (isFullScan) {
                // do full scan
                repos.cleanCacheCovers();
            }else {
                // Only clean database on not full scan
                tagRepos.cleanInvalidTags();
            }

            List<File> list = pathList(getApplicationContext());
            List<File> allPaths = new ArrayList<>();

            // First gather all paths
            for (File file : list) {
                if (isStopped()) {
                    return Result.failure();
                }
                allPaths.addAll(search(file.getAbsolutePath()));
            }

            // Then process in batches
            processedFiles = processPaths(allPaths, isFullScan);

            if (isFullScan && !isStopped()) {
                // The catalogue was refreshed in place; prune only files missing from readable parents.
                tagRepos.cleanInvalidTags();
            }

            if (isStopped()) {
                return Result.failure();
            }

            // Export playlists (use DB as source)
            //MusicMateExecutors.lowPriority(this::exportPlaylists);
            exportPlaylists();

            // Dynamic range and other decoded details run as separate, resumable work
            AnalyzeTracksWorker.enqueue(getApplicationContext());

            Data outputData = new Data.Builder()
                    .putInt("processedFiles", processedFiles)
                    .build();
            return Result.success(outputData);
        } catch (Exception e) {
            Log.e(TAG, "Failed to complete scan", e);
            return Result.failure();
        }
    }

    private int processPaths(List<File> paths, boolean forceRead) {
        final int totalFiles = paths.size();
        final java.util.concurrent.atomic.AtomicInteger processedCount = new java.util.concurrent.atomic.AtomicInteger(0);

        // Process in smaller batches to reduce memory pressure
        for (int i = 0; i < paths.size(); i += optimalBatchSize) {
            if (isStopped()) {
                break;
            }
            int end = Math.min(i + optimalBatchSize, paths.size());
            List<File> batch = paths.subList(i, end);

            java.util.concurrent.CountDownLatch latch = new java.util.concurrent.CountDownLatch(batch.size());

            // Submit tasks in parallel within the batch
            for (File path : batch) {
                MusicMateExecutors.scan(() -> {
                    try {
                        repos.scanMusicFile(path, forceRead);
                    } finally {
                        int current = processedCount.incrementAndGet();
                        // Throttle progress updates to reduce Binder IPC overhead (every ~1% or 50 files)
                        int updateStep = Math.max(50, totalFiles / 100);
                        if (current % updateStep == 0 || current == totalFiles) {
                            androidx.work.Data progressData = new androidx.work.Data.Builder()
                                    .putInt("progress_value", current)
                                    .putInt("total_files", totalFiles)
                                    .build();
                            setProgressAsync(progressData);
                        }
                        latch.countDown();
                    }
                });
            }

            try {
                latch.await();
            } catch (InterruptedException e) {
                Log.e(TAG, "Batch processing interrupted", e);
                Thread.currentThread().interrupt();
                break;
            }
        }

        return processedCount.get();
    }

    private void exportPlaylists() {
        try {
            // 1. Ensure playlists are loaded + compiled
            apincer.music.core.repository.PlaylistRepository.loadPlaylists(getApplicationContext());

            for (apincer.music.core.model.PlaylistEntry entry : apincer.music.core.repository.PlaylistRepository.getPlaylists()) {
                entry.compileRules(); // IMPORTANT
            }

            // 2. Export
            java.io.File musicDir = android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_MUSIC);
            java.io.File playlistDir = new java.io.File(musicDir, "AA_Playlists");

            // Remove playlists that no longer exist; exportPlaylists recreates the folder
            java.io.File[] oldPlaylists = playlistDir.listFiles();
            if (oldPlaylists != null) {
                for (java.io.File old : oldPlaylists) {
                    if (old.isFile()) apincer.music.core.provider.FileSystem.delete(old);
                }
            }

            apincer.music.core.repository.PlaylistRepository.exportPlaylists(playlistDir, tagRepos);

            Log.d(TAG, "Playlists exported: " + playlistDir.getAbsolutePath());

        } catch (Exception e) {
            Log.e(TAG, "Failed to export playlists", e);
        }
    }

    private List<File> search(String pathname) {
        try {
            // Through the access, so a granted folder also yields the DSF files a path walk cannot see
            return FileSystem.access().walk(new File(pathname), TagReader::isSupportedFileFormat);
        } catch (IOException e) {
            Log.e(TAG, "Error searching path: " + pathname, e);
            return new ArrayList<>();
        }
    }

    public static List<File> pathList(Context context) {
        List<File> files = new ArrayList<>();
        List<String> dirs = TagRepository.getDirectories(context);
        for (String dir : dirs) {
            files.add(new File(dir));
        }
        return files;
    }

    /**
     * Scan requested by the user. A full rescan replaces whatever is queued or running; an
     * incremental scan runs after it instead, so it can never cancel a pending full rescan.
     */
    public static void startScan(Context context, boolean isFullScan) {
        enqueueScan(context, isFullScan, isFullScan
                ? androidx.work.ExistingWorkPolicy.REPLACE
                : androidx.work.ExistingWorkPolicy.APPEND_OR_REPLACE);
    }

    /** Incremental scan at app start; keeps any scan already queued or running. */
    public static void startStartupScan(Context context) {
        enqueueScan(context, false, androidx.work.ExistingWorkPolicy.KEEP);
    }

    private static void enqueueScan(Context context, boolean isFullScan, androidx.work.ExistingWorkPolicy policy) {
        AnalyzeTracksWorker.cancel(context);
        Data inputData = new Data.Builder()
                .putBoolean("isFullScan", isFullScan)
                .build();

        Constraints constraints = new Constraints.Builder()
                //.setRequiresBatteryNotLow(true)
                .setRequiresStorageNotLow(true)
                //.setRequiresDeviceIdle(true)
                .build();

        OneTimeWorkRequest scanRequest = new OneTimeWorkRequest.Builder(ScanAudioFileWorker.class)
                .setConstraints(constraints)
                // This is the magic flag for Android 12+
                .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
                //.setInitialDelay(SCAN_SCHEDULE_TIME, TimeUnit.SECONDS)
                .addTag(WORKER_TAG)
                .setInputData(inputData)
                .build();

        WorkManager.getInstance(context).enqueueUniqueWork("MusicScanWork", policy, scanRequest);
    }

    // Dynamically adjust based on device capabilities
    private int getOptimalThreadCount() {
        int availableProcessors = Runtime.getRuntime().availableProcessors();
        // Use at most half of available processors, between 1-4 threads
        return Math.max(1, Math.min(4, availableProcessors / 2));
    }

    // Adjust batch size based on available memory
    private int getOptimalBatchSize() {
        Runtime runtime = Runtime.getRuntime();
        long maxMemory = runtime.maxMemory() / (1024 * 1024); // MB

        // Scale batch size based on available memory
        if (maxMemory > 512) return 50;
        if (maxMemory > 256) return 30;
        return 15;
    }

}
