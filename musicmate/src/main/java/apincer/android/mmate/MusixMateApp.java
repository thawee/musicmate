package apincer.android.mmate;

import android.app.Application;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Color;
import android.os.BatteryManager;
import android.os.PowerManager;
import android.util.Log;

import androidx.appcompat.app.AppCompatDelegate;
import androidx.appcompat.content.res.AppCompatResources;
import androidx.core.app.NotificationCompat;
import androidx.work.Constraints;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkManager;

import com.balsikandar.crashreporter.CrashReporter;
import com.google.android.material.color.DynamicColors;
import com.j256.ormlite.android.apptools.OpenHelperManager;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;

import apincer.android.mmate.broadcast.AudioTagPlayingEvent;
import apincer.android.mmate.broadcast.BroadcastHelper;
import apincer.android.mmate.broadcast.MusicMateNotificationListener;
import apincer.android.mmate.broadcast.MusicPlayerInfo;
import apincer.android.mmate.nas.NASServerService;
import apincer.android.mmate.repository.MediaServer;
import apincer.android.mmate.repository.MusicMateOrmLite;
import apincer.android.mmate.repository.MusicTag;
import apincer.android.mmate.ui.MainActivity;
import apincer.android.mmate.work.ScanAudioFileWorker;
import sakout.mehdi.StateViews.StateViewsBuilder;

public class MusixMateApp extends Application {
    private static final String TAG = MusixMateApp.class.getName();

    private static MusixMateApp INSTANCE;
    //private static HTTPStreamingServer server;

    public static MusixMateApp getInstance() {
        return INSTANCE;
    }

    public static final String NOTIFICATION_CHANNEL_ID = "MusicMateNotifications";
    public static final String NOTIFICATION_GROUP_KEY = "MusicMate";
    private final HashMap<String, PowerManager.WakeLock> wakeLocks = new HashMap<>();
    private Executor contentLoadThreadPool;

    private static final BroadcastHelper broadcastHelper = new BroadcastHelper((context, song) -> {
        try {
          /*  BroadcastData data = new BroadcastData()
                    .setAction(BroadcastData.Action.PLAYING)
                    .setStatus(BroadcastData.Status.COMPLETED)
                    .setTagInfo(song)
                    .setMessage("");
            Intent intent = data.getIntent();
            LocalBroadcastManager.getInstance(context).sendBroadcast(intent); */
            AudioTagPlayingEvent.publishPlayingSong(song);
        } catch (Exception ex) {
            Log.e(TAG, "BroadcastHelper", ex);
        }
    });
    private static final long SCAN_SCHEDULE_TIME = 5;
   // private static final long LOUDNESS_SCAN_SCHEDULE_TIME = 15;

    private static final Map<String, List<MusicTag>> pendingQueue = new HashMap<>();
   // private static MusicMateDatabase database;
   // private MusicMateOrmLite ormLite = null;

    public static MusicTag getPlayingSong() {
        return BroadcastHelper.getPlayingSong();
    }

    /*
    public static void statHttpServer(Activity activity) {
        if(server==null) {
            server = new HTTPStreamingServer();
        }
        server.startServer(activity);
    } */

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

    /*
    public static void stopHttpServer(Activity activity) {
        if(server!=null) {
            server.stopServer(activity);
        }
    } */

    @Override
    public void onTerminate() {
        super.onTerminate();

      //  upnpClient.shutdown();
      //  stopService(new Intent(this, UpnpServerService.class));
      //  stopService(new Intent(this, UpnpRegistryService.class));
        stopService(new Intent(this, MusicMateNotificationListener.class));
       /* if(server!=null) {
            server.stopServer(null);
        }*/
        broadcastHelper.onTerminate(this);
        WorkManager.getInstance(getApplicationContext()).cancelAllWork();
    }

