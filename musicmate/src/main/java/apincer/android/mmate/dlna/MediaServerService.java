package apincer.android.mmate.dlna;

import android.app.Application;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkRequest;
import android.os.IBinder;
import android.os.PowerManager;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.core.app.NotificationCompat;

import org.jupnp.UpnpService;
import org.jupnp.UpnpServiceConfiguration;
import org.jupnp.UpnpServiceImpl;
import org.jupnp.model.meta.LocalDevice;

import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.Enumeration;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

import apincer.android.mmate.MusixMateApp;
import apincer.android.mmate.notification.NotificationId;
import apincer.android.mmate.R;
import apincer.android.mmate.ui.MainActivity;
import apincer.android.mmate.utils.ApplicationUtils;
import apincer.android.mmate.worker.MusicMateExecutors;

/**
 * DLNA Media Server consists of
 *  - Basic UPnp Framework (addressing, device discovery, content directory service, SOAP, eventing, etc.)
 *  - UPnP Content Directory Service
 *     - DLNA Digital Content Decoder
 *     - DLNA Digital Content Profiler
 *  - UPnP Connection Manager Service
 *  - HTTP Streamer - for streaming digital content to client
 *  - UPnp AV Transport Server (Optional)
 *  Note:
 *   - netty smooth, cpu < 10%, memory < 256 MB, short peak to 512 MB
 *     have issue jupnp auto stopping/starting on wifi loss
 *   - httpcore smooth, better SQ than netty, cpu <10, memory < 256 mb, peak 380mb
 *     have bug to stop playing on client sometime
 *   - jetty12 is faster than jetty11 12% but cannot run on android 34
 *   <p>
 *   Optimized for compatibility with DLNA clients like mConnectHD and RoPieeeXL
 *  </p>
 */
public class MediaServerService extends Service {
    private static final String TAG = "MediaServerService";
    public static final Pattern IPV4_PATTERN =
            Pattern.compile(
                    "^(25[0-5]|2[0-4]\\d|[0-1]?\\d?\\d)(\\.(25[0-5]|2[0-4]\\d|[0-1]?\\d?\\d)){3}$");

    // Service components
    protected UpnpService upnpService;
    protected LocalDevice mediaServerDevice;
    protected UpnpServiceConfiguration upnpServiceCfg;
    protected IBinder binder = new MediaServerServiceBinder();
    protected static MediaServerService INSTANCE;
    private boolean initialized;

    // Network monitoring
    private ConnectivityManager connectivityManager;
    private ConnectivityManager.NetworkCallback networkCallback;
    private boolean isNetworkAvailable = false;

    // Wake lock to keep CPU running for stable streaming
    private PowerManager.WakeLock wakeLock;

    // Service health monitoring
    private ScheduledExecutorService healthChecker;

    public static void startMediaServer(Application application) {
       // Intent intent = new Intent(application, MediaServerService.class);
       // application.startForegroundService(intent);
        Log.d(TAG, "Start media server requested");
        if (INSTANCE != null && INSTANCE.isInitialized()) {
            Log.d(TAG, "Media server already running");
            return;
        }

        Intent intent = new Intent(application, MediaServerService.class);
        try {
            application.startForegroundService(intent);
        } catch (Exception e) {
            Log.e(TAG, "Error starting media server service", e);
        }
    }

    public static void stopMediaServer(Application application) {
        Log.d(TAG, "Stop media server requested");
        if (INSTANCE == null) {
            Log.d(TAG, "Media server not running");
            return;
        }

        Intent intent = new Intent(application, MediaServerService.class);
        application.stopService(intent);
    }

    /**
     * @return the initialized
     */
    public boolean isInitialized() {
        return initialized;
    }

    /**
     * Starts the UPnP service.
     */
    @Override
    public void onCreate() {
        super.onCreate();
        INSTANCE = this;

        // Acquire wake lock to ensure reliable streaming
        PowerManager powerManager = (PowerManager) getSystemService(POWER_SERVICE);
        wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,
                "MusixMate:MediaServerWakeLock");
        wakeLock.acquire(10*60*1000L /*10 minutes*/);

