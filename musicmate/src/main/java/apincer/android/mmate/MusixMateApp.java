package apincer.android.mmate;

import android.app.Application;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.graphics.Color;
import android.os.IBinder;
import android.util.Log;

import androidx.appcompat.app.AppCompatDelegate;
import androidx.appcompat.content.res.AppCompatResources;
import androidx.core.app.NotificationCompat;
import androidx.work.Constraints;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkManager;

import com.arthenica.ffmpegkit.FFmpegKitConfig;
import com.balsikandar.crashreporter.CrashReporter;
import com.google.android.material.color.DynamicColors;
import com.j256.ormlite.android.apptools.OpenHelperManager;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import apincer.android.mmate.broadcast.AudioTagPlayingEvent;
import apincer.android.mmate.broadcast.BroadcastHelper;
import apincer.android.mmate.broadcast.MusicPlayerInfo;
import apincer.android.mmate.repository.OrmLiteHelper;
import apincer.android.mmate.repository.MusicTag;
import apincer.android.mmate.ui.MainActivity;
import apincer.android.mmate.work.MusicMateExecutors;
import apincer.android.mmate.work.ScanAudioFileWorker;
import sakout.mehdi.StateViews.StateViewsBuilder;

public class MusixMateApp extends Application {
    private static final String TAG = MusixMateApp.class.getName();

    private static MusixMateApp INSTANCE;

    public static MusixMateApp getInstance() {
        return INSTANCE;
    }

    public static final String NOTIFICATION_CHANNEL_ID = "MusicMateNotifications";
    public static final String NOTIFICATION_GROUP_KEY = "MusicMate";

    private static final BroadcastHelper broadcastHelper = new BroadcastHelper((context, song) -> {
        try {
            AudioTagPlayingEvent.publishPlayingSong(song);
        } catch (Exception ex) {
            Log.e(TAG, "BroadcastHelper", ex);
        }
    });
    private static final long SCAN_SCHEDULE_TIME = 5;
   // private static final long LOUDNESS_SCAN_SCHEDULE_TIME = 15;

    private static final Map<String, List<MusicTag>> pendingQueue = new HashMap<>();

    public static MusicTag getPlayingSong() {
        return BroadcastHelper.getPlayingSong();
    }

    public static List<MusicTag> getPendingItems(String name) {
        List<MusicTag> list = new ArrayList<>();
        synchronized (pendingQueue) {
            if (pendingQueue.containsKey(name)) {
                list.addAll(pendingQueue.get(name));
                pendingQueue.remove(name);
            }
        }
        return list;
    }

    public static void putPendingItems(String name, List<MusicTag> tags) {
        synchronized (pendingQueue) {
            if (pendingQueue.containsKey(name)) {
                List<MusicTag> list = pendingQueue.get(name);
                assert list != null;
                list.addAll(tags);
                pendingQueue.put(name, tags);
            } else {
                pendingQueue.put(name, tags);
            }
        }
    }

    public static MusicPlayerInfo getPlayerInfo() {
        return broadcastHelper.getPlayerInfo();
    }

    public static void setPlaying(MusicPlayerInfo playerInfo, MusicTag listening) {
       broadcastHelper.setPlayerInfo(playerInfo);
       broadcastHelper.setPlayingSong(listening);
    }

    public static void playNextSong(Context applicationContext) {
        BroadcastHelper.playNextSong(applicationContext);
    }

    @Override
    public void onTerminate() {
        super.onTerminate();

        broadcastHelper.onTerminate(this);
        WorkManager.getInstance(getApplicationContext()).cancelAllWork();
        MusicMateExecutors.getInstance().shutdown();
    }

