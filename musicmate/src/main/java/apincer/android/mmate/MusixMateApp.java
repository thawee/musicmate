package apincer.android.mmate;

import static apincer.android.mmate.playback.ExternalPlayerListener.setupNotificationListener;

import android.annotation.SuppressLint;
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
import androidx.work.WorkManager;

import com.antonkarpenko.ffmpegkit.FFmpegKitConfig;
import com.antonkarpenko.ffmpegkit.Level;
import com.balsikandar.crashreporter.CrashReporter;
import com.google.android.material.color.DynamicColors;
import com.j256.ormlite.android.apptools.OpenHelperManager;

import apincer.android.mmate.dlna.MediaServerService;
import apincer.android.mmate.playback.PlaybackService;
import apincer.android.mmate.repository.OrmLiteHelper;
import apincer.android.mmate.repository.PlaylistRepository;
import apincer.android.mmate.ui.MainActivity;
import apincer.android.mmate.utils.LogHelper;
import apincer.android.mmate.worker.MusicMateExecutors;
import apincer.android.mmate.worker.ScanAudioFileWorker;
import sakout.mehdi.StateViews.StateViewsBuilder;

public class MusixMateApp extends Application {
    private static final String TAG = LogHelper.getTag(MusixMateApp.class);

    private static MusixMateApp INSTANCE;

    public static MusixMateApp getInstance() {
        return INSTANCE;
    }

    public static final String NOTIFICATION_CHANNEL_ID = "MusicMateNotifications";
    public static final String NOTIFICATION_GROUP_KEY = "MusicMate";

   // private MediaServerManager mediaServerManager;
    private MediaServerService mediaServerService;

    @Override
    public void onTerminate() {
        super.onTerminate();

        // Rest of your termination code
       /* if(mediaServerManager!= null) {
            mediaServerManager.stopServer();
            mediaServerManager.cleanup();
        }*/
        if(mediaServerService != null) {
            mediaServerService.stopServers();
            unbindService(serviceConnection);
        }

        WorkManager.getInstance(getApplicationContext()).cancelAllWork();
        MusicMateExecutors.getInstance().shutdown();
    }

    @Override public void onCreate() {
        super.onCreate();
        INSTANCE = this;
        // Apply dynamic color
        DynamicColors.applyToActivitiesIfAvailable(this);
        AppCompatDelegate.setCompatVectorFromResourcesEnabled(true);
        LogHelper.initial();
        LogHelper.setSLF4JOn();
        CrashReporter.initialize(getApplicationContext());

        setupNotificationListener(this);

        // initialize thread executors
        MusicMateExecutors.getInstance();

        // turn off ffmpeg-kit log
        FFmpegKitConfig.setLogLevel(Level.AV_LOG_ERROR);

        createNotificationChannel();
        //mediaServerManager = new MediaServerManager(getApplicationContext());

        // Copy web assets like index.html to a place the web server can access them.
        //copyWebAssets();

        startMediaServer();

        // Call the static method to start the service from a valid context
        PlaybackService.startPlaybackService(this);

        PlaylistRepository.initPlaylist(this);

        StateViewsBuilder
                .init(this)
                .setIconColor(Color.parseColor("#D2D5DA"))
                .addState("error",
                        "No Connection",
                        "Error retrieving information from server.",
                        AppCompatResources.getDrawable(this, sakout.mehdi.StateViews.R.drawable.ic_server_error),
                        "Retry"
                )

                .addState("search",
                        "No Results Found",
                        "Could not find any results matching search criteria",
                        AppCompatResources.getDrawable(this, R.drawable.ic_search_black_24dp), null)

                .setButtonBackgroundColor(Color.parseColor("#317DED"))
                .setButtonTextColor(Color.parseColor("#FFFFFF"))
                .setIconSize(getResources().getDimensionPixelSize(R.dimen.state_views_icon_size));

    }

    private void startMediaServer() {
        if(Settings.isAutoStartMediaServer(this)) {
            Intent intent = new Intent(this, MediaServerService.class);
            bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE);
        }
    }


    // Add this to your MusixMateApp class
    public void startMusicScan() {
        // Clean up any pending work requests
        WorkManager.getInstance(getApplicationContext()).pruneWork();

        if(Settings.checkDirectoriesSet(getApplicationContext())) {
            // On normal startup, do a quick incremental scan
            Log.i(TAG, "Normal startup, performing incremental music scan");
            ScanAudioFileWorker.startScan(getApplicationContext());
        } else {
            Log.w(TAG, "Music scan skipped - no directories configured");
        }
    }

    private final ServiceConnection serviceConnection = new ServiceConnection() {
        @SuppressLint("CheckResult")
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            MediaServerService.MediaServerServiceBinder binder = (MediaServerService.MediaServerServiceBinder) service;
            mediaServerService = binder.getService();
            if(!mediaServerService.isInitialized()) {
                mediaServerService.startServers();
            }
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {

        }
    };

    /*
    Provides the SQLite Helper Object among the application
    */
    public OrmLiteHelper getOrmLite() {
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