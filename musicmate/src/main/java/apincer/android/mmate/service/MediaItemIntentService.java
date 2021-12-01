package apincer.android.mmate.service;

import android.app.Activity;
import android.app.IntentService;
import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Message;

import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import org.jaudiotagger.tag.images.Artwork;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import apincer.android.mmate.Constants;
import apincer.android.mmate.R;
import apincer.android.mmate.fs.MusicMateArtwork;
import apincer.android.mmate.objectbox.AudioTag;
import apincer.android.mmate.repository.AudioFileRepository;
import apincer.android.mmate.utils.StringUtils;
import timber.log.Timber;

@Deprecated
public class MediaItemIntentService extends IntentService {
    private static Map<String, List<AudioTag>> itemList = new HashMap<>();
    HandlerThread thread = new HandlerThread("AudioTag Handler Thread");
    private volatile MediaItemDeleteHandler deleteHandler;
    private volatile MediaItemSaveHandler saveHandler;
    private volatile MediaItemManageHandler manageHandler;
    private Artwork artwork = null;
    private volatile int pendingTotal = 0;
    private volatile int successCount = 0;
    private volatile int errorCount = 0;

    private final class MediaItemDeleteHandler extends Handler {
        public MediaItemDeleteHandler(Looper looper) {
            super(looper);
        }

        @Override
        public void handleMessage(Message msg) {
            AudioTag item = (AudioTag) msg.obj;
            boolean status = AudioFileRepository.getInstance(getApplication()).deleteMediaItem(item);
			String txt = status?getApplicationContext().getString(R.string.alert_delete_success, item.getTitle()):getApplicationContext().getString(R.string.alert_delete_fail, item.getTitle());
			String statusStr = status?"success":"fail";
			if(status) {
                successCount++;
            }else {
			    errorCount++;
            }
			sendBroadcast(Constants.COMMAND_DELETE, item, statusStr, txt);
        }
    }

    private final class MediaItemSaveHandler extends Handler {
        public MediaItemSaveHandler(Looper looper) {
            super(looper);
        }

        @Override
        public void handleMessage(Message msg) {
            AudioTag item = (AudioTag) msg.obj;
            boolean status = AudioFileRepository.getInstance(getApplication()).saveMediaItem(item, artwork);
			String txt = status?getApplicationContext().getString(R.string.alert_write_tag_success, item.getTitle()):getApplicationContext().getString(R.string.alert_write_tag_fail, item.getTitle());
			String statusStr = status?"success":"fail";
            if(status) {
                successCount++;
            }else {
                errorCount++;
            }
			sendBroadcast(Constants.COMMAND_SAVE, item, statusStr, txt);
        }
    }

    private final class MediaItemManageHandler extends Handler {
        public MediaItemManageHandler(Looper looper) {
            super(looper);
        }

        @Override
        public void handleMessage(Message msg) {
            AudioTag item = (AudioTag) msg.obj;
            boolean status = AudioFileRepository.getInstance(getApplication()).manageMediaItem(item);
			String txt = status?getApplicationContext().getString(R.string.alert_organize_success, item.getTitle()):getApplicationContext().getString(R.string.alert_organize_fail, item.getTitle());
			String statusStr = status?Constants.STATUS_SUCCESS:Constants.STATUS_FAIL;
            if(status) {
                successCount++;
            }else {
                errorCount++;
            }
			sendBroadcast(Constants.COMMAND_MOVE, item, statusStr, txt);
        }
    }

    /**
     * Creates an IntentService.  Invoked by your subclass's constructor.
     *
     * @param name Used to name the worker thread, important only for debugging.
     */
    public MediaItemIntentService(String name) {
        super(name);
    }

    public MediaItemIntentService() {
        super("MediaItemIntentService");
    }

    @Override
    public void onCreate() {
        super.onCreate();
        thread.start();
        deleteHandler = new MediaItemDeleteHandler(thread.getLooper());
        manageHandler = new MediaItemManageHandler(thread.getLooper());
        saveHandler = new MediaItemSaveHandler(thread.getLooper());
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        thread.quitSafely();
        thread = null;
    }

