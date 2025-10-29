package apincer.android.mmate.worker;

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.work.Constraints;
import androidx.work.Data;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.OneTimeWorkRequest;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import apincer.android.mmate.MusixMateApp;
import apincer.music.core.database.MusicTag;
import apincer.music.core.utils.MusicMateExecutors;
import apincer.android.mmate.Settings;
import apincer.music.core.codec.TagReader;
import apincer.music.core.repository.FileRepository;
import apincer.music.core.repository.TagRepository;
import apincer.music.core.utils.LogHelper;

public class ScanAudioFileWorker extends Worker {
    private static final String TAG = LogHelper.getTag(ScanAudioFileWorker.class);
    private static final String WORKER_TAG = "apincer.android.mmate.work.ScanAudioFileWorker";

    // These values will be determined dynamically
    private final int optimalThreadCount;
    private final int optimalBatchSize;

    static final long SCAN_SCHEDULE_TIME = 5;
    protected final FileRepository repos;
    protected final TagRepository tagRepos;

    public ScanAudioFileWorker(
            @NonNull Context context,
            @NonNull WorkerParameters parameters) {
           // FileRepository repos, // 3. Hilt provides this dependency automatically!
           // TagRepository tagRepos) {
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
            // Only clean database on full scan
            tagRepos.cleanMusicMate();

            List<File> list = pathList(getApplicationContext());
            List<Path> allPaths = new ArrayList<>();

            // First gather all paths
            for (File file : list) {
                allPaths.addAll(search(file.getAbsolutePath()));
            }

            // Then process in batches
            processedFiles = processPaths(allPaths);

            // start scan extras
            MusicMateExecutors.lowPriority(() -> {
                        List<MusicTag> BasicList = tagRepos.findMyNoDRMeterSongs();
                        for (MusicTag basicTag : BasicList) {
                            try {
                                //full scan
                                TagReader.readExtras(getApplicationContext(), basicTag);
                               //     basicTag.setMusicManaged(repos.isManagedInLibrary(basicTag));
                                //}

                                basicTag.setMusicManaged(repos.isManagedInLibrary(basicTag));

                                // re-try to extract embed album art
                                repos.saveCoverartToCache(basicTag);
                                tagRepos.saveTag(basicTag);
                            } catch(Exception e) {
                                Log.e(TAG, "Error extracting cover art", e);
                            }
                        }
                    });

            // Save current time as last scan time
            Settings.setLastScanTime(getApplicationContext(), System.currentTimeMillis());

            Data outputData = new Data.Builder()
                    .putInt("processedFiles", processedFiles)
                    .build();
            return Result.success(outputData);
        } catch (Exception e) {
            Log.e(TAG, "Failed to complete scan", e);
            return Result.failure();
        }
    }

    private int processPaths(List<Path> paths) {
        int processedCount = 0;

        // Process in smaller batches to reduce memory pressure
        for (int i = 0; i < paths.size(); i += optimalBatchSize) {
            int end = Math.min(i + optimalBatchSize, paths.size());
            List<Path> batch = paths.subList(i, end);

            // Sequential processing within the batch to reduce CPU load
            for (Path path : batch) {
                MusicMateExecutors.scan(() -> repos.scanMusicFile(path.toFile(), false));
                processedCount++;
            }
        }

        return processedCount;
    }

    private List<Path> search(String pathname) throws ExecutionException, InterruptedException {
        List<Path> result = new ArrayList<>();
      //  try {
            // Use optimal thread count for file searching
            try (ForkJoinPool customThreadPool = new ForkJoinPool(optimalThreadCount)) {

                customThreadPool.submit(() -> {
                    try {
                        // First do a quick filter by extension to improve performance
                        try (Stream<Path> pathStream = Files.walk(Paths.get(pathname))) {
                            pathStream
                                    .filter(this::filter)
                                    .forEach(result::add);
                        }
                    } catch (IOException ignored) {
                    }
                }).get();

                // Explicitly shutdown the pool to free resources
                customThreadPool.shutdown();
            }
      //  } catch (Exception e) {
      //      Log.e(TAG, "Error searching path: " + pathname, e);
      //  }
        return result;
    }

    // Only scan new or modified files
    private boolean filter(Path path) {
        try {
            String pathString = path.toString();
           // System.out.println("Filter: "+pathString);
            if (!TagReader.isSupportedFileFormat(pathString)) {
               // System.out.println("  NOT SUPPORT!, "+pathString);
                return false;
            }

            if(pathString.toLowerCase().contains("download")) {
                // always re-scan in-comming songs
                return true;
            }

            return true;
        } catch (Exception e) {
            Log.e(TAG, "filter", e);
        }
        return false;
    }

    public static List<File> pathList(Context context) {
        List<File> files = new ArrayList<>();
        List<String> dirs = TagRepository.getDirectories(context);
        for (String dir : dirs) {
            files.add(new File(dir));
        }
        return files;
    }

    // One-time scan (can be full)
    public static void startScan(Context context) {
        WorkManager.getInstance(context).cancelAllWorkByTag(WORKER_TAG);

        Constraints constraints = new Constraints.Builder()
                .setRequiresBatteryNotLow(true)
                .setRequiresStorageNotLow(true)
                .build();

        OneTimeWorkRequest workRequest = new OneTimeWorkRequest.Builder(ScanAudioFileWorker.class)
               // .setInputData(inputData)
                .setInitialDelay(SCAN_SCHEDULE_TIME, TimeUnit.SECONDS)
                .setConstraints(constraints)
                .addTag(WORKER_TAG)
                .build();
        WorkManager.getInstance(context).enqueue(workRequest);
    }

    // Schedule regular incremental scans
    public static void scheduleRegularScans(Context context) {
        Constraints constraints = new Constraints.Builder()
                .setRequiresBatteryNotLow(true)
                .setRequiresStorageNotLow(true)
                .setRequiresDeviceIdle(true)  // Only when device is idle
                .build();

        PeriodicWorkRequest periodicWorkRequest = new PeriodicWorkRequest.Builder(
                ScanAudioFileWorker.class,
                24, TimeUnit.HOURS)  // Run once per day
               // .setInputData(inputData)
                .setConstraints(constraints)
                .addTag(WORKER_TAG + "_PERIODIC")
                .build();

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                "PERIODIC_MUSIC_SCAN",
                ExistingPeriodicWorkPolicy.UPDATE,
                periodicWorkRequest);
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

    // Determine optimal pause time based on device performance
    private int getOptimalPauseTime() {
        Runtime runtime = Runtime.getRuntime();
        long maxMemory = runtime.maxMemory() / (1024 * 1024); // MB

        // Lower-end devices need more time to recover
        if (maxMemory < 256) return 200;
        if (maxMemory < 512) return 150;
        return 100;
    }
}
