package apincer.android.mmate.service;

import android.app.Activity;
import android.app.IntentService;
import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Message;

import androidx.lifecycle.Lifecycle;
import androidx.lifecycle.ProcessLifecycleOwner;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import com.anggrayudi.storage.file.DocumentFileCompat;

import org.jaudiotagger.audio.generic.Utils;

import java.io.File;
import java.util.List;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import apincer.android.mmate.Constants;
import apincer.android.mmate.R;
import apincer.android.mmate.repository.AudioFileRepository;
import apincer.android.mmate.repository.AudioTagRepository;
import timber.log.Timber;

@Deprecated
public class MediaItemScanService extends IntentService {
    private volatile Looper serviceLooper;
    private volatile MediaItemScanHandler scanHandler;
    private volatile MediaItemCleanHandler cleanHandler;
    private ThreadPoolExecutor mExecutor;
    /**
     * Gets the number of available cores
     * (not always the same as the maximum number of cores)
     **/
    private static final int NUMBER_OF_CORES = Runtime.getRuntime().availableProcessors();
    // Sets the amount of time an idle thread waits before terminating
    private static final int KEEP_ALIVE_TIME = 1000;
    // Sets the Time Unit to Milliseconds
    private static final TimeUnit KEEP_ALIVE_TIME_UNIT = TimeUnit.MILLISECONDS;

    private final class MediaItemScanHandler extends Handler {
        public MediaItemScanHandler(Looper looper) {
            super(looper);
        }

        @Override
        public void handleMessage(Message msg) {
           // MediaItemRepository.getInstance(getApplication()).scanFromMediaStore();
           // if(msg.arg1 == 1) {
                startScan();
          //      AudioFileRepository.getInstance(getApplication()).scanFromMediaSources(false);
          //  }else {
          //      AudioFileRepository.getInstance(getApplication()).scanFromMediaSources(true);
          //  }
        }
    }

    private void startScan() {
        List<String> storageIds = DocumentFileCompat.getStorageIds(getApplicationContext());
        for (String sid : storageIds) {
            File file = new File(DocumentFileCompat.buildAbsolutePath(getApplicationContext(), sid, "Music"));
            if(file.exists()) {
                ScanRunnable r = new ScanRunnable(file);
                mExecutor.execute(r);
            }
            file = new File(DocumentFileCompat.buildAbsolutePath(getApplicationContext(), sid, "Download"));
            if(file.exists()) {
                ScanRunnable r = new ScanRunnable(file);
                mExecutor.execute(r);
            }
        }
    }

    private final class MediaItemCleanHandler extends Handler {
        public MediaItemCleanHandler(Looper looper) {
            super(looper);
        }

        @Override
        public void handleMessage(Message msg) {
            AudioTagRepository.cleanMusicMate();
        }
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
                            AudioFileRepository.getInstance(getApplication()).scanFileAndSaveTag(f);
                        } else if(f.isDirectory()) {
                            ScanRunnable r = new ScanRunnable(f);
                            mExecutor.execute(r);
                        }
                    }
            } catch (Exception e) {
                Timber.e(e);
            }
        }
    }

    /**
     * Creates an IntentService.  Invoked by your subclass's constructor.
     *
     * @param name Used to name the worker thread, important only for debugging.
     */
    public MediaItemScanService(String name) {
        super(name);
    }


    public MediaItemScanService() {
        super("MediaItemScanService");
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
        }else if(ext.equalsIgnoreCase("dsf")) {
        return true;
      /*  }else if(ext.equalsIgnoreCase("dff")) {
            return true;
        }else if(ext.equalsIgnoreCase("iso")) {
            return true; */
        }
        return false;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        HandlerThread thread = new HandlerThread("MediaItemScanService");
        thread.start();

        serviceLooper = thread.getLooper();
        scanHandler = new MediaItemScanHandler(serviceLooper);
        cleanHandler = new MediaItemCleanHandler(serviceLooper);
        mExecutor = new ThreadPoolExecutor(
                NUMBER_OF_CORES + 5,   // Initial pool size
                NUMBER_OF_CORES + 8,   // Max pool size
                KEEP_ALIVE_TIME,       // Time idle thread waits before terminating
                KEEP_ALIVE_TIME_UNIT,  // Sets the Time Unit for KEEP_ALIVE_TIME
                new LinkedBlockingDeque<Runnable>());  // Work Queue
    }

    @Override
    protected void onHandleIntent(Intent intent) {
        String command = intent.getStringExtra(Constants.KEY_COMMAND);
        if (Constants.COMMAND_SCAN.equals(command) || Constants.COMMAND_SCAN_FULL.equals(command)) {
			
			String txt = getApplicationContext().getString(R.string.alert_scan_title);
            sendBroadcast(Constants.STATUS_START, txt);
			
            // scan new items
			Message msg = scanHandler.obtainMessage();
            msg.obj = intent;
            if(Constants.COMMAND_SCAN.equals(command)) {
                msg.arg1 = 1; // speed
            }else {
                msg.arg1 = 0; //full, delete and re-scan
            }
            scanHandler.sendMessage(msg);

            // only clean hanging item on normal scan
            if(Constants.COMMAND_SCAN.equals(command)) {
                // clean not existed items
                msg = cleanHandler.obtainMessage();
                msg.obj = intent;
                cleanHandler.sendMessage(msg);
            }
			
			txt = getApplicationContext().getString(R.string.alert_scan_success);
            sendBroadcast(Constants.STATUS_SUCCESS, txt);
        }else if(Constants.COMMAND_CLEAN_DB.equals(command)) {
            Message msg = cleanHandler.obtainMessage();
            msg.obj = intent;
            cleanHandler.sendMessage(msg);
        }
  }

  public static void startService(Context context, String command) {
      //Check if app is in foreground
      boolean atLeast = ProcessLifecycleOwner.get().getLifecycle().getCurrentState().isAtLeast(Lifecycle.State.STARTED);
      if(!atLeast) {
          Intent msgIntent = new Intent(context, MediaItemScanService.class);
          msgIntent.putExtra(Constants.KEY_COMMAND, command);
          context.startService(msgIntent);
      }
  }

      protected void sendBroadcast(final String status, final String message){
        // Fire the broadcast with intent packaged
        // Construct our Intent specifying the Service
        Intent intent = new Intent(AudioFileRepository.ACTION);
        // Add extras to the bundle
        intent.putExtra(Constants.KEY_RESULT_CODE, Activity.RESULT_OK);
        intent.putExtra(Constants.KEY_COMMAND, Constants.COMMAND_SCAN);
        intent.putExtra(Constants.KEY_STATUS, status);
        intent.putExtra(Constants.KEY_MESSAGE, message); 
        LocalBroadcastManager.getInstance(getApplication()).sendBroadcast(intent);
    }
}
