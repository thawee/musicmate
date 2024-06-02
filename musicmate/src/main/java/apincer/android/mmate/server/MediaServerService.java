package apincer.android.mmate.server;

import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Binder;
import android.os.IBinder;
import android.util.Log;

import androidx.core.app.NotificationCompat;

import org.jupnp.UpnpServiceConfiguration;
import org.jupnp.UpnpServiceImpl;
import org.jupnp.android.AndroidUpnpServiceConfiguration;
import org.jupnp.model.ValidationException;
import org.jupnp.model.resource.Resource;
import org.jupnp.protocol.ProtocolFactory;
import org.jupnp.registry.Registry;

import java.io.IOException;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.net.URISyntaxException;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

import apincer.android.mmate.Constants;
import apincer.android.mmate.MusixMateApp;
import apincer.android.mmate.NotificationId;
import apincer.android.mmate.R;
import apincer.android.mmate.server.media.ContentTree;
import apincer.android.mmate.server.media.ExternalUrls;
import apincer.android.mmate.ui.MainActivity;
import apincer.android.mmate.utils.ExecutorHelper;
import apincer.android.mmate.utils.NetHelper;

public class MediaServerService extends Service {
    static MediaServerService INSTANCE;
    private static final int HTTP_CONTENT_PORT =8192;
   // private HTTPServer httpServer;
    final MyUpnpService upnpService = new MyUpnpService(new MusicMateUpnpServiceConfiguration());
    //private final List<InetAddress> addressesToBind = new ArrayList<>();  // Named this way cos NetworkAddressFactoryImpl has a bindAddresses field.
    private final Map<String, Resource<?>> registryPathToRes = new HashMap<>();

    private static final Pattern IPV4_PATTERN =
            Pattern.compile(
                    "^(25[0-5]|2[0-4]\\d|[0-1]?\\d?\\d)(\\.(25[0-5]|2[0-4]\\d|[0-1]?\\d?\\d)){3}$");

    protected IBinder binder = new MediaServerService.MusicServerServiceBinder();

    public static MediaServerService getInstance() {
        return INSTANCE;
    }

    /*
     * (non-Javadoc)
     *
     * @see android.app.Service#onBind(android.content.Intent)
     */
    @Override
    public IBinder onBind(Intent intent) {
        Log.d(this.getClass().getName(), "On Bind MusicMate Server Service");
        // do nothing
        return binder;
    }

