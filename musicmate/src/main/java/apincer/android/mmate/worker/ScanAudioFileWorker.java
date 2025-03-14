package apincer.android.mmate.worker;

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.work.Constraints;
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
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import apincer.android.mmate.Settings;
import apincer.android.mmate.codec.TagReader;
import apincer.android.mmate.repository.FileRepository;
import apincer.android.mmate.repository.TagRepository;
import apincer.android.mmate.utils.LogHelper;

public class ScanAudioFileWorker extends Worker {
    private static final String TAG = LogHelper.getTag(ScanAudioFileWorker.class);
    FileRepository repos;
    private static final long SCAN_SCHEDULE_TIME = 5;
    private ScanAudioFileWorker(
            @NonNull Context context,
            @NonNull WorkerParameters parameters) {
        super(context, parameters);
        repos = FileRepository.newInstance(getApplicationContext());
    }

    @NonNull
    @Override
    public Result doWork() {
        TagRepository.cleanMusicMate();

        List<File> list = pathList(getApplicationContext());
        for(File file: list) {
            List<Path> paths = search(file.getAbsolutePath());
            for(Path path: paths) {
                MusicMateExecutors.scan(() -> repos.scanMusicFile(path.toFile(), false));
            }
        }

        return Result.success();
    }

    private List<Path> search(String pathname) {
        try (Stream<Path> pathStream = Files.walk(Paths.get(pathname))) {
            return pathStream
                    .parallel()
                    .filter(this::filter)
                    .collect(Collectors.toList());
        } catch (IOException e) {
            Log.e(TAG, "search", e);
        }

        return new ArrayList<>();
    }

    private boolean filter(Path path) {
        try {
            String pathString = path.toString();
           return TagReader.isSupportedFileFormat(pathString);
        } catch(Exception e) {
            Log.e(TAG, "filter", e);
        }

        return false;
    }

    public static List<File> pathList(Context context) {
        List<File> files = new ArrayList<>();
        List<String> dirs = Settings.getDirectories(context);
        for(String dir: dirs) {
            files.add(new File(dir));
        }
        return files;
    }

    public static void startScan(Context context) {
        WorkManager.getInstance(context).cancelAllWorkByTag("apincer.android.mmate.work.ScanAudioFileWorker");

        Constraints constraints = new Constraints.Builder()
                .setRequiresBatteryNotLow(true)
                .setRequiresStorageNotLow(true)
                .build();
        OneTimeWorkRequest workRequest = new OneTimeWorkRequest.Builder(ScanAudioFileWorker.class)
                .setInitialDelay(SCAN_SCHEDULE_TIME, TimeUnit.SECONDS)
                .setConstraints(constraints)
                .build();
        WorkManager.getInstance(context).enqueue(workRequest);
    }
}
