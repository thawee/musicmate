package apincer.android.mmate.service;

import static apincer.music.core.NotificationId.MEDIA_SERVER;

import android.annotation.SuppressLint;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkRequest;
import android.net.wifi.WifiManager;
import android.os.Binder;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import java.io.IOException;

import javax.inject.Inject;

import apincer.android.mmate.R;
import apincer.music.core.Constants;
import apincer.music.core.repository.FileRepository;
import apincer.music.core.repository.TagRepository;
import apincer.music.core.server.spi.MediaServerHub;
import apincer.music.core.utils.ApplicationUtils;
import apincer.music.core.utils.NetworkUtils;
import dagger.hilt.android.AndroidEntryPoint;

@AndroidEntryPoint
public class MediaServerHubService extends Service {
    private static final String TAG = "MediaServerHubService";
    private static final String CHANNEL_ID = "media_server_hub_service_channel";

    @Inject
    MediaServerHub mediaServer;
    @Inject
    FileRepository fileRepos;
    @Inject
    TagRepository tagRepos;

    private WifiManager wifiManager;
    private PowerManager powerManager;
    private ConnectivityManager connectivityManager;
    private NotificationManager notificationManager;

    // Locks to keep the CPU and WiFi active for stable streaming
    private WifiManager.WifiLock wifiLock;
    private WifiManager.MulticastLock multicastLock;
    private PowerManager.WakeLock wakeLock;

    // Network monitoring for the 30-minute auto-stop failsafe
    private final Handler networkHandler = new Handler(Looper.getMainLooper());
    private Runnable stopServerRunnable;
    private ConnectivityManager.NetworkCallback networkCallback;

    // The Service is now the single source of truth for its status.
    private final MutableLiveData<MediaServerHub.ServerStatus> statusLiveData = new MutableLiveData<>(MediaServerHub.ServerStatus.STOPPED);

