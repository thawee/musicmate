package apincer.android.mmate.work;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.anggrayudi.storage.file.DocumentFileCompat;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import apincer.android.mmate.repository.FileRepository;
import apincer.android.mmate.repository.MusicTagRepository;
import apincer.android.mmate.repository.TagReader;

public class ScanAudioFileWorker extends Worker {
    FileRepository repos;
    private ScanAudioFileWorker(
            @NonNull Context context,
            @NonNull WorkerParameters parameters) {
        super(context, parameters);
        repos = FileRepository.newInstance(getApplicationContext());
    }

    @NonNull
    @Override
    public Result doWork() {
        MusicTagRepository.cleanMusicMate();

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
            e.printStackTrace();
        }

        return new ArrayList<>();
    }

    private boolean filter(Path path) {
        try {
            String pathString = path.toString();
           return TagReader.isSupportedFileFormat(pathString);
        } catch(Exception e) {
            e.printStackTrace();
        }

        return false;
    }

    public static List<File> pathList(Context context) {
        List<String> storageIds = DocumentFileCompat.getStorageIds(context);
        List<File> files = new ArrayList<>();
        for (String sid : storageIds) {
            File file = new File(DocumentFileCompat.buildAbsolutePath(context, sid, "Music"));
            if (file.exists()) {
                files.add(file);
            }
            file = new File(DocumentFileCompat.buildAbsolutePath(context, sid, "Download"));
            if (file.exists()) {
                files.add(file);
            }
            file = new File(DocumentFileCompat.buildAbsolutePath(context, sid, "IDMP"));
            if (file.exists()) {
                files.add(file);
            }
        }
        return files;
    }
}
