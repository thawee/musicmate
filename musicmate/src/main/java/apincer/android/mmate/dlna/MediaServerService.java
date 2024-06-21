package apincer.android.mmate.dlna;

import static org.jupnp.model.Constants.MIN_ADVERTISEMENT_AGE_SECONDS;

import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.drawable.AdaptiveIconDrawable;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.os.IBinder;
import android.util.Log;

import androidx.core.app.NotificationCompat;
import androidx.core.content.res.ResourcesCompat;
import androidx.preference.PreferenceManager;

import org.apache.commons.io.IOUtils;
import org.apache.hc.core5.http.URIScheme;
import org.apache.hc.core5.http.impl.bootstrap.HttpAsyncServer;
import org.apache.hc.core5.http2.impl.nio.bootstrap.H2ServerBootstrap;
import org.apache.hc.core5.reactor.IOReactorConfig;
import org.apache.hc.core5.util.TimeValue;
import org.jupnp.UpnpService;
import org.jupnp.UpnpServiceImpl;
import org.jupnp.android.AndroidUpnpServiceConfiguration;
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
import org.jupnp.model.types.ServiceType;
import org.jupnp.model.types.UDADeviceType;
import org.jupnp.model.types.UDAServiceType;
import org.jupnp.model.types.UDN;
import org.jupnp.support.connectionmanager.ConnectionManagerService;
import org.jupnp.support.model.Protocol;
import org.jupnp.support.model.ProtocolInfo;
import org.jupnp.support.model.ProtocolInfos;
import org.jupnp.support.xmicrosoft.AbstractMediaReceiverRegistrarService;
import org.jupnp.transport.spi.NetworkAddressFactory;
import org.jupnp.transport.spi.StreamClient;
import org.jupnp.transport.spi.StreamServer;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.net.URI;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

import apincer.android.mmate.MusixMateApp;
import apincer.android.mmate.NotificationId;
import apincer.android.mmate.R;
import apincer.android.mmate.ui.MainActivity;
import apincer.android.mmate.utils.ApplicationUtils;

public class MediaServerService extends Service {
    private static final Pattern IPV4_PATTERN =
            Pattern.compile(
                    "^(25[0-5]|2[0-4]\\d|[0-1]?\\d?\\d)(\\.(25[0-5]|2[0-4]\\d|[0-1]?\\d?\\d)){3}$");
    public static int SERVER_PORT = 5001;
    public static int SSDP_PORT = 2869;

    public static final int LOCK_TIMEOUT = 5000;
    protected UpnpService upnpService;
    protected IBinder binder = new UpnpRegistryServiceBinder();
    private LocalService<MusicMateContentDirectory> contentDirectoryService;
    private boolean watchdog;
    protected static MediaServerService INSTANCE;
    private boolean initialized;
    private LocalDevice localServer;
    private HttpAsyncServer httpServer;

    public static boolean isServerStarted() {
        if(INSTANCE != null) {
            return INSTANCE.isInitialized();
        }
        return false;
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
        long start = System.currentTimeMillis();
        super.onCreate();
        INSTANCE = this;
        upnpService = new UpnpServiceImpl(new MusicMateServiceConfiguration());

        Log.d(this.getClass().getName(), "on start took: " + (System.currentTimeMillis() - start));
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        long start = System.currentTimeMillis();
        showNotification();
        // the footprint of the onStart() method must be small
        // otherwise android will kill the service
        // in order of this circumstance we have to initialize the service
        // asynchronous
        Thread initializationThread = new Thread(this::initialize);
        initializationThread.start();
        Log.d(this.getClass().getName(), "End On Start"); 

        
        Log.d(this.getClass().getName(), "on start took: " + (System.currentTimeMillis() - start));
        return START_STICKY;
    }

    /**
     *
     */
    private void initialize() {
        this.initialized = false;
        upnpService.startup();
        createHttpServer();
        createMediaServer();
        this.initialized = true;
    }

    /**
     * creates a http request thread
     */
    private void createHttpServer() {
        // Create a HttpService for providing content in the network.
        //FIXME set correct timeout
       // SocketConfig socketConfig = SocketConfig.custom()
       //         .setSoKeepAlive(true)
       //         .setTcpNoDelay(false)
       //         .build();

        // Set up the HTTP service
        if (httpServer == null) {
            String bindAddress = getIpAddress();
            IOReactorConfig config = IOReactorConfig.custom()
                    .setSoKeepAlive(true)
                    .setTcpNoDelay(true)
                    .setSoTimeout(60, TimeUnit.SECONDS)
                    .build();
            Log.d(getClass().getName(), "Adding present connector: " + bindAddress + ":" + SERVER_PORT);

            httpServer = H2ServerBootstrap.bootstrap()
           // httpServer = AsyncServerBootstrap.bootstrap()
                    .setIOReactorConfig(config)
                    .setCanonicalHostName(bindAddress)
                    .register("*", new MediaServerServiceHttpHandler(getApplicationContext()))
                    .create();

            httpServer.listen(new InetSocketAddress(SERVER_PORT), URIScheme.HTTP);
            httpServer.start();
        }
    }

