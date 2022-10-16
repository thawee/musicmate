package apincer.android.mmate.work;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.anggrayudi.storage.file.DocumentFileCompat;

import org.jaudiotagger.audio.generic.Utils;

import java.io.File;
import java.util.List;

import apincer.android.mmate.repository.FileRepository;
import apincer.android.mmate.repository.MusicTagRepository;
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
        List<String> storageIds = DocumentFileCompat.getStorageIds(getApplicationContext());
        for (String sid : storageIds) {
            File file = new File(DocumentFileCompat.buildAbsolutePath(getApplicationContext(), sid, "Music"));
            if(file.exists()) {
                MusicMateExecutors.scan(new ScanRunnable(file));
            }
            file = new File(DocumentFileCompat.buildAbsolutePath(getApplicationContext(), sid, "Download"));
            if(file.exists()) {
                //ScanRunnable r = new ScanRunnable(file);
                //mExecutor.execute(r);
                MusicMateExecutors.scan(new ScanRunnable(file));
            }
            file = new File(DocumentFileCompat.buildAbsolutePath(getApplicationContext(), sid, "IDMP"));
            if(file.exists()) {
                //ScanRunnable r = new ScanRunnable(file);
                //mExecutor.execute(r);
                MusicMateExecutors.scan(new ScanRunnable(file));
            }
        }
        MusicTagRepository.cleanMusicMate();

        return Result.success();
    }


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
    }

    private final class ScanRunnable  implements Runnable {
        private final File dir;

        private ScanRunnable(File dir) {
            this.dir = dir;
        }
        @Override
        public void run() {
            try {
                //Timber.i("scanning"+ dir+":"+ new Date());
                File[] files = dir.listFiles();
                if(files == null) return;

                for (File f : files) {
                    if(!f.exists()) continue;
                    if(isValidMediaFile(f)) {
                        repos.scanFileAndSaveTag(f);
                    } else if(f.isDirectory()) {
                        MusicMateExecutors.scan(new ScanRunnable(f));
                       // ScanRunnable r = new ScanRunnable(f);
                       // mExecutor.execute(r);
                    }
                }
            } catch (Exception e) {
                Timber.e(e);
            }
        }
    }
}