    /*
     * (non-Javadoc)
     *
     * @see android.app.Service#onStart(android.content.Intent, int)
     */
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        long start = System.currentTimeMillis();
        // the footprint of the onStart() method must be small
        // otherwise android will kill the service
        // in order of this circumstance we have to initialize the service
        // asynchronous
        try {
            Thread initializationThread = new Thread(this::initialize);
            initializationThread.start();
            showNotification();
            INSTANCE = this;
            Log.d(this.getClass().getName(), "End On Start MusicMate Server Service");
            Log.d(this.getClass().getName(), "on start took: " + (System.currentTimeMillis() - start));
        }catch (Exception ex) {
            ex.printStackTrace();
        }
        return START_STICKY;
    }

   /* private void createHttpServer() {
        try {
            httpServer = new HTTPServer(HTTP_CONTENT_PORT);
            HTTPServer.VirtualHost host = httpServer.getVirtualHost(null); // default host
            host.setAllowGeneratedIndex(false);
            host.setDirectoryIndex(null); // disable auto suffix index.html
            host.addContext("/music/", new DAVContextHandler(getApplicationContext()), "GET","OPTIONS","PROPFIND");
            host.addContext("/playlist/", new PlaylistContextHandler(getApplicationContext()));
            host.addContext("/file/", new FileContextHandler(getApplicationContext()));
            httpServer.start();
        } catch (Exception e) {
            System.err.println("error: " + e);
        }
    }*/

    @Override
    public void onDestroy() {
        Log.d(this.getClass().getName(), "Destroying the MusicMate Server Service");
       /* if (httpServer != null) {
            httpServer.stop();
            httpServer = null;
        }*/
        if(upnpService !=null) {
            upnpService.shutdown();
        }
        cancelNotification();
        super.onDestroy();
    }

    /**
     *
     */
    private void initialize()  {
       // createHttpServer();
        try {
            createDLNAServer();
        } catch (IOException | ValidationException | URISyntaxException e) {
            throw new RuntimeException(e);
        }
    }

    private void createDLNAServer() throws IOException, ValidationException, URISyntaxException {
        ContentTree contentTree = new ContentTree();
        DLNASystemId systemId = new DLNASystemId();
        final String hostName = InetAddress.getLocalHost().getHostName();
        final InetAddress selfAddress = NetHelper.guessSelfAddress();

        final ExternalUrls externalUrls = new ExternalUrls(selfAddress, HTTP_CONTENT_PORT);

        final NodeConverter nodeConverter = new NodeConverter(externalUrls);

        boolean printAccessLog = true;
        upnpService.startup();
        upnpService.getRegistry().addDevice(new MediaServer(systemId, contentTree, nodeConverter, hostName, printAccessLog, externalUrls.getSelfUri()).getDevice());

        // Periodic rescan to catch missed devices.
        final ScheduledExecutorService upnpExSvc = ExecutorHelper.newScheduledExecutor(1, "upnp");
        upnpExSvc.scheduleWithFixedDelay(() -> {
            upnpService.getControlPoint().search();
           // if (args.isVerboseLog()) LOG.info("Scanning for devices.");
        }, 0, Constants.DEVICE_SEARCH_INTERVAL_MINUTES, TimeUnit.MINUTES);

    }

    /**
     * Displays the notification.
     */
    private void showNotification() {
        ((MusixMateApp) getApplicationContext()).createGroupNotification();
        Intent notificationIntent = new Intent(this, MainActivity.class);
        PendingIntent contentIntent = PendingIntent.getActivity(this, 0, notificationIntent, PendingIntent.FLAG_IMMUTABLE);
        NotificationCompat.Builder mBuilder = new NotificationCompat.Builder(this, MusixMateApp.NOTIFICATION_CHANNEL_ID)
                .setOngoing(true)
                .setSmallIcon(R.drawable.ic_notification_default)
                .setSilent(true)
                .setContentTitle("MusicMate Server")
                .setGroup(MusixMateApp.NOTIFICATION_GROUP_KEY)
                .setContentText(getApplicationContext().getString(R.string.settings_local_server_name));
        mBuilder.setContentIntent(contentIntent);
        startForeground(NotificationId.MEDIA_SERVER.getId(), mBuilder.build());

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
            Log.d(MediaServerService.class.getName(),
                    "Error while retrieving network interfaces", se);
        }
        // maybe wifi is off we have to use the loopback device
        hostAddress = hostAddress == null ? "0.0.0.0" : hostAddress;
        return hostAddress;
    }

    public boolean isStarted() {
        if(upnpService != null) {
            return true;
        }
        return false;
    }


    public class MusicServerServiceBinder extends Binder {
        public MediaServerService getService() {
            return MediaServerService.this;
        }
    }


    private class MyUpnpService extends UpnpServiceImpl {

        private MyUpnpService(final UpnpServiceConfiguration configuration) {
            super(configuration);
        }

        @Override
        protected Registry createRegistry (final ProtocolFactory pf) {
            return new RegistryImplWithOverrides(this, MediaServerService.this.registryPathToRes);
        }
    }

   private static class MusicMateUpnpServiceConfiguration extends AndroidUpnpServiceConfiguration {

       /* @Override
        protected NetworkAddressFactory createNetworkAddressFactory(final int streamListenPort, final int multicastResponsePort) {
            return new AndroidNetworkAddressFactory(streamListenPort, multicastResponsePort);
        } */

        // private final ServletContainerAdapter jettyAdaptor = new MyJettyServletContainer();

        // Workaround for https://github.com/jupnp/jupnp/issues/225
        // TODO remove this override once it is fixed.
        /*@Override
        public StreamServer createStreamServer(final NetworkAddressFactory networkAddressFactory) {
              return new MusicMateStreamServer(getInstance().upnpService.getProtocolFactory(), new MusicMateStreamServerConfiguration(networkAddressFactory.getStreamListenPort()));
        }*/

        // Workaround for jupnp not being compatible with Jetty 10.
        // TODO remove this and the edited classes when jupnp uses Jetty 10.
        /*@Override
        public StreamClient createStreamClient() {
            // values from org.jupnp.transport.spi.AbstractStreamClientConfiguration.
            MusicMateStreamClientConfiguration clientConfiguration = new MusicMateStreamClientConfiguration(
                    getSyncProtocolExecutorService());

            return new MusicMateStreamClient(clientConfiguration);
        }*/
    }
}
