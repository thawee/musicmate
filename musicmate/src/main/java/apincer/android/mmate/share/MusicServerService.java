package apincer.android.mmate.share;

import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Binder;
import android.os.IBinder;
import android.util.Log;

import androidx.core.app.NotificationCompat;

import net.freeutils.httpserver.HTTPServer;

import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.Enumeration;
import java.util.regex.Pattern;

import apincer.android.mmate.MusixMateApp;
import apincer.android.mmate.NotificationId;
import apincer.android.mmate.R;
import apincer.android.mmate.ui.MainActivity;

public class MusicServerService extends Service {
    public static int PORT = 49152;
    private static final Pattern IPV4_PATTERN =
            Pattern.compile(
                    "^(25[0-5]|2[0-4]\\d|[0-1]?\\d?\\d)(\\.(25[0-5]|2[0-4]\\d|[0-1]?\\d?\\d)){3}$");

    private HTTPServer httpServer = null;
    protected IBinder binder = new MusicServerServiceBinder();

    /*
     * (non-Javadoc)
     *
     * @see android.app.Service#onBind(android.content.Intent)
     */
    @Override
    public IBinder onBind(Intent intent) {
        Log.d(this.getClass().getName(), "On Bind");
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
        Thread initializationThread = new Thread(this::initialize);
        initializationThread.start();
        showNotification();
        Log.d(this.getClass().getName(), "End On Start");
        Log.d(this.getClass().getName(), "on start took: " + (System.currentTimeMillis() - start));
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        Log.d(this.getClass().getName(), "Destroying the service");
        if (httpServer != null) {
            httpServer.stop();
        }
        cancelNotification();
        super.onDestroy();
    }

    /**
     *
     */
    private void initialize() {
        createHttpServer();
    }

    private void createHttpServer() {
        try {
            httpServer = new HTTPServer(PORT);
            HTTPServer.VirtualHost host = httpServer.getVirtualHost(null); // default host
            host.setAllowGeneratedIndex(false);
            host.addContext("/", new MusicContextHandler(getApplicationContext()));
             httpServer.start();
        } catch (Exception e) {
            System.err.println("error: " + e);
        }
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
                .setContentTitle("Music Server")
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
     * creates a http request thread
     */
    /*
    private void createHttpServer() {
        // Create a HttpService for providing content in the network.
        try {

            //FIXME set correct timeout
            SocketConfig socketConfig = SocketConfig.custom()
                    .setSoKeepAlive(true)
                    .setTcpNoDelay(false)
                   // .setSoTimeout(10, TimeUnit.SECONDS)
                    .build();

            // Set up the HTTP service
            if (httpServer == null) {
                httpServer = ServerBootstrap.bootstrap()
                        .setListenerPort(PORT)
                        .setSocketConfig(socketConfig)
                        .setExceptionListener(new ExceptionListener() {

                            @Override
                            public void onError(final Exception ex) {
                                Log.i(getClass().getName(), "HttpServer throws exception:", ex);
                            }

                            @Override
                            public void onError(final HttpConnection conn, final Exception ex) {
                                if (ex instanceof SocketTimeoutException) {
                                    Log.i(getClass().getName(), "connection timeout:", ex);
                                } else if (ex instanceof ConnectionClosedException) {
                                    Log.i(getClass().getName(), "connection closed:", ex);
                                } else {
                                    Log.i(getClass().getName(), "connection error:", ex);
                                }
                            }

                        })
                        .setCanonicalHostName(getIpAddress())
                        .register("*", new MusicHttpHandler(getApplicationContext()))
                        .create();

                httpServer.start();
            }

        } catch (BindException e) {
            Log.w(this.getClass().getName(), "Server already running");
        } catch (IOException e) {
            // FIXME Ignored right error handling on rebind needed
            Log.w(this.getClass().getName(), "ContentProvider can not be initialized!", e);
            // throw new
            // IllegalStateException("ContentProvider can not be initialized!",
            // e);
        }
    } */

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
            Log.d(MusicServerService.class.getName(),
                    "Error while retrieving network interfaces", se);
        }
        // maybe wifi is off we have to use the loopback device
        hostAddress = hostAddress == null ? "0.0.0.0" : hostAddress;
        return hostAddress;
    }


    public class MusicServerServiceBinder extends Binder {
        public MusicServerService getService() {
            return MusicServerService.this;
        }
    }
}
