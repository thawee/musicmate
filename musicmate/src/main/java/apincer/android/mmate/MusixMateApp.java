package apincer.android.mmate;

import static apincer.android.mmate.Constants.COVER_ARTS;
import static apincer.android.mmate.player.NotificationListener.reconnectNotificationListener;

import android.app.Application;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkRequest;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.appcompat.content.res.AppCompatResources;
import androidx.core.app.NotificationCompat;
import androidx.work.WorkManager;

import com.arthenica.ffmpegkit.FFmpegKitConfig;
import com.balsikandar.crashreporter.CrashReporter;
import com.google.android.material.color.DynamicColors;
import com.j256.ormlite.android.apptools.OpenHelperManager;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import apincer.android.mmate.notification.NotificationId;
import apincer.android.mmate.player.PlayerControl;
import apincer.android.mmate.dlna.MediaServerService;
import apincer.android.mmate.repository.OrmLiteHelper;
import apincer.android.mmate.repository.MusicTag;
import apincer.android.mmate.repository.SearchCriteria;
import apincer.android.mmate.ui.MainActivity;
import apincer.android.mmate.utils.LogHelper;
import apincer.android.mmate.worker.MusicMateExecutors;
import apincer.android.mmate.worker.ScanAudioFileWorker;
import apincer.android.utils.FileUtils;
import sakout.mehdi.StateViews.StateViewsBuilder;

public class MusixMateApp extends Application {
    private static final String TAG = LogHelper.getTag(MusixMateApp.class);

    private ConnectivityManager connectivityManager;
    private ConnectivityManager.NetworkCallback networkCallback;
    private boolean isMediaServerRunning = false;
    private boolean isFirstStart = true;

    private static MusixMateApp INSTANCE;
    private SearchCriteria criteria;

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

        // Clean up network callback
        if (connectivityManager != null && networkCallback != null) {
            try {
                connectivityManager.unregisterNetworkCallback(networkCallback);
            } catch (Exception e) {
                Log.e(TAG, "Error unregistering network callback", e);
            }
        }

        // Stop media server if running
       // if (isMediaServerRunning) {
          //  MediaServerService.stopMediaServer(this);
       // }

        // Rest of your termination code
        MediaServerService.stopMediaServer(this);
        WorkManager.getInstance(getApplicationContext()).cancelAllWork();
        MusicMateExecutors.getInstance().shutdown();
    }

    // Add this method to properly set up network monitoring
    private void setupNetworkMonitoring() {
        connectivityManager = (ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE);

        // Create a network request that specifically looks for WiFi with Internet
        NetworkRequest networkRequest = new NetworkRequest.Builder()
                .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .build();

        // Create the callback only once to prevent memory leaks
        networkCallback = new ConnectivityManager.NetworkCallback() {
            @Override
            public void onAvailable(@NonNull Network network) {
                Log.d(TAG, "WiFi network became available");

                // Check if the network has the capabilities we need
                NetworkCapabilities capabilities = connectivityManager.getNetworkCapabilities(network);
                if (capabilities == null ||
                        !capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)) {
                    Log.d(TAG, "Network lacks required capabilities");
                    return;
                }

                // Check media server settings before starting
                if (Settings.isEnableMediaServer(getApplicationContext())) {
                    // Use a slight delay to allow network to stabilize
                    MusicMateExecutors.schedule(() -> {
                        try {
                            MediaServerService.startMediaServer(MusixMateApp.this);
                            isMediaServerRunning = true;
                            Log.i(TAG, "Media server started after network connection");
                        } catch (Exception e) {
                            Log.e(TAG, "Failed to start media server", e);
                        }
                    }, 2);
                }
            }

            @Override
            public void onLost(@NonNull Network network) {
                Log.d(TAG, "WiFi network lost");

                // Let the service handle its own shutdown in response to network events
                // Just update our tracking state
                isMediaServerRunning = false;
            }
        };

        // Register the callback
        connectivityManager.registerNetworkCallback(networkRequest, networkCallback);

        // Initial check for existing connection
        NetworkCapabilities capabilities = connectivityManager.getNetworkCapabilities(
                connectivityManager.getActiveNetwork());

        if (capabilities != null &&
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) &&
                capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                Settings.isEnableMediaServer(getApplicationContext())) {
            // Network is already available - start service directly
            MediaServerService.startMediaServer(this);
            isMediaServerRunning = true;
        }
    }

    @Override public void onCreate() {
        super.onCreate();
        INSTANCE = this;
        // Apply dynamic color
        DynamicColors.applyToActivitiesIfAvailable(this);
        AppCompatDelegate.setCompatVectorFromResourcesEnabled(true);
        LogHelper.initial();
        LogHelper.setSLF4JOn();
        CrashReporter.initialize(this);

        reconnectNotificationListener(this);

        // initialize thread executors
        MusicMateExecutors.getInstance();

        // turn off ffmpeg-kit log
        FFmpegKitConfig.setLogLevel(com.arthenica.ffmpegkit.Level.AV_LOG_ERROR);

        createNotificationChannel();

        // Set up network monitoring
        setupNetworkMonitoring();

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

    // Add this to your MusixMateApp class
    public void startMusicScan() {
        // Clean up any pending work requests
        WorkManager.getInstance(getApplicationContext()).pruneWork();

        if(Settings.checkDirectoriesSet(getApplicationContext())) {
            if (isFirstStart) {
                Log.i(TAG, "Starting initial music scan");
                isFirstStart = false;
            } else {
                Log.i(TAG, "Starting music scan on app restart");
            }
            ScanAudioFileWorker.startScan(getApplicationContext());
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

    public void setSearchCriteria(SearchCriteria criteria) {
        this.criteria = criteria;
    }

    public SearchCriteria getCriteria() {
        return criteria;
    }

    public void clearCaches() {
        File dir = getApplicationContext().getExternalCacheDir();
       // FileUtils.deleteDirectory(new File(dir, "/tmp/"));
        FileUtils.deleteDirectory(new File(dir, "/Icons/"));
        FileUtils.deleteDirectory(new File(dir, COVER_ARTS));
    }

    // Add this method to handle media server setting changes
    public void onMediaServerSettingChanged(boolean enabled) {
        Log.d(TAG, "Media server setting changed: enabled=" + enabled);

        if (enabled) {
            // Only start if we have a WiFi connection
            Network activeNetwork = connectivityManager.getActiveNetwork();
            NetworkCapabilities capabilities = connectivityManager.getNetworkCapabilities(activeNetwork);

            if (capabilities != null &&
                    capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) &&
                    capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)) {
                MediaServerService.startMediaServer(this);
                isMediaServerRunning = true;
            } else {
                Log.i(TAG, "Media server enabled but waiting for WiFi connection");
            }
        } else if (isMediaServerRunning) {
            MediaServerService.stopMediaServer(this);
            isMediaServerRunning = false;
        }
    }
}