    @Override public void onCreate() {
        super.onCreate();
        INSTANCE = this;
        // Apply dynamic color
        DynamicColors.applyToActivitiesIfAvailable(this);
        AppCompatDelegate.setCompatVectorFromResourcesEnabled(true);

        CrashReporter.initialize(this);

        broadcastHelper.onCreate(this);

        // initialize thread executors
        MusicMateExecutors.getInstance();

        // turn off ffmpeg-kit log
        FFmpegKitConfig.setLogLevel(com.arthenica.ffmpegkit.Level.AV_LOG_ERROR);

        createNotificationChannel();

        //initialize ObjectBox is when your app starts
        //ObjectBox.init(this);

        StateViewsBuilder
                .init(this)
                .setIconColor(Color.parseColor("#D2D5DA"))
                .addState("error",
                        "No Connection",
                        "Error retrieving information from server.",
                        AppCompatResources.getDrawable(this, R.drawable.ic_server_error),
                        "Retry"
                )

                .addState("search",
                        "No Results Found",
                        "Could not find any results matching search criteria",
                        AppCompatResources.getDrawable(this, R.drawable.ic_search_black_24dp), null)

                .setButtonBackgroundColor(Color.parseColor("#317DED"))
                .setButtonTextColor(Color.parseColor("#FFFFFF"))
                .setIconSize(getResources().getDimensionPixelSize(R.dimen.state_views_icon_size));

        /*
        // to detect not expected thread
        StrictMode.setThreadPolicy(new StrictMode.ThreadPolicy.Builder()
                .detectAll()
                .penaltyLog()
                .build());

        StrictMode.setVmPolicy(new StrictMode.VmPolicy.Builder()
                .detectAll()
                .penaltyLog()
                .build());
         */

        // TURN OFF log for JAudioTagger
            //jAudioTaggerLogger1.setLevel(Level.SEVERE);
           // jAudioTaggerLogger2.setLevel(Level.SEVERE);

        // Do it on main process
       // BlockCanary.install(this, new AppBlockCanaryContext()).start();
/*
        BlockCanary.install(this, new BlockCanaryContext() {

            public String provideQualifier() {
                return "unknown";
            }

            public String provideUid() {
                return "uid";
            }

            public String provideNetworkType() {
                return "unknown";
            }

            public int provideMonitorDuration() {
                return -1;
            }

            public int provideBlockThreshold() {
                return 1000;
            }

            public int provideDumpInterval() {
                return provideBlockThreshold();
            }

            public String providePath() {
                return "/blockcanary/";
            }

            public boolean displayNotification() {
                return true;
            }

            public boolean zip(File[] src, File dest) {
                return false;
            }

            public void upload(File zippedFile) {
                throw new UnsupportedOperationException();
            }


            public List<String> concernPackages() {
                return null;
            }

            public boolean filterNonConcernStack() {
                return false;
            }

            public List<String> provideWhiteList() {
                LinkedList<String> whiteList = new LinkedList<>();
                whiteList.add("org.chromium");
                return whiteList;
            }

            public boolean deleteFilesInWhiteList() {
                return true;
            }

            public void onBlock(Context context, BlockInfo blockInfo) {

            }
        }).start(); */

        // scan music on startup
        // Workmanager intitialize on MusicFileProvider
        // clear existing scanning worker
        WorkManager.getInstance(getApplicationContext()).cancelAllWorkByTag("apincer.android.mmate.work.ScanAudioFileWorker");
       // WorkManager.getInstance(getApplicationContext()).cancelAllWorkByTag("apincer.android.mmate.work.ScanLoudnessWorker");
        WorkManager.getInstance(getApplicationContext()).pruneWork();

        Constraints constraints = new Constraints.Builder()
                .setRequiresBatteryNotLow(true)
                .setRequiresStorageNotLow(true)
                .build();

        OneTimeWorkRequest workRequest = new OneTimeWorkRequest.Builder(ScanAudioFileWorker.class)
                .setInitialDelay(SCAN_SCHEDULE_TIME, TimeUnit.SECONDS)
                .setConstraints(constraints)
                .build();
        WorkManager.getInstance(getApplicationContext()).enqueue(workRequest);
    }

    /*
Provides the SQLite Helper Object among the application
 */
    public OrmLiteHelper getOrmLite() {
       /* if (ormLite == null) {
            ormLite = OpenHelperManager.getHelper(this, MusicMateOrmLite.class);
        }
        return ormLite; */
        return OpenHelperManager.getHelper(this, OrmLiteHelper.class);
    }

    public void createNotificationChannel() {
        CharSequence name = getString(R.string.channel_name);
        String description = getString(R.string.channel_description);
        int importance = NotificationManager.IMPORTANCE_DEFAULT;
        NotificationChannel channel = new NotificationChannel(NOTIFICATION_CHANNEL_ID, name, importance);
        channel.setDescription(description);

        // Register the channel with the system; you can't change the importance
        // or other notification behaviors after this
        NotificationManager notificationManager = getSystemService(NotificationManager.class);
        notificationManager.createNotificationChannel(channel);
    }

    public void createGroupNotification() {
        NotificationManager notificationManager = getSystemService(NotificationManager.class);
        Intent notificationIntent = new Intent(this, MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(this,
                0, notificationIntent, PendingIntent.FLAG_IMMUTABLE);
        NotificationCompat.Builder mBuilder = new NotificationCompat.Builder(
                getApplicationContext(), NOTIFICATION_CHANNEL_ID)
                .setSilent(true)
                .setGroup(NOTIFICATION_GROUP_KEY)
                .setGroupSummary(true)
                .setSmallIcon(R.drawable.ic_notification_default)
                .setContentTitle("MusicMate")
                .setContentText("")
                .setContentIntent(pendingIntent);
        notificationManager.notify(NotificationId.MAIN.getId(), mBuilder.build());
    }

    public void cancelGroupNotification() {
        NotificationManager mNotificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (mNotificationManager.getActiveNotifications().length == 1) {
            mNotificationManager.cancel(NotificationId.MAIN.getId());
        }
    }
}