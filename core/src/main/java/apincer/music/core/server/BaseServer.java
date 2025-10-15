package apincer.music.core.server;

import static apincer.music.core.utils.StringUtils.isEmpty;
import static apincer.music.core.utils.StringUtils.trimToEmpty;

import android.annotation.SuppressLint;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Build;
import android.os.IBinder;
import android.util.Log;

import androidx.annotation.NonNull;

import java.io.File;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.regex.Pattern;

import apincer.music.core.playback.spi.PlaybackService;
import apincer.music.core.playback.spi.PlaybackServiceBinder;
import apincer.music.core.utils.ApplicationUtils;

public class BaseServer {
    private static final String TAG = "BaseServer";
    public static final Pattern IPV4_PATTERN =
            Pattern.compile(
                    "^(25[0-5]|2[0-4]\\d|[0-1]?\\d?\\d)(\\.(25[0-5]|2[0-4]\\d|[0-1]?\\d?\\d)){3}$");

    public static final int UPNP_SERVER_PORT = 49152; // IANA-recommended range 49152-65535 for UPnP
    public static final int CONTENT_SERVER_PORT = 9000; //8089;
    public static final int WEB_SERVER_PORT = 9000;
    public static final String CONTEXT_PATH_WEBSOCKET = "/ws";
    protected static final String CONTEXT_PATH_ROOT = "/";
    public static final String CONTEXT_PATH_COVERART = "/coverart/";
    public static final String CONTEXT_PATH_MUSIC = "/music/";

    protected final Context context;
    private final File coverartDir;
    private final String appVersion;
    private final String osVersion;
    private final List<String> libInfos = new ArrayList<>();

    public PlaybackService getPlaybackService() {
        return playbackService;
    }

    private PlaybackService playbackService;
    private boolean isPlaybackServiceBound;

    private final ServiceConnection serviceConnection = new ServiceConnection() {
        @SuppressLint("CheckResult")
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            // Get the binder from the service and set the mediaServerService instance
            PlaybackServiceBinder binder = (PlaybackServiceBinder) service;
            playbackService = binder.getService();
            isPlaybackServiceBound = true;
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            isPlaybackServiceBound = false;
            playbackService = null;
            //  Log.w(TAG, "PlaybackService disconnected unexpectedly.");
        }
    };

    public BaseServer(Context context) {
        this.context = context;
        this.coverartDir = context.getExternalCacheDir();
        this.appVersion = ApplicationUtils.getVersionNumber(context);
        this.osVersion = Build.VERSION.RELEASE;

        Intent intent = new Intent();
        intent.setComponent(new ComponentName("apincer.android.mmate", "apincer.android.mmate.service.PlaybackServiceImpl"));
        context.bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE);
    }

    public void destroy() {
        if(playbackService != null) {
            context.unbindService(serviceConnection);
            playbackService = null;
            isPlaybackServiceBound = false;
        }
    }

    public String getServerSignature(String componentName) {
        //Server:  WebServer MusicMate/3.11.0-251014 (Android/16; Jetty/12.1.1;)
        String libInfos = "";
        for (String libInfo : this.libInfos) {
            libInfos += libInfo + "; ";
        }
        return String.format("%s MusicMate/%s (Android/%s; %s)", trimToEmpty(componentName), appVersion, osVersion, trimToEmpty(libInfos));
    }

    public void addLibInfo(String name, String version) {
        if(isEmpty(version)) {
            libInfos.add(name);
        }else {
            libInfos.add(name + "/" + version);
        }
    }

    public Context getContext() {
        return context;
    }

    public File getCoverartDir(String coverartName) {
        return new File(coverartDir, coverartName);
    }

    /**
     * Formats the given time value as a string in the standard IMF-fixdate format (RFC 1123).
     *
     * @param time the time in milliseconds since the Java epoch (00:00:00 GMT, January 1, 1970)
     * @return the given time value as a formatted string
     */
    public String formatDate(long time) {
        return DateTimeFormatter.RFC_1123_DATE_TIME.format(
                ZonedDateTime.ofInstant(Instant.ofEpochMilli(time), ZoneOffset.UTC)
        );
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
}
