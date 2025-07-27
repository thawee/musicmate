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
import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import org.jupnp.UpnpService;
import org.jupnp.UpnpServiceConfiguration;
import org.jupnp.UpnpServiceImpl;
import org.jupnp.model.meta.LocalDevice;
import org.jupnp.model.meta.RemoteDevice;
import org.jupnp.model.types.DeviceType;
import org.jupnp.model.types.ServiceType;
import org.jupnp.model.types.UDADeviceType;
import org.jupnp.model.types.UDAServiceType;
import org.jupnp.registry.DefaultRegistryListener;
import org.jupnp.registry.Registry;
import org.jupnp.registry.RegistryListener;

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

    // Standard DLNA Service Types
    public static final ServiceType CONTENT_DIRECTORY_SERVICE = new UDAServiceType("ContentDirectory", 1);
    public static final ServiceType AV_TRANSPORT_SERVICE = new UDAServiceType("AVTransport", 1);
    public static final ServiceType RENDERING_CONTROL_SERVICE = new UDAServiceType("RenderingControl", 1);
    public static final ServiceType CONNECTION_MANAGER_SERVICE = new UDAServiceType("ConnectionManager", 1);

    // Standard DLNA Device Types
    public static final DeviceType MEDIA_SERVER_DEVICE_TYPE = new UDADeviceType("MediaServer", 1);
    public static final DeviceType MEDIA_RENDERER_DEVICE_TYPE = new UDADeviceType("MediaRenderer", 1);

    // --- Add these constants for Broadcasts ---
    public static final String ACTION_STATUS_CHANGED = "apincer.android.mmate.dlna.STATUS_CHANGED";
    public static final String EXTRA_STATUS = "extra_status";
    public static final String EXTRA_ADDRESS = "extra_address";

    public enum ServerStatus { // You might already have this or a similar concept
        RUNNING,
        STOPPED,
        STARTING, // When onCreate/onStartCommand begins initialization
        INITIALIZED, // After successful initialization but before fully running (optional refinement)
        ERROR
    }
    // --- End of new constants ---

    // Service components
    protected UpnpService upnpService;
    private RegistryListener registryListener;
    protected LocalDevice mediaServerDevice;
    protected UpnpServiceConfiguration upnpServiceCfg;
    protected IBinder binder = new MediaServerServiceBinder();
    public static MediaServerService INSTANCE;
    private boolean initialized;

    // Network monitoring
    private ConnectivityManager connectivityManager;
    private ConnectivityManager.NetworkCallback networkCallback;

    // Wake lock to keep CPU running for stable streaming
    private PowerManager.WakeLock wakeLock;

    // Service health monitoring
    private ScheduledExecutorService healthChecker;

    public static void startMediaServer(Application application) {
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

        broadcastStatus(ServerStatus.STARTING, null);

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
            //Thread initializationThread = new Thread(this::initialize);
            // Broadcast STARTING explicitly if not done in onCreate,
            // or if re-attempting start.
            broadcastStatus(ServerStatus.STARTING, null);

            Thread initializationThread = new Thread(() -> {
                initialize(); // Your existing initialize method
                // After initialize() completes:
                if (isInitialized()) {
                    broadcastStatus(ServerStatus.RUNNING, getIpAddress() + ":" + MediaServerConfiguration.UPNP_SERVER_PORT);
                } else {
                    broadcastStatus(ServerStatus.ERROR, null); // If initialize failed
                }
            });
            initializationThread.setName("DLNA-Init");
            initializationThread.start();

            // Start health checker
            startHealthChecker();

            Log.d(TAG, "DLNA server startup initiated: " + (System.currentTimeMillis() - start) + "ms");
        }else {
            // If already initialized and running, just broadcast current status
            broadcastStatus(ServerStatus.RUNNING, getIpAddress() + ":" + MediaServerConfiguration.UPNP_SERVER_PORT);
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

                //
                this.registryListener = new MyRegistryListener();

                // Create and register media server device
                mediaServerDevice = MediaServerDevice.createMediaServerDevice(getApplicationContext());
                upnpService.getRegistry().addDevice(mediaServerDevice);

                this.upnpService.getRegistry().addListener(registryListener);

                // Send alive notification
                upnpService.getProtocolFactory().createSendingNotificationAlive(mediaServerDevice).run();
                // Initial search for all devices
                this.upnpService.getControlPoint().search();
                // Or search for specific types:
                // this.upnpService.getControlPoint().search(MEDIA_SERVER_DEVICE_TYPE);
                //this.upnpService.getControlPoint().search(MEDIA_RENDERER_DEVICE_TYPE);

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
        //String notificationTitle = "MusicMate [" + deviceModel + "]";

        NotificationCompat.Builder mBuilder = new NotificationCompat.Builder(this, MusixMateApp.NOTIFICATION_CHANNEL_ID)
                .setOngoing(true)
                .setSmallIcon(R.drawable.ic_notification_default)
                .setSilent(true)
                //.setContentTitle(notificationTitle)
                .setContentTitle(getApplicationContext().getString(R.string.media_server_name))
                .setGroup(MusixMateApp.NOTIFICATION_GROUP_KEY)
                .setSubText(deviceModel)
                //.setContentText(getApplicationContext().getString(R.string.media_server_name));
                .setContentText("http://"+getIpAddress()+":"+MediaServerConfiguration.UPNP_SERVER_PORT+"/music/");

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
        broadcastStatus(ServerStatus.STOPPED, null);
        super.onDestroy();
    }

    // --- Add this method to broadcast status ---
    public void broadcastStatus(ServerStatus status, @Nullable String address) {
        Intent intent = new Intent(ACTION_STATUS_CHANGED);
        intent.putExtra(EXTRA_STATUS, status.name()); // Send enum name as String
        if (address != null) {
            intent.putExtra(EXTRA_ADDRESS, address);
        }
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent);
        Log.d(TAG, "Broadcasted status: " + status + (address != null ? " Address: " + address : ""));

        // Also update notification if running or error
        if (status == ServerStatus.RUNNING && address != null) {
            updateNotificationText("Running at http://" + address);
        } else if (status == ServerStatus.ERROR) {
            updateNotificationText("Error starting server");
        } else if (status == ServerStatus.STOPPED) {
            // Notification is removed by stopForeground(true) in onDestroy
            // Or update to "Stopped" if you keep a persistent notification for the app
        }
    }

    private void updateNotificationText(String text) {
        // Your existing showNotification logic creates the initial notification.
        // This method updates its content text.
        // You'll need to make your NotificationCompat.Builder accessible or rebuild part of it.

        Intent notificationIntent = new Intent(this, MainActivity.class); // Assuming MainActivity
        PendingIntent contentIntent = PendingIntent.getActivity(this, 0, notificationIntent, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);

        String deviceModel = ApplicationUtils.getDeviceModel();

        NotificationCompat.Builder mBuilder = new NotificationCompat.Builder(this, MusixMateApp.NOTIFICATION_CHANNEL_ID)
                .setOngoing(true)
                .setSmallIcon(R.drawable.ic_notification_default)
                .setSilent(true)
                .setContentTitle(getApplicationContext().getString(R.string.media_server_name))
                .setGroup(MusixMateApp.NOTIFICATION_GROUP_KEY)
                .setSubText(deviceModel)
                .setContentText(text); // Key change here

        mBuilder.setContentIntent(contentIntent);

        NotificationManager notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (notificationManager != null) {
            notificationManager.notify(NotificationId.MEDIA_SERVER.getId(), mBuilder.build());
        }
    }

    private void shutdown() {
        try {
            // Send byebye notification before shutting down
            if (mediaServerDevice != null && upnpService != null) {
                upnpService.getProtocolFactory().createSendingNotificationByebye(mediaServerDevice).run();
            }
            if(upnpService != null) {
                upnpService.getRegistry().removeAllRemoteDevices(); // Optional: clear devices before shutdown
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
    @NonNull
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

    public static class MyRegistryListener extends DefaultRegistryListener {

        @Override
        public void remoteDeviceDiscoveryStarted(Registry registry, RemoteDevice device) {
            Log.d(TAG, "Discovery started for: " + device.getDisplayString());
        }

        @Override
        public void remoteDeviceDiscoveryFailed(Registry registry, RemoteDevice device, Exception ex) {
            Log.e(TAG, "Discovery failed for: " + device.getDisplayString() + " => " + ex.getMessage(), ex);
        }

        @Override
        public void remoteDeviceAdded(Registry registry, RemoteDevice device) {
            Log.i(TAG, "Remote device available: " + device.getDisplayString() + " (" + device.getType().getType() + ")");

            // You would typically update a UI list here or store the device
            // Example: Check for MediaServer or MediaRenderer
            //if (device.getType().equals(MEDIA_SERVER_DEVICE_TYPE)) {
           //     Log.i(TAG, "Found MediaServer: " + device.getDetails().getFriendlyName());
                // deviceFoundListener.onMediaServerFound(device);
           // } else
            if (device.getType().equals(MEDIA_RENDERER_DEVICE_TYPE)) {
                Log.i(TAG, "Found MediaRenderer: " + device.getDetails().getFriendlyName());
                // deviceFoundListener.onMediaRendererFound(device);
                MusixMateApp.getInstance().addRenderer(device);
            }

            // You can also check for specific services
           /* Service cdService = device.findService(CONTENT_DIRECTORY_SERVICE);
            if (cdService != null) {
                Log.d(TAG, device.getDetails().getFriendlyName() + " has ContentDirectory service.");
            }

            Service avtService = device.findService(AV_TRANSPORT_SERVICE);
            if (avtService != null) {
                Log.d(TAG, device.getDetails().getFriendlyName() + " has AVTransport service.");
            } */
        }

        @Override
        public void remoteDeviceRemoved(Registry registry, RemoteDevice device) {
            Log.i(TAG, "Remote device removed: " + device.getDisplayString());
            // Update your UI or remove from stored list
            // deviceFoundListener.onDeviceRemoved(device);
        }

        @Override
        public void localDeviceAdded(Registry registry, LocalDevice device) {
            Log.i(TAG, "Local device added: " + device.getDisplayString());
        }

        @Override
        public void localDeviceRemoved(Registry registry, LocalDevice device) {
            Log.i(TAG, "Local device removed: " + device.getDisplayString());
        }

        @Override
        public void beforeShutdown(Registry registry) {
            Log.i(TAG, "Registry about to shut down.");
        }

        @Override
        public void afterShutdown() {
            Log.i(TAG, "Registry has shut down.");
        }
    }
}
