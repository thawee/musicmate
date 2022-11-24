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
import java.util.ArrayList;
import java.util.List;

import apincer.android.mmate.repository.FileRepository;
import apincer.android.mmate.repository.MusicTagRepository;
import de.esoco.coroutine.Coroutine;
import timber.log.Timber;

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
       // int COROUTINE_COUNT = 10;
        MusicTagRepository.cleanMusicMate();

        // ExecutorService with timeout
        /*List<File> files = list();
        for(File file: files) {
            MusicMateExecutors.scan(new Runnable() {
                @Override
                public void run() {
                    scanDir(file);
                }
            }, 60);
        } */

        // co-routines
        Coroutine<?, ?> cIterating =
                first(supply(this::list)).then(
                        forEach(consume(this::scanDir)));

        launch(
                scope ->
                {
                        cIterating.runAsync(scope, null);

                });

        return Result.success();
    }

    private List<File> list() {
        List<String> storageIds = DocumentFileCompat.getStorageIds(getApplicationContext());
        List<File> files = new ArrayList<>();
        for (String sid : storageIds) {
            File file = new File(DocumentFileCompat.buildAbsolutePath(getApplicationContext(), sid, "Music"));
            if (file.exists()) {
                // MusicMateExecutors.scan(new ScanRunnable(file));
                files.add(file);
            }
            file = new File(DocumentFileCompat.buildAbsolutePath(getApplicationContext(), sid, "Download"));
            if (file.exists()) {
                // MusicMateExecutors.scan(new ScanRunnable(file));
                files.add(file);
            }
            file = new File(DocumentFileCompat.buildAbsolutePath(getApplicationContext(), sid, "IDMP"));
            if (file.exists()) {
                // MusicMateExecutors.scan(new ScanRunnable(file));
                files.add(file);
            }
        }
        return files;
    }

    private void scanDir(File file) {
        try {
            File[] files = file.listFiles();
            if(files == null) return;
            for (File f : files) {
                if(!f.exists()) continue;
                if(f.isDirectory()) {
                    scanDir(f);
                }else {
                    repos.scanMusicFile(f);
                    //scanFile(f);
                }
            }
        } catch (Exception e) {
            Timber.e(e);
        }
    }

    private void scanFile(File file) {
        MusicMateExecutors.scan(new Runnable() {
            @Override
            public void run() {
                repos.scanMusicFile(file);
            }
        }, 60);
    }
}
