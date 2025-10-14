package apincer.android.mmate.core.server;

import static apincer.android.mmate.core.utils.StringUtils.trimToEmpty;

import android.content.Context;
import android.os.Build;

import java.io.File;
import java.net.InetAddress;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

import apincer.android.mmate.core.utils.ApplicationUtils;

public abstract class AbstractServer {
    public static final int UPNP_SERVER_PORT = 49152; // IANA-recommended range 49152-65535 for UPnP
    public static final int CONTENT_SERVER_PORT = 9000; //8089;
    public static final int WEB_SERVER_PORT = 9000;
    public static final String CONTEXT_PATH_WEBSOCKET = "/ws";
    protected static final String CONTEXT_PATH_ROOT = "/";
    public static final String CONTEXT_PATH_COVERART = "/coverart/";
    public static final String CONTEXT_PATH_MUSIC = "/music/";

    protected final Context context;
    protected final IMediaServer mediaServer;
    private final File coverartDir;
    private final String appVersion;
    private final String osVersion;
    private final List<String> libInfos = new ArrayList<>();

    public AbstractServer(Context context, IMediaServer mediaServer) {
        this.context = context;
        this.mediaServer = mediaServer;
        this.coverartDir = context.getExternalCacheDir();
        this.appVersion = ApplicationUtils.getVersionNumber(context);
        this.osVersion = Build.VERSION.RELEASE;
    }

    public abstract void initServer(InetAddress bindAddress) throws Exception;
    public abstract void stopServer();

    public String getServerSignature() {
        //Server:  WebServer MusicMate/3.11.0-251014 (Android/16; Jetty/12.1.1;)
        String libInfos = "";
        for (String libInfo : this.libInfos) {
            libInfos += libInfo + "; ";
        }
        return String.format("%s MusicMate/%s (Android/%s; %s)", trimToEmpty(getComponentName()), appVersion, osVersion, trimToEmpty(libInfos));
    }

    public void addLibInfo(String name, String version) {
        libInfos.add(name+"/"+version);
    }

    protected abstract String getComponentName();

    public Context getContext() {
        return context;
    }

    public abstract int getListenPort();

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
}