        // Set up network monitoring
        setupNetworkMonitoring();
    }

    /**
     * Handle service start command
     */
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if(!isInitialized()) {
            long start = System.currentTimeMillis();
            showNotification();

            // Initialize asynchronously to avoid ANR
            Thread initializationThread = new Thread(this::initialize);
            initializationThread.setName("DLNA-Init");
            initializationThread.start();

            // Start health checker
            startHealthChecker();

            Log.d(TAG, "DLNA server startup initiated: " + (System.currentTimeMillis() - start) + "ms");
        }
        return START_STICKY;
    }

    /**
     * Set up network monitoring to handle connectivity changes
     */
    private void setupNetworkMonitoring() {
        connectivityManager = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);

        NetworkRequest networkRequest = new NetworkRequest.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
                .build();

        networkCallback = new ConnectivityManager.NetworkCallback() {
            @Override
            public void onAvailable(@NonNull Network network) {
                //Log.d(TAG, "Network available in media server service");
                isNetworkAvailable = true;

                // Don't re-initialize if already running
                if (!initialized || upnpService == null || mediaServerDevice == null) {
                    // If service lost its resources, re-initialize
                    if (!initialized) {
                        Thread initThread = new Thread(() -> {
                            initialize();
                            //Log.i(TAG, "Media server re-initialized after network connection");
                        });
                        initThread.setName("DLNA-Reinit");
                        initThread.start();
                    }
                    return;
                }

                // Network is available and server is running - re-announce
                MusicMateExecutors.schedule(() -> {
                    try {
                        // Re-register and announce
                        upnpService.getRegistry().addDevice(mediaServerDevice);
                        upnpService.getProtocolFactory().createSendingNotificationAlive(mediaServerDevice).run();
                        Log.i(TAG, "DLNA server re-announced after network change");
                    } catch (Exception e) {
                        Log.e(TAG, "Error re-announcing DLNA server", e);
                    }
                }, 2);
            }

            @Override
            public void onLost(@NonNull Network network) {
              //  Log.d(TAG, "Network lost in media server service");
                isNetworkAvailable = false;

                // Send byebye notification when network is lost
                if (initialized && upnpService != null && mediaServerDevice != null) {
                    try {
                        upnpService.getProtocolFactory().createSendingNotificationByebye(mediaServerDevice).run();
                       // Log.d(TAG, "Sent byebye notification for lost network");
                    } catch (Exception e) {
                        Log.e(TAG, "Error sending byebye notification", e);
                    }
                }
            }
        };

        connectivityManager.registerNetworkCallback(networkRequest, networkCallback);
        // Initial check
        Network activeNetwork = connectivityManager.getActiveNetwork();
        if (activeNetwork != null) {
            NetworkCapabilities capabilities = connectivityManager.getNetworkCapabilities(activeNetwork);
            isNetworkAvailable = capabilities != null &&
                    capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) &&
                    capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET);
        }
    }

    /**
     * Initialize the DLNA server
     */
    private synchronized void initialize() {
        if(!initialized) {
            shutdown(); // clean up before start

            try {
                //Log.d(TAG, "Initializing DLNA media server");

                // Create server configuration
                upnpServiceCfg = new MediaServerConfiguration(getApplicationContext());
                upnpService = new UpnpServiceImpl(upnpServiceCfg) {
                    @Override
                    public synchronized void shutdown() {
                        // Now we can concurrently run the Cling shutdown code, without occupying the
                        // Android main UI thread. This will complete probably after the main UI thread
                        // is done.
                        super.shutdown(true);
                    }
                };
                // Start UPnP service
                upnpService.startup();

                // Create and register media server device
                mediaServerDevice = MediaServerDevice.createMediaServerDevice(getApplicationContext());
                upnpService.getRegistry().addDevice(mediaServerDevice);

                // Send alive notification
                upnpService.getProtocolFactory().createSendingNotificationAlive(mediaServerDevice).run();

                // Mark as initialized
                MediaServerService.this.initialized = true;
                Log.i(TAG, "DLNA media server initialized successfully");

                // Debugging: Log available services
                logAvailableServices();
            } catch (Exception e) {
                Log.e(TAG, "Error initializing DLNA media server", e);
                shutdown();
            }
        }
    }

    private void showNotification() {
        ((MusixMateApp) getApplicationContext()).createGroupNotification();

        Intent notificationIntent = new Intent(this, MainActivity.class);
        PendingIntent contentIntent = PendingIntent.getActivity(this, 0, notificationIntent, PendingIntent.FLAG_IMMUTABLE);

        String deviceModel = ApplicationUtils.getDeviceModel();
        String notificationTitle = "MusicMate [" + deviceModel + "]";

        NotificationCompat.Builder mBuilder = new NotificationCompat.Builder(this, MusixMateApp.NOTIFICATION_CHANNEL_ID)
                .setOngoing(true)
                .setSmallIcon(R.drawable.ic_notification_default)
                .setSilent(true)
                .setContentTitle(notificationTitle)
                .setGroup(MusixMateApp.NOTIFICATION_GROUP_KEY)
                .setContentText(getApplicationContext().getString(R.string.media_server_name));

        mBuilder.setContentIntent(contentIntent);
        startForeground(NotificationId.MEDIA_SERVER.getId(), mBuilder.build());
    }

    /**
     * Start the health checker that periodically verifies the server is operational
     */
    private void startHealthChecker() {
        healthChecker = Executors.newSingleThreadScheduledExecutor();
        healthChecker.scheduleWithFixedDelay(() -> {
            if (initialized && upnpService != null) {
                try {
                    // Verify UPnP service is still operational
                    boolean registryActive = upnpService.getRegistry() != null &&
                            upnpService.getRegistry().getLocalDevices().size() > 0;

                    if (!registryActive) {
                        Log.w(TAG, "DLNA registry appears inactive, attempting recovery");
                        // Re-register if needed
                        if (mediaServerDevice != null) {
                            upnpService.getRegistry().addDevice(mediaServerDevice);
                        }
                    }

                    // Every 5 checks, send an alive message to ensure visibility
                    if (System.currentTimeMillis() % (5 * 30000) < 30000) {
                        if (mediaServerDevice != null) {
                            upnpService.getProtocolFactory().createSendingNotificationAlive(mediaServerDevice).run();
                        }
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Error in DLNA health check", e);
                }
            }
        }, 30, 30, TimeUnit.SECONDS);
    }

    /**
     * Log all available services for debugging
     */
    private void logAvailableServices() {
        if (mediaServerDevice != null) {
           // Log.d(TAG, "Available DLNA services:");
            for (org.jupnp.model.meta.Service service : mediaServerDevice.getServices()) {
                Log.d(TAG, " - " + service.getServiceType().getType());
            }
        }
    }


    @Override
    public IBinder onBind(Intent intent) {
        return binder;
    }

    /**
     * Stops the UPnP service, when the last Activity unbinds from this Service.
     */
    @Override
    public void onDestroy() {
        Thread shutdownThread = new Thread(this::shutdown);
        shutdownThread.start();

        // Clean up resources
        if (networkCallback != null && connectivityManager != null) {
            try {
                connectivityManager.unregisterNetworkCallback(networkCallback);
            } catch (Exception e) {
                Log.e(TAG, "Error unregistering network callback", e);
            }
        }

        if (healthChecker != null) {
            healthChecker.shutdownNow();
        }

        if (wakeLock != null && wakeLock.isHeld()) {
            wakeLock.release();
        }

        cancelNotification();
        super.onDestroy();
    }

    private void shutdown() {
        try {
            // Send byebye notification before shutting down
            if (mediaServerDevice != null && upnpService != null) {
                upnpService.getProtocolFactory().createSendingNotificationByebye(mediaServerDevice).run();
            }
            if(upnpService != null) {
                upnpService.shutdown();
                upnpService = null;
            }
        } catch (Exception e) {
            Log.e(TAG, "Error shutting down UPnP service", e);
        }
        initialized = false;
    }

    /**
     * Cancels the notification.
     */
    private void cancelNotification() {
        NotificationManager mNotificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        // mId allows you to update the notification later on.
        mNotificationManager.cancel(NotificationId.MEDIA_SERVER.getId());
        ((MusixMateApp) getApplicationContext()).cancelGroupNotification();
    }

    /**
     * get the ip address of the device
     *
     * @return the address or null if anything went wrong
     */
    @Deprecated
    public static String getIpAddress() {
        String hostAddress = null;
        try {
            for (Enumeration<NetworkInterface> networkInterfaces = NetworkInterface
                    .getNetworkInterfaces(); networkInterfaces
                         .hasMoreElements(); ) {
                NetworkInterface networkInterface = networkInterfaces
                        .nextElement();
                if (!networkInterface.getName().startsWith("rmnet")) {
                    for (Enumeration<InetAddress> inetAddresses = networkInterface
                            .getInetAddresses(); inetAddresses.hasMoreElements(); ) {
                        InetAddress inetAddress = inetAddresses.nextElement();
                        if (!inetAddress.isLoopbackAddress() && inetAddress
                                .getHostAddress() != null
                                && IPV4_PATTERN.matcher(inetAddress
                                .getHostAddress()).matches()) {

                            hostAddress = inetAddress.getHostAddress();
                        }

                    }
                }
            }
        } catch (SocketException se) {
            Log.d(TAG,
                    "Error while retrieving network interfaces", se);
        }
        // maybe wifi is off we have to use the loopback device
        hostAddress = hostAddress == null ? "0.0.0.0" : hostAddress;
        return hostAddress;
    }

    public class MediaServerServiceBinder extends android.os.Binder {
        public MediaServerService getService() {
            return MediaServerService.this;
        }
    }

}