    private final IBinder binder = new MediaServerHubBinder();

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && intent.getAction() != null) {
            switch (intent.getAction()) {
                case MediaServerManager.ACTION_START_SERVER:
                    startServers();
                    break;
                case MediaServerManager.ACTION_STOP_SERVER:
                    stopServers();
                    break;
            }
        }
        return START_STICKY;
    }

    public void startServers() {
        if (mediaServer.isInitialized()) return;

        if (!NetworkUtils.isWifiConnected(this)) {
            statusLiveData.postValue(MediaServerHub.ServerStatus.ERROR);
            Log.d(TAG, TAG+" - Error, Required WiFi network");
            return;
        }

        // --- ACQUIRE RESOURCES ---
        // Wake lock keeps the CPU from sleeping. A timeout is used as a safeguard.
        wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "MusixMate:MediaServerWakeLock");
        wakeLock.acquire(10 * 60 * 1000L /*10 minutes*/);

        // WifiLock keeps the WiFi radio from turning off, crucial for streaming.
        wifiLock = wifiManager.createWifiLock(WifiManager.WIFI_MODE_FULL_LOW_LATENCY, "MusixMate:WifiLock");
        wifiLock.setReferenceCounted(false);
        wifiLock.acquire();

        // MulticastLock is required for device discovery (DLNA/UPnP).
        multicastLock = wifiManager.createMulticastLock("MusixMate:MulticastLock");
        multicastLock.setReferenceCounted(false);
        multicastLock.acquire();
        Log.d(TAG, "CPU, Wi-Fi, and Multicast locks acquired.");

        // --- START SERVICES ---
        //showNotification(null);
        mediaServer.startServers();
        startNetworkMonitoring();

        // Report that the server is now running.
        statusLiveData.postValue(MediaServerHub.ServerStatus.RUNNING);
    }

    public void stopServers() {
        if (!mediaServer.isInitialized()) return;

        // --- RELEASE RESOURCES ---
        // Always cancel any pending stop command first
        networkHandler.removeCallbacks(stopServerRunnable);
        stopNetworkMonitoring();
        mediaServer.stopServers();
      //  cancelNotification();

        // Report that the server has stopped.
        statusLiveData.postValue(MediaServerHub.ServerStatus.STOPPED);

        if (wakeLock != null && wakeLock.isHeld()) {
            wakeLock.release();
        }
        if (multicastLock != null && multicastLock.isHeld()) {
            multicastLock.release();
        }
        if (wifiLock != null && wifiLock.isHeld()) {
            wifiLock.release();
        }
        Log.d(TAG, "All locks released and server stopped.");
    }

    /**
     * Allows clients (like MediaServerManager) to observe the service's status.
     */
    public LiveData<MediaServerHub.ServerStatus> getStatusLiveData() {
        return statusLiveData;
    }

    // It just creates the *first* notification shown before anything is loaded.
    private Notification createInitialNotification() {
        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle(Constants.getPresentationName())
                .setContentText("Monitoring media sessions")
                .setSmallIcon(android.R.drawable.ic_media_play) // TODO: Change to your app's icon
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setOngoing(true)
                .build();
    }

    private void createNotificationChannel() {
        NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID,
                Constants.getPresentationName(),
                NotificationManager.IMPORTANCE_LOW
        );
        channel.setDescription("Manages media playback");
        if (notificationManager != null) {
            notificationManager.createNotificationChannel(channel);
        }
    }

    /**
     * Builds and displays the dynamic notification based on the server status, and number of music in collection.
     */
    private void updateNotification(MediaServerHub.ServerStatus status) {
        NotificationCompat.Builder builder;

        builder = createServerStatusNotification(status);

        // Update the existing foreground notification
        notificationManager.notify(MEDIA_SERVER.getId(), builder.build());
    }

    /**
     * Builds the notification for when no player is active.
     * Displays server status and total music count in the collection.
     */
    private NotificationCompat.Builder createServerStatusNotification(MediaServerHub.ServerStatus status) {
        long musicCount = tagRepos.getMusicTotal();
        // 2. Local variables for the notification content
        int statusIcon;
        String statusText = switch (status) {
            case RUNNING -> {
                statusIcon = R.drawable.ic_status_online;
                yield "Online";
            }
            case STOPPED -> {
                statusIcon = R.drawable.ic_status_offline;
                yield "Offline";
            }
            default -> {
                statusIcon = R.drawable.ic_status_offline;
                yield "Unknown";
            }
        };

        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle(Constants.getPresentationName())
                .setContentText("Server: " + statusText + " | " + musicCount + " tracks")
                .setSmallIcon(statusIcon)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setOngoing(true);
    }

    /**
     * Sets up network monitoring. This will not start the server, but will
     * stop it after 30 minutes if the WiFi connection is lost.
     */
    @SuppressLint("MissingPermission")
    private void startNetworkMonitoring() {
        NetworkRequest networkRequest = new NetworkRequest.Builder()
                .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
                .build();

        stopServerRunnable = () -> {
            Log.w(TAG, "WiFi has been disconnected for 30 minutes. Stopping server to save battery.");
            if (mediaServer.isInitialized()) {
                stopServers();
            }
        };

        networkCallback = new ConnectivityManager.NetworkCallback() {
            @Override
            public void onAvailable(@NonNull Network network) {
                // WiFi is connected. Cancel any pending shutdown command.
                Log.d(TAG, "WiFi connection available. Cancelling stop timer.");
                networkHandler.removeCallbacks(stopServerRunnable);
            }

            @Override
            public void onLost(@NonNull Network network) {
                // WiFi is lost. Schedule a shutdown in 30 minutes.
                Log.d(TAG, "WiFi connection lost. Server will stop in 30 minutes.");
                networkHandler.postDelayed(stopServerRunnable, 1800000L); // 30 minutes
            }
        };

        connectivityManager.registerNetworkCallback(networkRequest, networkCallback);
    }

    private void stopNetworkMonitoring() {
        // Unregister the callback to prevent leaks.
        if (networkCallback != null) {
            try {
                connectivityManager.unregisterNetworkCallback(networkCallback);
                networkCallback = null; // Clear the reference
            } catch (Exception e) {
                Log.w(TAG, "Error unregistering network callback", e);
            }
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();
        this.wifiManager = (WifiManager) getSystemService(Context.WIFI_SERVICE);
        this.powerManager = (PowerManager) getSystemService(Context.POWER_SERVICE);
        this.connectivityManager = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        this.notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);

        copyWebUIAssets(this);

        // Create the channel (it's safe to call this every time)
        createNotificationChannel();

        // Start foreground service immediately with the *initial* notification
        startForeground(MEDIA_SERVER.getId(), createInitialNotification());

        getStatusLiveData().observeForever(this::updateNotification);
    }

    @Override
    public void onDestroy() {
        // Ensure everything is cleaned up if the service is destroyed.
        stopServers();
        mediaServer.onDestroy();
        super.onDestroy();
    }


    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return binder;
    }

    private void copyWebUIAssets(Context context) {
        try {
            String assetDir = "webui";
            // Per your code, this will delete and re-copy on every service creation.
            ApplicationUtils.deleteFilesFromCache(context, assetDir);
            ApplicationUtils.copyFilesToCache(context, assetDir);
        } catch (IOException e) {
            Log.e(TAG, "Failed to copy web assets", e);
        }
    }

    public String getLibraryName() {
        return mediaServer.getLibraryName();
    }

    public boolean isRunning() {
        return mediaServer.isInitialized();
    }

    public class MediaServerHubBinder extends Binder {
        public MediaServerHub getMediaServerHub() {
            return mediaServer;
        }
        public MediaServerHubService getService() {
            return MediaServerHubService.this;
        }
    }
}