    private void createMediaServer() {
        String versionName;
        String mediaServerUuid = "";
        // when the service starts, the preferences are initialized
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
        mediaServerUuid = preferences.getString(getApplicationContext().getString(R.string.settings_local_server_provider_uuid_key), null);
        if (mediaServerUuid == null) {
            mediaServerUuid = UUID.randomUUID().toString();
            preferences.edit().putString(getApplicationContext().getString(R.string.settings_local_server_provider_uuid_key), mediaServerUuid).apply();
        }
        Log.d(this.getClass().getName(), "Create MediaServer with ID: " + mediaServerUuid);
        try {
            versionName = getApplicationContext().getPackageManager().getPackageInfo(getApplicationContext().getPackageName(), 0).versionName;
        } catch (PackageManager.NameNotFoundException ex) {
            Log.e(this.getClass().getName(), "Error while creating device", ex);
            versionName = "??";
        }
        try {
            DeviceDetails msDetails = new DeviceDetails(
                    "MusicMate-UPnP/AV Server", new ManufacturerDetails("Apincer",
                    "http://www.apincer.com"), new ModelDetails("MusicMate", "UPnP/AV MediaServer",
                    versionName), URI.create("http://" + getIpAddress() + ":" + SERVER_PORT));

            DeviceIdentity identity = new DeviceIdentity(new UDN(mediaServerUuid), MIN_ADVERTISEMENT_AGE_SECONDS);

            localServer = new LocalDevice(identity, new UDADeviceType("MediaServer"), msDetails, createDeviceIcons(), createMediaServerServices());
            upnpService.getRegistry().addDevice(localServer);
        } catch (ValidationException e) {
            Log.e(this.getClass().getName(), "Exception during device creation", e);
            Log.e(this.getClass().getName(), "Exception during device creation Errors:" + e.getErrors());
            throw new IllegalStateException("Exception during device creation", e);
        }
    }

    private void showNotification() {
        ((MusixMateApp) getApplicationContext()).createGroupNotification();
        Intent notificationIntent = new Intent(this, MainActivity.class);
        PendingIntent contentIntent = PendingIntent.getActivity(this, 0, notificationIntent, PendingIntent.FLAG_IMMUTABLE);
        NotificationCompat.Builder mBuilder = new NotificationCompat.Builder(this, MusixMateApp.NOTIFICATION_CHANNEL_ID)
                .setOngoing(true)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setSilent(true)
                .setContentTitle("MusicMate")
                .setGroup(MusixMateApp.NOTIFICATION_GROUP_KEY)
                .setContentText(getApplicationContext().getString(R.string.settings_local_server_name));
        mBuilder.setContentIntent(contentIntent);
        startForeground(NotificationId.MEDIA_SERVER.getId(), mBuilder.build());

    }

    private String getLocalServerName() {
        return "MusicMate";
    }

    private Icon[] createDeviceIcons() {
        ArrayList<Icon> icons = new ArrayList<>();
        byte[] icon = getIconAsByteArray();
        if(icon != null) {
            icons.add(new Icon("image/png", 32, 32, 24, "musicmate.png", icon));
            icons.add(new Icon("image/png", 64, 64, 24, "musicmate64.png", icon));
        }
        return icons.toArray(new Icon[]{});
    }

    private byte[] getIconAsByteArray() {
        try {
            InputStream in = ApplicationUtils.getAssetsAsStream(getApplicationContext(), "icon.png");
            return IOUtils.toByteArray(in);
        } catch (IOException ex) {
            ex.printStackTrace();
        }
        return null;
    }

