package apincer.android.mmate.dlna;

import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkRequest;
import android.os.Binder;
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
import org.jupnp.model.types.UDADeviceType;
import org.jupnp.registry.DefaultRegistryListener;
import org.jupnp.registry.Registry;
import org.jupnp.registry.RegistryListener;

import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.net.URL;
import java.util.Enumeration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.regex.Pattern;

import apincer.android.mmate.MusixMateApp;
import apincer.android.mmate.NotificationId;
import apincer.android.mmate.R;
import apincer.android.mmate.playback.PlaybackService;
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
 *  - UPnp AV Transport Server (Not Supported)
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

    // Standard DLNA Device Types
    public static final DeviceType MEDIA_RENDERER_DEVICE_TYPE = new UDADeviceType("MediaRenderer", 1);

    // --- Add these constants for Broadcasts ---
    public static final String ACTION_STATUS_CHANGED = "apincer.android.mmate.dlna.STATUS_CHANGED";
    public static final String EXTRA_STATUS = "extra_status";
    public static final String EXTRA_ADDRESS = "extra_address";

    private final List<RemoteDevice> renderers = new CopyOnWriteArrayList<>();

    public LocalDevice getServerDevice() {
        return mediaServerDevice;
    }

    public RendererController getRendererController(PlaybackService playbackService) {
        rendererControls.setPlaybackService(playbackService);
        return rendererControls;
    }

    public void stopServers() {
        // Clean up resources
        if (networkCallback != null && connectivityManager != null) {
            try {
                connectivityManager.unregisterNetworkCallback(networkCallback);
            } catch (Exception e) {
                Log.e(TAG, "Error unregistering network callback", e);
            }
        }

        Thread shutdownThread = new Thread(this::shutdown);
        shutdownThread.start();
    }

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
    protected LocalDevice mediaServerDevice;
    protected UpnpServiceConfiguration upnpServiceCfg;
    protected IBinder binder = new MediaServerServiceBinder();
    private boolean initialized;

    // Network monitoring
    private ConnectivityManager connectivityManager;
    private ConnectivityManager.NetworkCallback networkCallback;

    // Wake lock to keep CPU running for stable streaming
    private PowerManager.WakeLock wakeLock;

    private RendererController rendererControls;

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

        // Acquire wake lock to ensure reliable streaming
        PowerManager powerManager = (PowerManager) getSystemService(POWER_SERVICE);
        wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,
                "MusixMate:MediaServerWakeLock");
        wakeLock.acquire(10*60*1000L /*10 minutes*/);

       // broadcastStatus(ServerStatus.STARTING, null);
    }

    /**
     * Handle service start command
     */
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        showNotification();
        if(intent.getStringExtra("START_SERVERS") != null) {
            startServers();
        }
        return START_STICKY;
    }

    private void startServers() {
        if(!isInitialized()) {
            long start = System.currentTimeMillis();
            showNotification();

            // Initialize asynchronously to avoid ANR
            //Thread initializationThread = new Thread(this::initialize);
            // Broadcast STARTING explicitly if not done in onCreate,
            // or if re-attempting start.
            //broadcastStatus(ServerStatus.STARTING, null);

            Thread initializationThread = new Thread(() -> {
                initialize(); // Your existing initialize method
                // After initialize() completes:
                if (isInitialized()) {
                    broadcastStatus(ServerStatus.RUNNING, getIpAddress() + ":" + MediaServerConfiguration.WEB_SERVER_PORT);
                } else {
                    broadcastStatus(ServerStatus.ERROR, null); // If initialize failed
                }
            });
            initializationThread.setName("MediaServer-Init");
            initializationThread.start();

            // Set up network monitoring
            setupNetworkMonitoring();

            Log.d(TAG, "MediaServer startup initiated: " + (System.currentTimeMillis() - start) + "ms");
        }else {
            // If already initialized and running, just broadcast current status
            broadcastStatus(ServerStatus.RUNNING, getIpAddress() + ":" + MediaServerConfiguration.WEB_SERVER_PORT);
        }
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
                        initThread.setName("MediaServer-Reinit");
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
                        Log.i(TAG, "MediaServer re-announced after network change");
                    } catch (Exception e) {
                        Log.e(TAG, "Error re-announcing MediaServer", e);
                    }
                }, 2);
            }
        };

        connectivityManager.registerNetworkCallback(networkRequest, networkCallback);
    }

    /**
     * Initialize the DLNA server
     */
    private synchronized void initialize() {
        if(!initialized) {
            shutdown(); // clean up before start

            try {
                //Log.d(TAG, "Initializing DLNA media server");

                // Set system properties BEFORE any jUPnP code is executed
                System.setProperty("org.slf4j.simpleLogger.defaultLogLevel", "trace");
                System.setProperty("org.slf4j.simpleLogger.showThreadName", "false");

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
                RegistryListener registryListener = new MyRegistryListener();

                // Create and register media server device
                mediaServerDevice = MediaServerDevice.createMediaServerDevice(getApplicationContext());
                upnpService.getRegistry().addDevice(mediaServerDevice);

                this.upnpService.getRegistry().addListener(registryListener);

                // Send alive notification
                upnpService.getProtocolFactory().createSendingNotificationAlive(mediaServerDevice).run();
                // Initial search for all devices
                this.upnpService.getControlPoint().search(MEDIA_RENDERER_DEVICE_TYPE.getVersion());

                this.rendererControls = new RendererController(upnpService);

                // Mark as initialized
                this.initialized = true;
                Log.i(TAG, "MediaServer initialized successfully");
            } catch (Exception e) {
                Log.e(TAG, "Error initializing MediaServer", e);
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
                .setPriority(NotificationCompat.PRIORITY_LOW) // Use a lower priority for less important notifications
                //.setContentText(getApplicationContext().getString(R.string.media_server_name));
                .setContentText("http://"+getIpAddress()+":"+MediaServerConfiguration.WEB_SERVER_PORT+"/");

        mBuilder.setContentIntent(contentIntent);
        startForeground(NotificationId.MEDIA_SERVER.getId(), mBuilder.build());
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

        if (wakeLock != null && wakeLock.isHeld()) {
            wakeLock.release();
        }

        cancelNotification();
       // broadcastStatus(ServerStatus.STOPPED, null);
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
        //Log.d(TAG, "Broadcasted status: " + status + (address != null ? " Address: " + address : ""));

        // Also update notification if running or error
        if (status == ServerStatus.RUNNING && address != null) {
            updateNotificationText("DMS running on " + address);
        } else if (status == ServerStatus.ERROR) {
            updateNotificationText("Error starting DMS server");
        } else if (status == ServerStatus.STOPPED) {
            // Notification is removed by stopForeground(true) in onDestroy
            // Or update to "Stopped" if you keep a persistent notification for the app
            updateNotificationText("DMS stopped!");
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
            if(rendererControls != null) {
                rendererControls.stopPolling();
            }

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
    public String getIpAddress() {
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

    public class MediaServerServiceBinder extends Binder {
        public MediaServerService getService() {
            return MediaServerService.this;
        }
    }

    public class MyRegistryListener extends DefaultRegistryListener {

        @Override
        public void remoteDeviceDiscoveryStarted(Registry registry, RemoteDevice device) {
            //Log.d(TAG, "Discovery started for: " + device.getDisplayString());
        }

        @Override
        public void remoteDeviceDiscoveryFailed(Registry registry, RemoteDevice device, Exception ex) {
            //Log.e(TAG, "Discovery failed for: " + device.getDisplayString() + " => " + ex.getMessage(), ex);
        }

        @Override
        public void remoteDeviceAdded(Registry registry, RemoteDevice device) {
           // Log.i(TAG, "Remote device available: " + device.getDisplayString() + " (" + device.getType().getType() + ")");

            if (device.getType().equals(MEDIA_RENDERER_DEVICE_TYPE)) {
                addRenderer(device);
            }
        }

        @Override
        public void remoteDeviceUpdated(Registry registry, RemoteDevice device) {
            super.remoteDeviceUpdated(registry, device);
            if (device.getType().equals(MEDIA_RENDERER_DEVICE_TYPE)) {
                addRenderer(device);
            }
        }

        @Override
        public void remoteDeviceRemoved(Registry registry, RemoteDevice device) {
           // Log.i(TAG, "Remote device removed: " + device.getDisplayString());
            // Update your UI or remove from stored list
            // deviceFoundListener.onDeviceRemoved(device);
        }

        @Override
        public void localDeviceAdded(Registry registry, LocalDevice device) {
           // Log.i(TAG, "Local device added: " + device.getDisplayString());
        }

        @Override
        public void localDeviceRemoved(Registry registry, LocalDevice device) {
           // Log.i(TAG, "Local device removed: " + device.getDisplayString());
        }

        @Override
        public void beforeShutdown(Registry registry) {
           // Log.i(TAG, "Registry about to shut down.");
        }

        @Override
        public void afterShutdown() {
           // Log.i(TAG, "Registry has shut down.");
        }
    }

    public List<RemoteDevice> getRenderers() {
        return renderers;
    }

    private synchronized void addRenderer(RemoteDevice device) {
        String udn = device.getIdentity().getUdn().getIdentifierString();
        RemoteDevice udnDev = getRendererByUDN(udn);
        if(udnDev != null) {
            renderers.remove(udnDev);
        }
        renderers.add(device);
    }

    public RemoteDevice getRendererByUDN(String udn) {
        for(RemoteDevice dev: renderers) {
            String ludn = dev.getIdentity().getUdn().getIdentifierString();
            if(udn != null && udn.equals(ludn)) {
                return dev;
            }
        }
        return null;
    }

    public RemoteDevice getRendererByIpAddress(String ipAddress) {
        for(RemoteDevice dev: renderers) {
            String ip = getDeviceIpAddress(dev);
            if(ipAddress.equals(ip)) {
                return dev;
            }
        }
        return null;
    }

    public static String getDeviceIpAddress(RemoteDevice device) {
        URL descriptorURL = device.getIdentity().getDescriptorURL();
        return descriptorURL.getHost();
    }


}