    @Override
    protected void onHandleIntent(Intent intent) {
        String command = intent.getStringExtra(Constants.KEY_COMMAND);
        List<AudioTag> items = itemList.get(command);
        pendingTotal = pendingTotal+items.size();
        if (Constants.COMMAND_DELETE.equals(command) && items !=null) {
			String txt = getApplicationContext().getString(R.string.alert_delete_inprogress_many, items.size());
			if(items.size()==1) {
			    txt = getApplicationContext().getString(R.string.alert_delete_inprogress, items.get(0).getTitle());
            }
            sendBroadcast(Constants.COMMAND_DELETE,null, "start", txt);
            for(AudioTag item: items) {
                Message msg = deleteHandler.obtainMessage();
                msg.obj = item;
                deleteHandler.sendMessage(msg);
            }
        }else if (Constants.COMMAND_SAVE.equals(command) && items !=null) {
            artwork = null;
			String txt = getApplicationContext().getString(R.string.alert_write_tag_inprogress_many, items.size());
            if(items.size()==1) {
                txt = getApplicationContext().getString(R.string.alert_write_tag_inprogress, items.get(0).getTitle());
            }
            sendBroadcast(Constants.COMMAND_SAVE, null, "start", txt);
            String pendingArtworkPath = intent.getStringExtra(Constants.KEY_COVER_ART_PATH);
            if(!StringUtils.isEmpty(pendingArtworkPath)) {
                File artworkFile = new File(pendingArtworkPath);
                if(artworkFile.exists()) {
                    try {
                        artwork = MusicMateArtwork.createArtworkFromFile(artworkFile);
                    } catch (IOException e) {
                        Timber.e(e);
                    }
                }
            }
            int count = 0;
            for(AudioTag item: items) {
                Message msg = saveHandler.obtainMessage();
                msg.obj = item;
                saveHandler.sendMessage(msg);
            }
        }else if (Constants.COMMAND_MOVE.equals(command) && items !=null) { 
            String txt = getApplicationContext().getString(R.string.alert_organize_inprogress_many, items.size());
            if(items.size()==1) {
                txt = getApplicationContext().getString(R.string.alert_organize_inprogress, items.get(0).getTitle());
            }
            sendBroadcast(Constants.COMMAND_MOVE, null, Constants.STATUS_START, txt);
           
            for(AudioTag item: items) {
                Message msg = manageHandler.obtainMessage();
                msg.obj = item;
                manageHandler.sendMessage(msg);
            }			
        }
  }

  public static void startService(Context context, String command, List<AudioTag> items) {
      Intent msgIntent = new Intent(context, MediaItemIntentService.class);
      msgIntent.putExtra(Constants.KEY_COMMAND, command);
      if(items!=null) {
          itemList.put(command, items);
      }
      context.startService(msgIntent);
  }

  public static void startService(Context context, String command, List<AudioTag> items, String artworkPath) {
      Intent msgIntent = new Intent(context, MediaItemIntentService.class);
      msgIntent.putExtra(Constants.KEY_COMMAND, command);
      if(artworkPath != null) {
          msgIntent.putExtra(Constants.KEY_COVER_ART_PATH, artworkPath);
      }
      if(items!=null) {
          itemList.put(command, items);
      }
      context.startService(msgIntent);
  }
  
      protected void sendBroadcast(final String command, final AudioTag item, final String status, final String message){
        // Fire the broadcast with intent packaged
        // Construct our Intent specifying the Service
        Intent intent = new Intent(AudioFileRepository.ACTION);
        // Add extras to the bundle
        intent.putExtra(Constants.KEY_RESULT_CODE, Activity.RESULT_OK);
        intent.putExtra(Constants.KEY_COMMAND, command);
        intent.putExtra(Constants.KEY_STATUS, status);
        intent.putExtra(Constants.KEY_MESSAGE, message);
        intent.putExtra(Constants.KEY_SUCCESS_COUNT, successCount);
        intent.putExtra(Constants.KEY_ERROR_COUNT, errorCount);
        intent.putExtra(Constants.KEY_PENDING_TOTAL, pendingTotal);
        if(item !=null) {
              intent.putExtra(Constants.KEY_MEDIA_TAG, item);
        }
        if((successCount+errorCount)==pendingTotal) {
            pendingTotal = 0;
            successCount = 0;
            errorCount = 0;
        }

        LocalBroadcastManager.getInstance(getApplication()).sendBroadcast(intent);
    }
}
