package apincer.android.mmate;

import static apincer.android.mmate.Constants.COVER_ARTS;
import static apincer.android.mmate.dlna.MediaServerService.startMediaServer;
import static apincer.android.mmate.player.NotificationListener.reconnectNotificationListener;

import android.app.Application;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
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

import org.jupnp.model.meta.RemoteDevice;

import java.io.File;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import apincer.android.mmate.notification.NotificationId;
import apincer.android.mmate.player.PlayerControl;
import apincer.android.mmate.dlna.MediaServerService;
import apincer.android.mmate.repository.OrmLiteHelper;
import apincer.android.mmate.repository.MusicTag;
import apincer.android.mmate.repository.PlaylistRepository;
import apincer.android.mmate.ui.MainActivity;
import apincer.android.mmate.utils.LogHelper;
import apincer.android.mmate.worker.MusicMateExecutors;
import apincer.android.mmate.worker.ScanAudioFileWorker;
import apincer.android.utils.FileUtils;
import sakout.mehdi.StateViews.StateViewsBuilder;

public class MusixMateApp extends Application {
    private static final String TAG = LogHelper.getTag(MusixMateApp.class);

    private static MusixMateApp INSTANCE;
    private final List<RemoteDevice> renderers = new ArrayList<>();

    public static MusixMateApp getInstance() {
        return INSTANCE;
    }

    public static final String NOTIFICATION_CHANNEL_ID = "MusicMateNotifications";
    public static final String NOTIFICATION_GROUP_KEY = "MusicMate";

    private static final PlayerControl playerControl = new PlayerControl();

    private static final Map<String, List<MusicTag>> pendingQueue = new HashMap<>();

    public static List<MusicTag> getPendingItems(String name) {
        List<MusicTag> list = new ArrayList<>();
        synchronized (pendingQueue) {
            if (pendingQueue.get(name) != null) {
                list.addAll(Objects.requireNonNull(pendingQueue.get(name)));
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
                // No need to put the list back into the map, as we modified the list that's already there
            } else {
                pendingQueue.put(name, new ArrayList<>(tags)); // Create a copy to avoid external modifications
            }
        }
    }

    public static PlayerControl getPlayerControl() {
        return playerControl;
    }

    @Override
    public void onTerminate() {
        super.onTerminate();

        // Rest of your termination code
        MediaServerService.stopMediaServer(this);
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

        reconnectNotificationListener(this);

        // initialize thread executors
        MusicMateExecutors.getInstance();

        // turn off ffmpeg-kit log
        FFmpegKitConfig.setLogLevel(Level.AV_LOG_ERROR);

        createNotificationChannel();

        // Set up network monitoring
       // setupNetworkMonitoring();
        startDMSServer(this);

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

    private void startDMSServer(MusixMateApp musixMateApp) {
        // Check media server settings before starting
        if (Settings.isAutoStartMediaServer(getApplicationContext())) {
            // Use a slight delay to allow network to stabilize
            MusicMateExecutors.schedule(() -> {
                try {
                    startMediaServer(MusixMateApp.this);
                    //isMediaServerRunning = true;
                    Log.i(TAG, "Media server started after network connection");
                } catch (Exception e) {
                    Log.e(TAG, "Failed to start media server", e);
                }
            }, 2);
        }
    }

    // Add this to your MusixMateApp class
    public void startMusicScan() {
        // Clean up any pending work requests
        WorkManager.getInstance(getApplicationContext()).pruneWork();

        if(Settings.checkDirectoriesSet(getApplicationContext())) {
            // Get preferences to check first run or app upgrade
           // SharedPreferences prefs = Settings.getPreferences(getApplicationContext());
           // boolean isFirstRun = prefs.getBoolean("is_first_run", true);
           // long previousVersion = prefs.getLong("app_version", 0);
           // long currentVersion = ApplicationUtils.getVersionCode(getApplicationContext());

            // Schedule regular incremental scans for ongoing maintenance
           // ScanAudioFileWorker.scheduleRegularScans(this);

           /* if (isFirstRun || previousVersion < currentVersion) {
                // Perform a full scan on first run or app upgrade
                Log.i(TAG, "First run or app upgrade, performing full music scan");
                ScanAudioFileWorker.startScan(getApplicationContext());

                // Update preferences
                prefs.edit()
                        .putBoolean("is_first_run", false)
                        .putLong("app_version", currentVersion)
                        .apply();
            } else { */
                // On normal startup, do a quick incremental scan
                Log.i(TAG, "Normal startup, performing incremental music scan");
                ScanAudioFileWorker.startScan(getApplicationContext());
          //  }
        } else {
            Log.w(TAG, "Music scan skipped - no directories configured");
        }
    }

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

    public void clearCaches() {
        File dir = getApplicationContext().getExternalCacheDir();
       // FileUtils.deleteDirectory(new File(dir, "/tmp/"));
        FileUtils.deleteDirectory(new File(dir, "/Icons/"));
        FileUtils.deleteDirectory(new File(dir, COVER_ARTS));
    }

    public RemoteDevice getRenderer(String ipAddress) {
        for(RemoteDevice dev: renderers) {
            String ip = getDeviceIpAddress(dev);
            if(ip != null && ipAddress.equals(ip)) {
                return dev;
            }
        }
        return null;
    }

    public void addRenderer(RemoteDevice device) {
        renderers.add(device);
    }

    public static String getDeviceIpAddress(RemoteDevice device) {
        URL descriptorURL = device.getIdentity().getDescriptorURL();
        return descriptorURL.getHost();
    }

}