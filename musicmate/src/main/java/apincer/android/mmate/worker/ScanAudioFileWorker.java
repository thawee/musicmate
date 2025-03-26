package apincer.android.mmate.worker;

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.work.Constraints;
import androidx.work.Data;
import androidx.work.OneTimeWorkRequest;
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
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import apincer.android.mmate.Settings;
import apincer.android.mmate.codec.TagReader;
import apincer.android.mmate.repository.FileRepository;
import apincer.android.mmate.repository.TagRepository;
import apincer.android.mmate.utils.LogHelper;

public class ScanAudioFileWorker extends Worker {
    private static final String TAG = LogHelper.getTag(ScanAudioFileWorker.class);
    private static final String WORKER_TAG = "apincer.android.mmate.work.ScanAudioFileWorker";
    private static final int MAX_THREADS = 2; // Limit to 2 working threads
    private static final int BATCH_SIZE = 30; // Smaller batch size to reduce memory pressure
    private static final int PAUSE_BETWEEN_BATCHES_MS = 100; // Reduce CPU pressure

    static final long SCAN_SCHEDULE_TIME = 5;
    private final FileRepository repos;

    public ScanAudioFileWorker(
            @NonNull Context context,
            @NonNull WorkerParameters parameters) {
        super(context, parameters);
        repos = FileRepository.newInstance(getApplicationContext());
    }

    @NonNull
    @Override
    public Result doWork() {
        int processedFiles = 0;

        try {
            TagRepository.cleanMusicMate();

            List<File> list = pathList(getApplicationContext());
            List<Path> allPaths = new ArrayList<>();

            // First gather all paths
            for (File file : list) {
                allPaths.addAll(search(file.getAbsolutePath()));
            }

            // Then process in batches
            processedFiles = processPaths(allPaths);

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
        for (int i = 0; i < paths.size(); i += BATCH_SIZE) {
            int end = Math.min(i + BATCH_SIZE, paths.size());
            List<Path> batch = paths.subList(i, end);

            // Sequential processing within the batch to reduce CPU load
            for (Path path : batch) {
                MusicMateExecutors.scan(() -> repos.scanMusicFile(path.toFile(), false));
                processedCount++;
            }

            // Report progress
           /* setProgressAsync(new Data.Builder()
                    .putInt("progress", end)
                    .putInt("total", paths.size())
                    .build()); */

            // Add a small delay between batches to prevent CPU overload
            try {
                Thread.sleep(PAUSE_BETWEEN_BATCHES_MS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        return processedCount;
    }

    private List<Path> search(String pathname) {
        List<Path> result = new ArrayList<>();
        try {
            // Limit to exactly 2 threads for file searching
            ForkJoinPool customThreadPool = new ForkJoinPool(MAX_THREADS);

            customThreadPool.submit(() -> {
                try (Stream<Path> pathStream = Files.walk(Paths.get(pathname))) {
                    pathStream.filter(this::filter).forEach(result::add);
                } catch (IOException ignored) {}
            }).get();

            // Explicitly shutdown the pool to free resources
            customThreadPool.shutdown();
        } catch (Exception e) {
            Log.e(TAG, "Error searching path: " + pathname, e);
        }
        return result;
    }

    private boolean filter(Path path) {
        try {
            String pathString = path.toString();
            return TagReader.isSupportedFileFormat(pathString);
        } catch (Exception e) {
            Log.e(TAG, "filter", e);
        }
        return false;
    }

    public static List<File> pathList(Context context) {
        List<File> files = new ArrayList<>();
        List<String> dirs = Settings.getDirectories(context);
        for (String dir : dirs) {
            files.add(new File(dir));
        }
        return files;
    }

    public static void startScan(Context context) {
        WorkManager.getInstance(context).cancelAllWorkByTag(WORKER_TAG);

        Data inputData = new Data.Builder()
                .putBoolean("fullScan", true)
                .build();

        Constraints constraints = new Constraints.Builder()
                .setRequiresBatteryNotLow(true)
                .setRequiresStorageNotLow(true)
                // .setRequiresDeviceIdle(true)
                .build();

        OneTimeWorkRequest workRequest = new OneTimeWorkRequest.Builder(ScanAudioFileWorker.class)
                .setInputData(inputData)
                .setInitialDelay(SCAN_SCHEDULE_TIME, TimeUnit.SECONDS)
                .setConstraints(constraints)
                .addTag(WORKER_TAG)
                .build();
        WorkManager.getInstance(context).enqueue(workRequest);
    }
}
