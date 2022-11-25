package apincer.android.mmate.work;

import static de.esoco.coroutine.Coroutine.first;
import static de.esoco.coroutine.CoroutineScope.launch;
import static de.esoco.coroutine.step.CodeExecution.consume;
import static de.esoco.coroutine.step.CodeExecution.supply;
import static de.esoco.coroutine.step.Iteration.forEach;

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

import apincer.android.mmate.repository.FFMPeg;
import apincer.android.mmate.repository.FileRepository;
import apincer.android.mmate.repository.MusicTagRepository;
import de.esoco.coroutine.Coroutine;
import timber.log.Timber;

public class ScanAudioFileWorker extends Worker {
    FileRepository repos;
    //private static final int NUMBER_OF_CORES = Runtime.getRuntime().availableProcessors();
   // ExecutorService service;
    private ScanAudioFileWorker(
            @NonNull Context context,
            @NonNull WorkerParameters parameters) {
        super(context, parameters);
        repos = FileRepository.newInstance(getApplicationContext());
        //service = new ThreadPoolExecutor(1, NUMBER_OF_CORES,0L, TimeUnit.MILLISECONDS, new LinkedBlockingQueue<Runnable>());
      //  service = Executors.newFixedThreadPool(NUMBER_OF_CORES);
    }

    @NonNull
    @Override
    public Result doWork() {
       // int COROUTINE_COUNT = 10;
        MusicTagRepository.cleanMusicMate();

        List<File> list = list();
        for(File file: list) {
            List<Path> paths = search(file.getAbsolutePath());
            for(Path path: paths) {
                MusicMateExecutors.scan(() -> repos.scanMusicFile(path.toFile()));
            }
        }

        // ExecutorService with timeout
        /*
        List<File> list = list();
        for(File file: list) {
            try {
                File[] files = file.listFiles();
                if(files != null) {
                    for (File f : files) {
                        if (!f.exists()) continue;
                        if (f.isDirectory()) {
                            scan(f);
                        } else {
                            service.execute(()->repos.scanMusicFile(f));
                           // MusicMateExecutors.scan(() -> repos.scanMusicFile(f));
                        }
                    }
                }
            } catch (Exception e) {
                Timber.e(e);
            }
        } */

     /*   service.shutdown();
        while (true) {
            try {
                if (service.isTerminated())
                    break;
                if (service.awaitTermination(5000, TimeUnit.MILLISECONDS)) {
                    break;
                }
            } catch (InterruptedException ignored) {}
        } */

        // co-routines
        /*
        Coroutine<?, ?> cIterating =
                first(supply(this::list)).then(
                        forEach(consume(this::scanDir)));

        launch(
                scope ->
                {
                        cIterating.runAsync(scope, null);

                }); */



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
           return FFMPeg.isSupportedFileFormat(pathString);
        } catch(Exception e) {
            e.printStackTrace();
        }

        return false;
    }

    private List<File> list() {
        List<String> storageIds = DocumentFileCompat.getStorageIds(getApplicationContext());
        List<File> files = new ArrayList<>();
        for (String sid : storageIds) {
            File file = new File(DocumentFileCompat.buildAbsolutePath(getApplicationContext(), sid, "Music"));
            if (file.exists()) {
                files.add(file);
            }
            file = new File(DocumentFileCompat.buildAbsolutePath(getApplicationContext(), sid, "Download"));
            if (file.exists()) {
                files.add(file);
            }
            file = new File(DocumentFileCompat.buildAbsolutePath(getApplicationContext(), sid, "IDMP"));
            if (file.exists()) {
                files.add(file);
            }
        }
        return files;
    }
}
