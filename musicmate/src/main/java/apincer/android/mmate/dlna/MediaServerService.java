package apincer.android.mmate.dlna;

import android.app.Application;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.IBinder;
import android.util.Log;

import androidx.core.app.NotificationCompat;
import androidx.preference.PreferenceManager;

import org.apache.commons.io.IOUtils;
import org.jupnp.UpnpService;
import org.jupnp.UpnpServiceConfiguration;
import org.jupnp.UpnpServiceImpl;
import org.jupnp.binding.annotations.AnnotationLocalServiceBinder;
import org.jupnp.model.DefaultServiceManager;
import org.jupnp.model.ValidationException;
import org.jupnp.model.meta.DeviceDetails;
import org.jupnp.model.meta.DeviceIdentity;
import org.jupnp.model.meta.Icon;
import org.jupnp.model.meta.LocalDevice;
import org.jupnp.model.meta.LocalService;
import org.jupnp.model.meta.ManufacturerDetails;
import org.jupnp.model.meta.ModelDetails;
import org.jupnp.model.types.UDADeviceType;
import org.jupnp.model.types.UDN;
import org.jupnp.protocol.ProtocolFactory;
import org.jupnp.registry.Registry;
import org.jupnp.support.connectionmanager.ConnectionManagerService;
import org.jupnp.support.model.Protocol;
import org.jupnp.support.model.ProtocolInfo;
import org.jupnp.support.model.ProtocolInfos;
import org.jupnp.support.xmicrosoft.AbstractMediaReceiverRegistrarService;
import org.jupnp.transport.Router;

import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.net.URI;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;

import apincer.android.mmate.Constants;
import apincer.android.mmate.MusixMateApp;
import apincer.android.mmate.NotificationId;
import apincer.android.mmate.R;
import apincer.android.mmate.dlna.content.ContentDirectory;
import apincer.android.mmate.dlna.transport.AndroidRouter;
import apincer.android.mmate.ui.MainActivity;
import apincer.android.mmate.utils.ApplicationUtils;
import apincer.android.mmate.utils.StringUtils;

/**
 * DLNA Media Server consists of
 *  - Basic UPnp Framework (addressing, device discovery, content directory service, SOAP, eventing, etc.)
 *  - UPnP Content Directory Service
 *     - DLNA Digital Content Decoder
 *     - DLNA Digital Content Profiler
 *  - UPnP Connection Manager Service
 *  - HTTP Streamer - for streaming digital content to client
 *  - UPnp AV Transport Server (Optional)
 *
 */
public class MediaServerService extends Service {
    private static final String TAG = "MediaServerService";
    private static final int MIN_ADVERTISEMENT_AGE_SECONDS = 300;
    public static final Pattern IPV4_PATTERN =
            Pattern.compile(
                    "^(25[0-5]|2[0-4]\\d|[0-1]?\\d?\\d)(\\.(25[0-5]|2[0-4]\\d|[0-1]?\\d?\\d)){3}$");
    public static int HTTP_STREAMER_PORT = 49159; //5001;
    public static final int LOCK_TIMEOUT = 5000;
    protected UpnpService upnpService;
    protected UpnpServiceConfiguration upnpServiceCfg;
    protected IBinder binder = new MediaServerServiceBinder();
    private LocalService<ContentDirectory> contentDirectoryService;
    protected static MediaServerService INSTANCE;
    private boolean initialized;
   // private JLHttpStreamerServer server;
   private HCHttpStreamerServer server;

    public static void startMediaServer(Application application) {
        application.startForegroundService(new Intent(application, MediaServerService.class));
    }