    private byte[] getIconAsByteArray(int drawableId, Bitmap.CompressFormat format) {

        Drawable drawable = ResourcesCompat.getDrawable(getResources(), drawableId, getTheme());
        byte[] result = null;
        if (drawable != null) {
            if(drawable instanceof  BitmapDrawable) {
                Bitmap bitmap = ((BitmapDrawable) drawable).getBitmap();
                ByteArrayOutputStream stream = new ByteArrayOutputStream();
                bitmap.compress(format, 100, stream);
                result = stream.toByteArray();
            }else if (drawable instanceof AdaptiveIconDrawable) {
              /*  Bitmap bitmap = ((AdaptiveIconDrawable) drawable).
                ByteArrayOutputStream stream = new ByteArrayOutputStream();
                bitmap.compress(format, 100, stream);
                result = stream.toByteArray(); */
            }
        }
        return result;
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
                new ProtocolInfo("http-get:*:audio/mpeg:*"),
                new ProtocolInfo("http-get:*:audio/x-mpegurl:*"),
                new ProtocolInfo("http-get:*:audio/x-wav:*"),
                new ProtocolInfo("http-get:*:audio/mpeg:DLNA.ORG_PN=MP3"),
                new ProtocolInfo("http-get:*:audio/mp4:DLNA.ORG_PN=AAC_ISO"),
                new ProtocolInfo("http-get:*:audio/x-flac:*"),
                new ProtocolInfo("http-get:*:audio/x-aiff:*"),
                new ProtocolInfo("http-get:*:audio/wav:*"),
                new ProtocolInfo("http-get:*:audio/x-m4a:*"),
                new ProtocolInfo("http-get:*:audio/basic:*"),
                new ProtocolInfo("http-get:*:audio/L16;rate=11025;channels=2:DLNA.ORG_PN=LPCM"),
                new ProtocolInfo("http-get:*:audio/L16;rate=22050;channels=2:DLNA.ORG_PN=LPCM"),
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
                new ProtocolInfo("http-get:*:image/jpeg:DLNA.ORG_PN=JPEG_TN"),
                new ProtocolInfo("http-get:*:image/x-ycbcr-yuv420:*"),
                new ProtocolInfo("http-get:*:video/mp4:*"),
                new ProtocolInfo("http-get:*:video/mpeg:*"),
                new ProtocolInfo("http-get:*:video/x-flc:*"));
    }

    private LocalService<?> createContentDirectoryService() {
        contentDirectoryService = new AnnotationLocalServiceBinder().read(MusicMateContentDirectory.class);
        contentDirectoryService.setManager(new DefaultServiceManager<>(contentDirectoryService, null) {

            @Override
            protected int getLockTimeoutMillis() {
                return LOCK_TIMEOUT;
            }

            @Override
            protected MusicMateContentDirectory createServiceInstance() {
                return new MusicMateContentDirectory(getApplicationContext(), getIpAddress());
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
        if(httpServer != null) {
            httpServer.initiateShutdown();
            try {
                httpServer.awaitShutdown(TimeValue.ofSeconds(3));
            } catch (InterruptedException e) {
                Log.w(getClass().getName(), "got exception on stream server stop ", e);
            }
            httpServer = null;
        }

        upnpService.getRegistry().removeAllLocalDevices();

        initialized = false;
        cancelNotification();
        super.onDestroy();
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
            Log.d(MediaServerService.class.getName(),
                    "Error while retrieving network interfaces", se);
        }
        // maybe wifi is off we have to use the loopback device
        hostAddress = hostAddress == null ? "0.0.0.0" : hostAddress;
        return hostAddress;
    }

    protected class UpnpRegistryServiceBinder extends android.os.Binder {
        public MediaServerService getService() {
            return MediaServerService.this;
        }
    }

    private static class MusicMateServiceConfiguration extends AndroidUpnpServiceConfiguration {

        private MusicMateServiceConfiguration() {
            super();
        }

        @Override
        protected NetworkAddressFactory createNetworkAddressFactory(int streamListenPort, int multicastResponsePort) {
            return super.createNetworkAddressFactory(SSDP_PORT, multicastResponsePort);
        }

        @Override
        public ServiceType[] getExclusiveServiceTypes() {

            return new ServiceType[]{new UDAServiceType("ContentDirectory"), new UDAServiceType("ConnectionManager"), new UDAServiceType("X_MS_MediaReceiverRegistrar")};
        }

        @Override
        public StreamServer<MusicMateStreamServerConfigurationImpl> createStreamServer(NetworkAddressFactory networkAddressFactory) {
            //return new MusicMateStreamServerImpl( new MusicMateStreamServerConfigurationImpl(SSDP_PORT)
            return new MusicMateStreamServerImpl( new MusicMateStreamServerConfigurationImpl(networkAddressFactory.getStreamListenPort())
            );
        }

        @Override
        public StreamClient<MusicMateStreamingClientConfigurationImpl> createStreamClient() {
            return new MusicMateStreamingClientImpl(
                    new MusicMateStreamingClientConfigurationImpl(
                            getSyncProtocolExecutorService()
                    )
            );
        }
    }
}
