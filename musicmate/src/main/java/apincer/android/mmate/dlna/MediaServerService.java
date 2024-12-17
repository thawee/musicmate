package apincer.android.mmate.dlna;

import android.app.Application;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.IBinder;
import android.util.Log;

import androidx.core.app.NotificationCompat;

import org.jupnp.UpnpService;
import org.jupnp.UpnpServiceConfiguration;
import org.jupnp.UpnpServiceImpl;
import org.jupnp.model.meta.LocalDevice;
import org.jupnp.protocol.ProtocolFactory;
import org.jupnp.registry.Registry;
import org.jupnp.transport.Router;
import org.jupnp.transport.RouterException;

import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.Enumeration;
import java.util.regex.Pattern;

import apincer.android.mmate.MusixMateApp;
import apincer.android.mmate.notification.NotificationId;
import apincer.android.mmate.R;
import apincer.android.mmate.dlna.android.AndroidRouter;
import apincer.android.mmate.ui.MainActivity;
import apincer.android.mmate.utils.ApplicationUtils;

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
 */
public class MediaServerService extends Service {
    private static final String TAG = "MediaServerService";
    public static final Pattern IPV4_PATTERN =
            Pattern.compile(
                    "^(25[0-5]|2[0-4]\\d|[0-1]?\\d?\\d)(\\.(25[0-5]|2[0-4]\\d|[0-1]?\\d?\\d)){3}$");
    protected UpnpService upnpService;
    protected LocalDevice mediaServerDevice;
    protected UpnpServiceConfiguration upnpServiceCfg;
    protected IBinder binder = new MediaServerServiceBinder();
    protected static MediaServerService INSTANCE;
    private boolean initialized;

    public static void startMediaServer(Application application) {
        application.startForegroundService(new Intent(application, MediaServerService.class));
    }

    public static void stopMediaServer(Application application) {
        application.stopService(new Intent(application, MediaServerService.class));
    }

    public boolean isMediaServerStarted() throws RouterException {
        return upnpService != null && upnpService.getRouter().isEnabled();
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
        //long start = System.currentTimeMillis();
        super.onCreate();
        INSTANCE = this;
       // Log.d(TAG, "on start took: " + (System.currentTimeMillis() - start));
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if(!isInitialized()) {
            long start = System.currentTimeMillis();
            showNotification();
            // the footprint of the onStart() method must be small
            // otherwise android will kill the service
            // in order of this circumstance we have to initialize the service
            // asynchronous
            Thread initializationThread = new Thread(this::initialize);
            initializationThread.start();
            Log.d(TAG, "on start took: " + (System.currentTimeMillis() - start) + "ms");
        }
        return START_STICKY;
    }


    /**
     *
     */
    private synchronized void initialize() {
       // this.initialized = false;
        if(!initialized) {
            shutdown(); // clean up before start

            upnpServiceCfg = new MediaServerConfiguration(getApplicationContext());
            upnpService = new UpnpServiceImpl(upnpServiceCfg) {

                @Override
                protected Router createRouter(ProtocolFactory protocolFactory, Registry registry) {
                    return new AndroidRouter(getConfiguration(), protocolFactory, MediaServerService.this);
                }

                @Override
                public synchronized void shutdown() {
                    // Now we can concurrently run the Cling shutdown code, without occupying the
                    // Android main UI thread. This will complete probably after the main UI thread
                    // is done.
                    super.shutdown(true);
                }
            };
            upnpService.startup();
            mediaServerDevice = MediaServerDevice.createMediaServerDevice(getApplicationContext()); // createMediaServerDevice();
            upnpService.getRegistry().addDevice(mediaServerDevice);
            // send live notification
            upnpService.getProtocolFactory().createSendingNotificationAlive(mediaServerDevice).run();
            MediaServerService.this.initialized = true;
        }
    }

    private void showNotification() {
        ((MusixMateApp) getApplicationContext()).createGroupNotification();
        Intent notificationIntent = new Intent(this, MainActivity.class);
        PendingIntent contentIntent = PendingIntent.getActivity(this, 0, notificationIntent, PendingIntent.FLAG_IMMUTABLE);
        NotificationCompat.Builder mBuilder = new NotificationCompat.Builder(this, MusixMateApp.NOTIFICATION_CHANNEL_ID)
                .setOngoing(true)
                .setSmallIcon(R.drawable.ic_notification_default)
                .setSilent(true)
                .setContentTitle("MusicMate ["+ ApplicationUtils.getDeviceModel()+"]")
                .setGroup(MusixMateApp.NOTIFICATION_GROUP_KEY)
                .setContentText(getApplicationContext().getString(R.string.media_server_name));
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
        // This will stop the UPnP service if nobody else is bound to it
        cancelNotification();
        super.onDestroy();
    }

    private void shutdown() {
        if(upnpService != null) {
            upnpService.shutdown();
            upnpService = null;
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