    public static void stopMediaServer(Application application) {
        application.stopService(new Intent(application, MediaServerService.class));
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
    private void initialize() {
        this.initialized = false;
        shutdown(); // clean up before start

        upnpServiceCfg = new MediaServerConfiguration();
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
        createMediaServerDevice();
        createHttpStreamerServer();
        MediaServerService.this.initialized = true;
    }

    /**
     * creates a http request thread
     */
    private void createHttpStreamerServer() {
        String bindAddress = getIpAddress();
        Log.i(TAG, "Adding http streamer connector: " + bindAddress + ":" + HTTP_STREAMER_PORT);
        // Create a HttpService for providing content in the network.

        /*
        if (server == null) {
            try {
                server = new JLHttpStreamerServer(getApplicationContext(), bindAddress, HTTP_STREAMER_PORT);
                server.start();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        } */

        if(server == null) {
            try {
                server = new HCHttpStreamerServer(getApplicationContext(), bindAddress, HTTP_STREAMER_PORT);
                server.start();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    }

    private void createMediaServerDevice() {
        String versionName;
        String mediaServerUuid;
        // when the service starts, the preferences are initialized
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
        mediaServerUuid = preferences.getString(Constants.PREF_MEDIA_SERVER_UUID_KEY, null);
        if (mediaServerUuid == null) {
            mediaServerUuid = UUID.randomUUID().toString();
            preferences.edit().putString(Constants.PREF_MEDIA_SERVER_UUID_KEY, mediaServerUuid).apply();
        }
        Log.d(TAG, "Create MediaServer with ID: " + mediaServerUuid);
        try {
            versionName = getApplicationContext().getPackageManager().getPackageInfo(getApplicationContext().getPackageName(), 0).versionName;
        } catch (PackageManager.NameNotFoundException ex) {
            Log.e(TAG, "Error while creating device", ex);
            versionName = "??";
        }
        try {
            DeviceDetails msDetails = new DeviceDetails(
                    "MusicMate Server ("+getPhoneModel()+")", new ManufacturerDetails("MusicMate",
                    "http://www.apincer.com"), new ModelDetails("MusicMate Server", "DLNA/UPnP MediaServer",
                    versionName), URI.create("http://" + getIpAddress() + ":" + HTTP_STREAMER_PORT));

            DeviceIdentity identity = new DeviceIdentity(new UDN(mediaServerUuid), MIN_ADVERTISEMENT_AGE_SECONDS);

            LocalDevice localServer = new LocalDevice(identity, new UDADeviceType("MediaServer"), msDetails, createDeviceIcons(), createMediaServerServices());
            upnpService.getRegistry().addDevice(localServer);
        } catch (ValidationException e) {
            Log.e(TAG, "Exception during device creation", e);
           // Log.e(TAG, "Exception during device creation Errors:" + e.getErrors());
            throw new IllegalStateException("Exception during device creation", e);
        }
    }

    private String getPhoneModel() {
        return StringUtils.trimToEmpty(Build.MODEL);
    }

    private void showNotification() {
        ((MusixMateApp) getApplicationContext()).createGroupNotification();
        Intent notificationIntent = new Intent(this, MainActivity.class);
        PendingIntent contentIntent = PendingIntent.getActivity(this, 0, notificationIntent, PendingIntent.FLAG_IMMUTABLE);
        NotificationCompat.Builder mBuilder = new NotificationCompat.Builder(this, MusixMateApp.NOTIFICATION_CHANNEL_ID)
                .setOngoing(true)
                .setSmallIcon(R.drawable.ic_notification_default)
                .setSilent(true)
                .setContentTitle("MusicMate ("+getPhoneModel()+")")
                .setGroup(MusixMateApp.NOTIFICATION_GROUP_KEY)
                .setContentText(getApplicationContext().getString(R.string.media_server_name));
        mBuilder.setContentIntent(contentIntent);
        startForeground(NotificationId.MEDIA_SERVER.getId(), mBuilder.build());
    }

    private Icon[] createDeviceIcons() {
        ArrayList<Icon> icons = new ArrayList<>();
        icons.add(new Icon("image/png", 32, 32, 24, "musicmate32.png", getIconAsByteArray("iconpng32.png")));
        icons.add(new Icon("image/png", 48, 48, 24, "musicmate48.png", getIconAsByteArray("iconpng48.png")));
        icons.add(new Icon("image/png", 120, 120, 24, "musicmate120.png", getIconAsByteArray("iconpng120.png")));
        icons.add(new Icon("image/png", 192, 192, 24, "musicmate192.png", getIconAsByteArray("iconpng192.png")));
        return icons.toArray(new Icon[]{});
    }

    private byte[] getIconAsByteArray(String iconFile) {
        try {
            InputStream in = ApplicationUtils.getAssetsAsStream(getApplicationContext(), iconFile);
            if(in != null) {
                return IOUtils.toByteArray(in);
            }
        } catch (IOException ex) {
            Log.e("getIconAsByteArray", "cannot get icon file - "+iconFile, ex);
        }
        return null;
    }

    private LocalService<?>[] createMediaServerServices() {
        List<LocalService<?>> services = new ArrayList<>();
        services.add(createContentDirectoryService());
        services.add(createServerConnectionManagerService());
        services.add(createMediaReceiverRegistrarService());
        return services.toArray(new LocalService[]{});
    }

    private LocalService<AbstractMediaReceiverRegistrarService> createMediaReceiverRegistrarService() {
        LocalService<AbstractMediaReceiverRegistrarService> service = new AnnotationLocalServiceBinder()
                .read(AbstractMediaReceiverRegistrarService.class);
        service.setManager(new DefaultServiceManager<>(service, null) {

            @Override
            protected int getLockTimeoutMillis() {
                return LOCK_TIMEOUT;
            }

            @Override
            protected AbstractMediaReceiverRegistrarService createServiceInstance() {
                return new MediaReceiverRegistrarService();
            }
        });
        return service;
    }
    
    private LocalService<ConnectionManagerService>  createServerConnectionManagerService() {
        LocalService<ConnectionManagerService> service = new AnnotationLocalServiceBinder().read(ConnectionManagerService.class);
        final ProtocolInfos sourceProtocols = getSourceProtocolInfos();

        service.setManager(new DefaultServiceManager<>(service, ConnectionManagerService.class) {

            @Override
            protected int getLockTimeoutMillis() {
                return LOCK_TIMEOUT;
            }

            @Override
            protected ConnectionManagerService createServiceInstance() {
                return new ConnectionManagerService(sourceProtocols, null);
            }
        });

        return service;
    }

    private ProtocolInfos getSourceProtocolInfos() {
        return new ProtocolInfos(
                new ProtocolInfo("http-get:*:audio:*"),
                new ProtocolInfo("http-get:*:audio/aac:*"), // added by thawee
                new ProtocolInfo("http-get:*:audio/mpeg:*"),
                new ProtocolInfo("http-get:*:audio/x-mpegurl:*"),
                new ProtocolInfo("http-get:*:audio/x-wav:*"),
                new ProtocolInfo("http-get:*:audio/mpeg:DLNA.ORG_PN=MP3"),
                new ProtocolInfo("http-get:*:audio/mp4:DLNA.ORG_PN=AAC_ISO"),
                new ProtocolInfo("http-get:*:audio/x-flac:*"),
                new ProtocolInfo("http-get:*:audio/x-aiff:*"),
                new ProtocolInfo("http-get:*:audio/x-ogg:*"),
                new ProtocolInfo("http-get:*:audio/wav:*"),
                new ProtocolInfo("http-get:*:audio/wave:*"),
                new ProtocolInfo("http-get:*:audio/x-ape:*"),
                new ProtocolInfo("http-get:*:audio/x-m4a:*"),
                new ProtocolInfo("http-get:*:audio/x-mp4:*"), // added by thawee
                new ProtocolInfo("http-get:*:audio/x-m4b:*"),
                new ProtocolInfo("http-get:*:audio/basic:*"),
              //  new ProtocolInfo("http-get:*:audio/L16;rate=11025;channels=2:DLNA.ORG_PN=LPCM"),
              //  new ProtocolInfo("http-get:*:audio/L16;rate=22050;channels=2:DLNA.ORG_PN=LPCM"),
                new ProtocolInfo("http-get:*:audio/L16;rate=44100;channels=2:DLNA.ORG_PN=LPCM"),
                new ProtocolInfo("http-get:*:audio/L16;rate=48000;channels=2:DLNA.ORG_PN=LPCM"),
                new ProtocolInfo("http-get:*:audio/L16;rate=88200;channels=2:DLNA.ORG_PN=LPCM"),
                new ProtocolInfo("http-get:*:audio/L16;rate=96000;channels=2:DLNA.ORG_PN=LPCM"),
                new ProtocolInfo("http-get:*:audio/L16;rate=192000;channels=2:DLNA.ORG_PN=LPCM"),
                new ProtocolInfo(Protocol.HTTP_GET, ProtocolInfo.WILDCARD, "audio/mpeg", "DLNA.ORG_PN=MP3;DLNA.ORG_OP=01"),
                new ProtocolInfo("http-get:*:audio/mpeg:DLNA.ORG_PN=MP3"),
                new ProtocolInfo("http-get:*:audio/mpeg:DLNA.ORG_PN=MP3X"),
                new ProtocolInfo("http-get:*:image/jpeg:*"),
                new ProtocolInfo("http-get:*:image/png:*"),
                new ProtocolInfo("http-get:*:image/jpeg:DLNA.ORG_PN=JPEG_LRG"),
                new ProtocolInfo("http-get:*:image/jpeg:DLNA.ORG_PN=JPEG_MED"),
                new ProtocolInfo("http-get:*:image/jpeg:DLNA.ORG_PN=JPEG_SM"),
                new ProtocolInfo("http-get:*:image/jpeg:DLNA.ORG_PN=JPEG_TN"));
    }

    private LocalService<?> createContentDirectoryService() {
        contentDirectoryService = new AnnotationLocalServiceBinder().read(ContentDirectory.class);
        contentDirectoryService.setManager(new DefaultServiceManager<>(contentDirectoryService, null) {

            @Override
            protected int getLockTimeoutMillis() {
                return LOCK_TIMEOUT;
            }

            @Override
            protected ContentDirectory createServiceInstance() {
                return new ContentDirectory(getApplicationContext(), getIpAddress(), HTTP_STREAMER_PORT);
            }
        });
        return contentDirectoryService;
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
        if(server != null) {
            server.stop();
            server = null;
        }
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

    private static class MediaReceiverRegistrarService extends AbstractMediaReceiverRegistrarService {
    }

}
