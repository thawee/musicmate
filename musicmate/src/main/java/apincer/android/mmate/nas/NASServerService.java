package apincer.android.mmate.nas;

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

public class NASServerService extends Service {
    private static final int WEBDAV_PORT = 8082;
    private HTTPServer httpServer;

    private static final Pattern IPV4_PATTERN =
            Pattern.compile(
                    "^(25[0-5]|2[0-4]\\d|[0-1]?\\d?\\d)(\\.(25[0-5]|2[0-4]\\d|[0-1]?\\d?\\d)){3}$");

    protected IBinder binder = new NASServerService.MusicServerServiceBinder();

    /*
     * (non-Javadoc)
     *
     * @see android.app.Service#onBind(android.content.Intent)
     */
    @Override
    public IBinder onBind(Intent intent) {
        Log.d(this.getClass().getName(), "On Bind NAS service");
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
            Log.d(this.getClass().getName(), "End On Start NAS service");
            Log.d(this.getClass().getName(), "on start took: " + (System.currentTimeMillis() - start));
        }catch (Exception ex) {
            ex.printStackTrace();
        }
        return START_STICKY;
    }

    private void createHttpServer() {
        try {
            httpServer = new HTTPServer(WEBDAV_PORT);
            HTTPServer.VirtualHost host = httpServer.getVirtualHost(null); // default host
            host.setAllowGeneratedIndex(false);
            host.setDirectoryIndex(null); // disable auto suffix index.html
            host.addContext("/music/", new WebDAVContextHandler(getApplicationContext()), "GET","OPTIONS","PROPFIND");
            httpServer.start();
        } catch (Exception e) {
            System.err.println("error: " + e);
        }
    }

    @Override
    public void onDestroy() {
        Log.d(this.getClass().getName(), "Destroying the NAS service");
        if (httpServer != null) {
            httpServer.stop();
            httpServer = null;
        }
        cancelNotification();
        super.onDestroy();
    }

    /**
     *
     */
    private void initialize()  {
        createHttpServer();
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
            Log.d(apincer.android.mmate.nas.NASServerService.class.getName(),
                    "Error while retrieving network interfaces", se);
        }
        // maybe wifi is off we have to use the loopback device
        hostAddress = hostAddress == null ? "0.0.0.0" : hostAddress;
        return hostAddress;
    }


    public class MusicServerServiceBinder extends Binder {
        public NASServerService getService() {
            return NASServerService.this;
        }
    }
}
