package apincer.android.mmate.dlna;

import android.app.Application;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
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
  //  private LocalService<ContentDirectory> contentDirectoryService;
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
/*
    private LocalDevice createMediaServerDevice() {
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
            String friendlyName = "MusicMate Server ["+getDeviceModel()+"]";
            ManufacturerDetails manufacturerDetails = new ManufacturerDetails("Thawee",
                    "https://github.com/thawee/musicmate");
            ModelDetails modelDetails = new ModelDetails("MusicMate",
                    "DLNA (UPnP/AV 1.0) Media Server, "+getDeviceDetails(),
                    versionName);
            URI presentationURI = null;
            if (!StringUtils.isEmpty(MediaServerSession.streamServerHost)) {
                String webInterfaceUrl = "http://" + MediaServerSession.streamServerHost + ":" + HCContentServer.SERVER_PORT +"/musicmate.html";
                presentationURI = URI.create(webInterfaceUrl);
            }
            DeviceDetails msDetails = new DeviceDetails(
                    null,
                    friendlyName,
                    manufacturerDetails,
                    modelDetails,
                    null,
                    null,
                    presentationURI,
                    DLNA_DOCS,
                    DLNA_CAPS); //,
                    //SEC_CAP);

            DeviceIdentity identity = new DeviceIdentity(new UDN(mediaServerUuid), MIN_ADVERTISEMENT_AGE_SECONDS);

            LocalDevice localServer = new LocalDevice(identity, new UDADeviceType("MediaServer"), msDetails, createDeviceIcons(), createMediaServerServices());
         //   upnpService.getRegistry().addDevice(localServer);
            // send live notification
           // upnpService.getProtocolFactory().createSendingNotificationAlive(localServer).run();
            return localServer;
        } catch (ValidationException e) {
            Log.e(TAG, "Exception during device creation", e);
           // Log.e(TAG, "Exception during device creation Errors:" + e.getErrors());
            throw new IllegalStateException("Exception during device creation", e);
        }
    } */

    private String getDeviceModel() {
        return StringUtils.trimToEmpty(Build.MODEL);
    }

    /*
    private String getDeviceDetails() {
        return "Android " +StringUtils.trimToEmpty(Build.VERSION.RELEASE) +" on "+StringUtils.trimToEmpty(Build.MANUFACTURER) +" "+  StringUtils.trimToEmpty(Build.MODEL)+".";
    } */

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

    /*
    private Icon[] createDeviceIcons() {
        ArrayList<Icon> icons = new ArrayList<>();
         icons.add(new Icon("image/png", 64, 64, 24, "musicmate.png", getIconAsByteArray("iconpng64.png")));
         icons.add(new Icon("image/png", 128, 128, 24, "musicmate128.png", getIconAsByteArray("iconpng128.png")));
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
    } */

    /*
    private LocalService<?>[] createMediaServerServices() {
        List<LocalService<?>> services = new ArrayList<>();
        services.add(createServerConnectionManagerService());
        services.add(createContentDirectoryService());
      //  services.add(createMediaReceiverRegistrarService());
        return services.toArray(new LocalService[]{});
    } */

    /*
    private LocalService<AbstractMediaReceiverRegistrarService> createMediaReceiverRegistrarService() {
        AnnotationLocalServiceBinder binder = new AnnotationLocalServiceBinder();
        LocalService<AbstractMediaReceiverRegistrarService> service = binder.read(AbstractMediaReceiverRegistrarService.class);
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
    } */

    /*
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
 */

    /*
    private ProtocolInfos getSourceProtocolInfos() {
        return new ProtocolInfos(
                //this one overlap all ???
               // new ProtocolInfo(Protocol.HTTP_GET, ProtocolInfo.WILDCARD, MimeType.WILDCARD, ProtocolInfo.WILDCARD),
                //this one overlap all images ???
                new ProtocolInfo(Protocol.HTTP_GET, ProtocolInfo.WILDCARD, "image/" + MimeType.WILDCARD, ProtocolInfo.WILDCARD),
                new ProtocolInfo(Protocol.HTTP_GET, ProtocolInfo.WILDCARD, "audio", ProtocolInfo.WILDCARD),
                //this one overlap all audio ???
                new ProtocolInfo(Protocol.HTTP_GET, ProtocolInfo.WILDCARD, "audio/" + MimeType.WILDCARD, ProtocolInfo.WILDCARD),

                //IMAGE
                new ProtocolInfo(Protocol.HTTP_GET, ProtocolInfo.WILDCARD, "image/jpeg", "DLNA.ORG_PN=JPEG_TN"),
                new ProtocolInfo(Protocol.HTTP_GET, ProtocolInfo.WILDCARD, "image/jpeg", "DLNA.ORG_PN=JPEG_SM"),
                new ProtocolInfo(Protocol.HTTP_GET, ProtocolInfo.WILDCARD, "image/jpeg", "DLNA.ORG_PN=JPEG_MED"),
                new ProtocolInfo(Protocol.HTTP_GET, ProtocolInfo.WILDCARD, "image/jpeg", "DLNA.ORG_PN=JPEG_LRG"),
                new ProtocolInfo(Protocol.HTTP_GET, ProtocolInfo.WILDCARD, "image/jpeg", "DLNA.ORG_PN=JPEG_RES_H_V"),
                new ProtocolInfo(Protocol.HTTP_GET, ProtocolInfo.WILDCARD, "image/png", "DLNA.ORG_PN=PNG_TN"),
                new ProtocolInfo(Protocol.HTTP_GET, ProtocolInfo.WILDCARD, "image/png", "DLNA.ORG_PN=PNG_LRG"),
               // new ProtocolInfo(Protocol.HTTP_GET, ProtocolInfo.WILDCARD, "image/gif", "DLNA.ORG_PN=GIF_LRG"),

                //AUDIO
                new ProtocolInfo(Protocol.HTTP_GET, ProtocolInfo.WILDCARD, "audio/mpeg", ProtocolInfo.WILDCARD),
                new ProtocolInfo(Protocol.HTTP_GET, ProtocolInfo.WILDCARD, "audio/mpeg", "DLNA.ORG_PN=MP3"),
                new ProtocolInfo(Protocol.HTTP_GET, ProtocolInfo.WILDCARD, "audio/wav", ProtocolInfo.WILDCARD),
                new ProtocolInfo(Protocol.HTTP_GET, ProtocolInfo.WILDCARD, "audio/wave", ProtocolInfo.WILDCARD),
                new ProtocolInfo(Protocol.HTTP_GET, ProtocolInfo.WILDCARD, "audio/x-wav", ProtocolInfo.WILDCARD),
                new ProtocolInfo(Protocol.HTTP_GET, ProtocolInfo.WILDCARD, "audio/flac", ProtocolInfo.WILDCARD),
                new ProtocolInfo(Protocol.HTTP_GET, ProtocolInfo.WILDCARD, "audio/x-flac", ProtocolInfo.WILDCARD),
                new ProtocolInfo(Protocol.HTTP_GET, ProtocolInfo.WILDCARD, "audio/x-aiff", ProtocolInfo.WILDCARD),
                new ProtocolInfo(Protocol.HTTP_GET, ProtocolInfo.WILDCARD, "audio/x-mp4", ProtocolInfo.WILDCARD), // alac
                new ProtocolInfo(Protocol.HTTP_GET, ProtocolInfo.WILDCARD, "audio/x-m4a", ProtocolInfo.WILDCARD), // aac
                new ProtocolInfo(Protocol.HTTP_GET, ProtocolInfo.WILDCARD, "audio/mp4", "DLNA.ORG_PN=AAC_ISO") // aac
              //  new ProtocolInfo(Protocol.HTTP_GET, ProtocolInfo.WILDCARD, "audio/L16;rate=44100;channels=2", "DLNA.ORG_PN=LPCM")
               // new ProtocolInfo(Protocol.HTTP_GET, ProtocolInfo.WILDCARD, "audio/L16", "DLNA.ORG_PN=LPCM"),
              //  new ProtocolInfo("http-get:*:audio/aac:*"), // added by thawee
               // new ProtocolInfo("http-get:*:audio/mpeg:*"),
              //  new ProtocolInfo("http-get:*:audio/x-mpegurl:*"),
              //  new ProtocolInfo("http-get:*:audio/x-wav:*"),
               // new ProtocolInfo("http-get:*:audio/mpeg:DLNA.ORG_PN=MP3"),
              //  new ProtocolInfo("http-get:*:audio/mp4:DLNA.ORG_PN=AAC_ISO"),
               // new ProtocolInfo("http-get:*:audio/x-flac:*"),
             //   new ProtocolInfo("http-get:*:audio/x-aiff:*"),
               // new ProtocolInfo("http-get:*:audio/x-ogg:*"),
               // new ProtocolInfo("http-get:*:audio/wav:*"),
              //  new ProtocolInfo("http-get:*:audio/wave:*"),
               // new ProtocolInfo("http-get:*:audio/x-ape:*"),
              //  new ProtocolInfo("http-get:*:audio/x-m4a:*"),
               // new ProtocolInfo("http-get:*:audio/x-mp4:*"), // added by thawee
               // new ProtocolInfo("http-get:*:audio/x-m4b:*"),
              //  new ProtocolInfo("http-get:*:audio/basic:*"),
              //  new ProtocolInfo("http-get:*:audio/L16;rate=11025;channels=2:DLNA.ORG_PN=LPCM"),
              //  new ProtocolInfo("http-get:*:audio/L16;rate=22050;channels=2:DLNA.ORG_PN=LPCM"),
               // new ProtocolInfo("http-get:*:audio/L16;rate=44100;channels=2:DLNA.ORG_PN=LPCM"),
              //  new ProtocolInfo("http-get:*:audio/L16;rate=48000;channels=2:DLNA.ORG_PN=LPCM"),
              //  new ProtocolInfo("http-get:*:audio/L16;rate=88200;channels=2:DLNA.ORG_PN=LPCM"),
              //  new ProtocolInfo("http-get:*:audio/L16;rate=96000;channels=2:DLNA.ORG_PN=LPCM"),
              //  new ProtocolInfo("http-get:*:audio/L16;rate=192000;channels=2:DLNA.ORG_PN=LPCM"));
               // new ProtocolInfo(Protocol.HTTP_GET, ProtocolInfo.WILDCARD, "audio/mpeg", "DLNA.ORG_PN=MP3;DLNA.ORG_OP=01"),
               // new ProtocolInfo("http-get:*:audio/mpeg:DLNA.ORG_PN=MP3"),
               // new ProtocolInfo("http-get:*:audio/mpeg:DLNA.ORG_PN=MP3X"),
               // new ProtocolInfo("http-get:*:image/jpeg:*"),
               // new ProtocolInfo("http-get:*:image/png:*"),
               // new ProtocolInfo("http-get:*:image/jpeg:DLNA.ORG_PN=JPEG_LRG"),
               // new ProtocolInfo("http-get:*:image/jpeg:DLNA.ORG_PN=JPEG_MED"),
              //  new ProtocolInfo("http-get:*:image/jpeg:DLNA.ORG_PN=JPEG_SM"),
               // new ProtocolInfo("http-get:*:image/jpeg:DLNA.ORG_PN=JPEG_TN"));
        );
    } */

    /*
    private LocalService<ContentDirectory> createContentDirectoryService() {
        contentDirectoryService = new AnnotationLocalServiceBinder().read(ContentDirectory.class);
        contentDirectoryService.setManager(new DefaultServiceManager<>(contentDirectoryService, null) {

            @Override
            protected int getLockTimeoutMillis() {
                return LOCK_TIMEOUT;
            }

            @Override
            protected ContentDirectory createServiceInstance() {
                return new ContentDirectory(getApplicationContext());
            }
        });
        return contentDirectoryService;
    } */


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

    /*
    private static class MediaReceiverRegistrarService extends AbstractMediaReceiverRegistrarService {
    } */

}
