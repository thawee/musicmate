package apincer.android.mmate.service;

import static apincer.music.core.server.BaseServer.CONTENT_SERVER_PORT;

import android.annotation.SuppressLint;
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
import android.os.IBinder;
import android.os.PowerManager;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import javax.inject.Inject;

import apincer.music.core.NotificationId;
import apincer.music.core.repository.FileRepository;
import apincer.music.core.repository.TagRepository;
import apincer.music.core.server.BaseServer;
import apincer.music.core.server.spi.MediaServer;
import apincer.android.mmate.MusixMateApp;
import apincer.android.mmate.R;
import apincer.music.core.utils.ApplicationUtils;
import dagger.hilt.android.AndroidEntryPoint;

@AndroidEntryPoint
public class MediaServerHostingService extends Service {
    private static final String TAG = "MediaServerHostingService";

    @Inject
    MediaServer mediaServer;
    @Inject
    FileRepository fileRepos;
    @Inject
    TagRepository tagRepos;

    private WifiManager wifiManager;
    private PowerManager powerManager;
    private ConnectivityManager connectivityManager;
    private NotificationManager notificationManager;

    // You will need locks to keep Wi-Fi active
    private WifiManager.WifiLock wifiLock;
    private WifiManager.MulticastLock multicastLock;

    // Network monitoring
    private ConnectivityManager.NetworkCallback networkCallback;

    // Wake lock to keep CPU running for stable streaming
    private PowerManager.WakeLock wakeLock;

