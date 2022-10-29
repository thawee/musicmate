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

import apincer.android.mmate.objectbox.MusicTag;
import apincer.android.mmate.repository.FileRepository;
import de.esoco.coroutine.Continuation;
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
        /*
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
        } */
            // for call recordings
            /*
            file = new File(DocumentFileCompat.buildAbsolutePath(getApplicationContext(), sid, "Call"));
            if(file.exists()) {
                MusicMateExecutors.scan(new ScanRunnable(file));
            }
            file = new File(DocumentFileCompat.buildAbsolutePath(getApplicationContext(), sid, "Recordings"));
            if(file.exists()) {
                MusicMateExecutors.scan(new ScanRunnable(file));
            }*/
       // int COROUTINE_COUNT = 10;

        Coroutine<?, ?> cIterating =
                first(supply(this::list)).then(
                        forEach(consume(this::scanDir)));

        launch(
                scope ->
                {
                   // for (int i = 0; i < COROUTINE_COUNT; i++)
                   // {
                        cIterating.runAsync(scope, null);
                   // }
                });

        // schedule scan
        /*
        WorkManager instance = WorkManager.getInstance(getApplicationContext());
        instance.pruneWork();
        Constraints constraints = new Constraints.Builder()
                .setRequiresBatteryNotLow(true)
                .build();
        OneTimeWorkRequest workRequest = new OneTimeWorkRequest.Builder(ScanAudioFileWorker.class)
                .setInitialDelay(10, TimeUnit.MINUTES)
                .setConstraints(constraints)
                .build();
        instance.enqueue(workRequest); */

        return Result.success();
    }

    private void scanMQA(MusicTag tag) {
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
                    repos.scanMusicFiles(f);
                }
            }
        } catch (Exception e) {
            Timber.e(e);
        }
    }

    /*
    private boolean isValidMediaFile(File file) {
        if(!file.exists()) return false;

        String ext = Utils.getExtension(file);
        if(ext.equalsIgnoreCase("mp3")) {
            return true;
        }else if(ext.equalsIgnoreCase("m4a")) {
            return true;
        }else if(ext.equalsIgnoreCase("flac")) {
            return true;
        }else if(ext.equalsIgnoreCase("wav")) {
            return true;
        }else if(ext.equalsIgnoreCase("aif")) {
            return true;
        }else if(ext.equalsIgnoreCase("aiff")) {
            return true;
        }else if(ext.equalsIgnoreCase("dsf")) {
            return true;
        }else if(ext.equalsIgnoreCase("dff")) {
            return true;
        }else return ext.equalsIgnoreCase("iso");
    } */

    private final class ScanRunnable  implements Runnable {
        private final File dir;

        private ScanRunnable(File dir) {
            this.dir = dir;
        }
        @Override
        public void run() {
            try {
                File[] files = dir.listFiles();
                if(files == null) return;

                for (File f : files) {
                    if(!f.exists()) continue;
                    if(f.isDirectory()) {
                        repos.scanMusicFiles(f);
                    } else {
                        MusicMateExecutors.scan(new ScanRunnable(f));
                    }
                }
            } catch (Exception e) {
                Timber.e(e);
            }
        }
    }
}