    @Override public void onCreate() {
        super.onCreate();
        INSTANCE = this;
        // Apply dynamic color
        DynamicColors.applyToActivitiesIfAvailable(this);
        AppCompatDelegate.setCompatVectorFromResourcesEnabled(true);

        CrashReporter.initialize(this);

        broadcastHelper.onCreate(this);

//        upnpClient = new UpnpClient(this);
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

       // workRequest = new OneTimeWorkRequest.Builder(ScanLoudnessWorker.class)
       //         .setInitialDelay(SCAN_SCHEDULE_TIME, TimeUnit.MINUTES)
       //         .setConstraints(constraints)
       //         .build();
       // WorkManager.getInstance(getApplicationContext()).enqueue(workRequest);

      //  MusicMateServer.startServer();

       // server = new HTTPStreamingServer();
       // server.startServer(getApplicationContext());

        // init mediaServer if nothing, to be deleted after implement maintain media server screen
        // FIXME - do UI and delete these code
        if(getOrmLite().getMediaServers().isEmpty()) {
            List list = new ArrayList();
            MediaServer server = new MediaServer();
            server.setIp("10.100.1.198");
            server.setPort(22);
            server.setName("tc@pcp.local");
            server.setUsername("tc");
            server.setPassword("piCore");
            server.setPath("/mnt/mmcblk0p2/media");
            list.add(server);
            getOrmLite().saveMediaServers(list);
        }
    }

    /*
Provides the SQLite Helper Object among the application
 */
    public MusicMateOrmLite getOrmLite() {
       /* if (ormLite == null) {
            ormLite = OpenHelperManager.getHelper(this, MusicMateOrmLite.class);
        }
        return ormLite; */
        return OpenHelperManager.getHelper(this, MusicMateOrmLite.class);
    }

    /*
    public boolean isHttpServerRunning() {
        if(server == null) return false;
        return server.isRunning();
    }*/

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
                .setContentText("MusicMate DMS")
                .setContentIntent(pendingIntent);
        notificationManager.notify(NotificationId.MAIN.getId(), mBuilder.build());

    }

    public void cancelGroupNotification() {
        NotificationManager mNotificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (mNotificationManager.getActiveNotifications().length == 1) {
            mNotificationManager.cancel(NotificationId.MAIN.getId());
        }
    }

    public Executor getContentLoadExecutor() {
        return contentLoadThreadPool;
    }

    public boolean isUnplugged() {
        Intent intent = registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
        int plugged = intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1);
        boolean unplugged = plugged == BatteryManager.BATTERY_PLUGGED_WIRELESS;
        return !(plugged == BatteryManager.BATTERY_PLUGGED_AC ||
                plugged == BatteryManager.BATTERY_PLUGGED_USB ||
                unplugged);
    }
    public void acquireWakeLock(long timeout, String tag) {

        if (!wakeLocks.containsKey(tag)) {
            PowerManager powerManager = (PowerManager) getSystemService(Context.POWER_SERVICE);
            wakeLocks.put(tag, powerManager.newWakeLock(PowerManager.FULL_WAKE_LOCK, tag));
        }
        PowerManager.WakeLock wakeLock = wakeLocks.get(tag);
        if (wakeLock != null) {
            if (wakeLock.isHeld()) {
                releaseWakeLock(tag);
            }
            while (!wakeLock.isHeld()) {
                wakeLock.acquire(timeout);
            }
            Log.d(getClass().getName(), "WakeLock aquired Tag:" + tag + " timeout: " + timeout);
        }


    }

    public void releaseWakeLock(String tag) {
        PowerManager.WakeLock wakeLock = wakeLocks.get(tag);
        if (wakeLock != null && wakeLock.isHeld()) {
            try {
                wakeLock.release();
                Log.d(getClass().getName(), "WakeLock released: " + tag);
            } catch (Throwable th) {
                // ignoring this exception, probably wakeLock was already released
                Log.d(getClass().getName(), "Ignoring exception on WakeLock (" + tag + ") release maybe no wakelock?");
            }
        }

    }

    public boolean isNASRunning() {
        //NASServerService service = getSystemService(NASServerService.class);
        NASServerService service = NASServerService.getInstance(); //getSystemService(NASServerService.class);
        if(service != null) {
            return service.isStarted();
        }
        return false;
    }
}