    private final IBinder binder = new MediaServerBinder();

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && intent.getStringExtra("START_SERVER") != null) {
            startServers();
        }else if (intent != null && intent.getStringExtra("STOP_SERVER") != null) {
            stopServers();
        }
        return START_STICKY;
    }

    public void startServers() {
        if(mediaServer.isInitialized()) return;

        // Acquire wake lock to ensure reliable streaming
        wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,
                "MusixMate:MediaServerWakeLock");
        wakeLock.acquire(10*60*1000L /*10 minutes*/);

        if (wifiManager != null) {
            // Keeps the Wi-Fi radio from turning off
            wifiLock = wifiManager.createWifiLock(WifiManager.WIFI_MODE_FULL_LOW_LATENCY, "MyWifiLockTag");
            wifiLock.setReferenceCounted(false);
            wifiLock.acquire();

            // Allows the app to receive multicast packets
            multicastLock = wifiManager.createMulticastLock("MyMulticastLockTag");
            multicastLock.setReferenceCounted(false);
            multicastLock.acquire();
            Log.d(TAG, "Wi-Fi and Multicast locks acquired.");
        }
        showNotification();
        mediaServer.startServers();
        startNetworkMonitoring();
    }

    private void showNotification() {
        ((MusixMateApp)getApplicationContext()).createGroupNotification();

        // Intent notificationIntent = new Intent(this, MainActivity.class);
        // PendingIntent contentIntent = PendingIntent.getActivity(this, 0, notificationIntent, PendingIntent.FLAG_IMMUTABLE);

        String deviceModel = ApplicationUtils.getDeviceModel();
        //String notificationTitle = "MusicMate [" + deviceModel + "]";

        NotificationCompat.Builder mBuilder = new NotificationCompat.Builder(this, MusixMateApp.NOTIFICATION_CHANNEL_ID)
                .setOngoing(true)
                .setSmallIcon(R.drawable.ic_notification_default)
                .setSilent(true)
                //.setContentTitle(notificationTitle)
                .setContentTitle(getApplicationContext().getString(R.string.media_server_name))
                .setGroup(MusixMateApp.NOTIFICATION_GROUP_KEY)
                .setSubText(deviceModel)
                .setPriority(NotificationCompat.PRIORITY_LOW) // Use a lower priority for less important notifications
                //.setContentText(getApplicationContext().getString(R.string.media_server_name));
                .setContentText("http://"+ BaseServer.getIpAddress()+":"+ CONTENT_SERVER_PORT);

        // mBuilder.setContentIntent(contentIntent);
        startForeground(NotificationId.MEDIA_SERVER.getId(), mBuilder.build());
    }

    /**
     * Set up network monitoring to handle connectivity changes
     */
    @SuppressLint("MissingPermission")
    private void startNetworkMonitoring() {
        NetworkRequest networkRequest = new NetworkRequest.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
                .build();

        networkCallback = new ConnectivityManager.NetworkCallback() {
            @Override
            public void onAvailable(@NonNull Network network) {
                Log.d(TAG, "Network available in media server service");

                 // If service lost its resources, re-initialize
                if (!mediaServer.isInitialized()) {
                    mediaServer.startServers();
                    cancelNotification();
                }
            }

            @Override
            public void onLost(@NonNull Network network) {
                Log.d(TAG, "Network lost in media server service");

                // If service lost its resources, re-initialize
                if (mediaServer.isInitialized()) {
                    showNotification();
                    mediaServer.stopServers();
                }
            }
        };

        connectivityManager.registerNetworkCallback(networkRequest, networkCallback);
        networkCallback = null;
    }

    private void stopNetworkMonitoring() {
        if(networkCallback!= null) {
            connectivityManager.unregisterNetworkCallback(networkCallback);
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();
        // Get the singleton instance from the Application
       // this.mediaServer =  new MediaServerImpl(getApplicationContext(), fileRepos, tagRepos);
        this.wifiManager = (WifiManager) getSystemService(Context.WIFI_SERVICE);
        this.powerManager = (PowerManager) getSystemService(Context.POWER_SERVICE);
        this.connectivityManager = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        this.notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        copyWebUIAssets(this);
    }

    @Override
    public void onDestroy() {
        mediaServer.onDestroy();
        super.onDestroy();
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return binder;
    }

    /**
     * Cancels the notification.
     */
    private void cancelNotification() {
        // NotificationManager mNotificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        // mId allows you to update the notification later on.
        notificationManager.cancel(NotificationId.MEDIA_SERVER.getId());
        ((MusixMateApp) getApplicationContext()).cancelGroupNotification();
    }

    private void copyWebUIAssets(Context context) {
        try {
            String assetDir = "webui";
            String[] assets = context.getAssets().list(assetDir);
            if (assets == null || assets.length == 0) {
                return;
            }

            File webappDir = new File(context.getFilesDir(), assetDir);
            if (!webappDir.exists()) {
                webappDir.mkdirs();
            }

            for (String asset : assets) {
                File destFile = new File(webappDir, asset);
                // Only copy if the file doesn't exist to prevent overwriting on every launch.
                if (!destFile.exists()) {
                    try (InputStream in = context.getAssets().open(assetDir + "/" + asset);
                         OutputStream out = new FileOutputStream(destFile)) {

                        byte[] buffer = new byte[1024];
                        int read;
                        while ((read = in.read(buffer)) != -1) {
                            out.write(buffer, 0, read);
                        }
                    }
                    Log.i(TAG, "Copied web asset: " + asset);
                }
            }
        } catch (IOException e) {
            Log.e(TAG, "Failed to copy web assets", e);
        }
    }

    public void stopServers() {
        stopNetworkMonitoring();
        mediaServer.stopServers();
        cancelNotification();
        if (wakeLock != null && wakeLock.isHeld()) {
            wakeLock.release();
        }
        if (multicastLock != null && multicastLock.isHeld()) {
            multicastLock.release();
            Log.d(TAG, "Multicast lock released.");
        }
        if (wifiLock != null && wifiLock.isHeld()) {
            wifiLock.release();
            Log.d(TAG, "Wi-Fi lock released.");
        }
    }

    // The binder now provides access to the INTERFACE, not the service itself
    public class MediaServerBinder extends Binder {
        public MediaServer getMediaServer() {
            return mediaServer;
        }

        public MediaServerHostingService getService() {
            return MediaServerHostingService.this;
        }
    }
}
