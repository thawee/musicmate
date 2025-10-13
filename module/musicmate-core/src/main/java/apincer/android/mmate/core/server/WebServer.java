package apincer.android.mmate.core.server;

import static apincer.android.mmate.core.utils.StringUtils.trimToEmpty;

import android.content.Context;

import java.io.File;
import java.net.InetAddress;
import java.text.SimpleDateFormat;
import java.util.Locale;
import java.util.TimeZone;

import apincer.android.mmate.core.utils.ApplicationUtils;

public abstract class WebServer {
    public static final int UPNP_SERVER_PORT = 49152; // IANA-recommended range 49152-65535 for UPnP
    public static final int CONTENT_SERVER_PORT = 8089;
    public static final int WEB_SERVER_PORT = 9000;
    public static final String RFC1123_PATTERN = "EEE, dd MMM yyyy HH:mm:ss z";
    //public static final String SERVER_SUFFIX = "UPnP/1.0 jUPnP/3.0.3";
    public static final String CONTEXT_PATH_WEBSOCKET = "/ws";
    protected static final String CONTEXT_PATH_ROOT = "/";
    public static final String CONTEXT_PATH_COVERART = "/coverart/";
    private final static TimeZone GMT_ZONE = TimeZone.getTimeZone("GMT");
    public static final SimpleDateFormat dateFormatter = new SimpleDateFormat(RFC1123_PATTERN, Locale.US);
    static {
        dateFormatter.setTimeZone(GMT_ZONE);
    }

    protected final Context context;
    protected final IMediaServer mediaServer;
    private final File coverartDir;
    private final String serverName;

    public WebServer(Context context, IMediaServer mediaServer) {
        this.context = context;
        this.mediaServer = mediaServer;
        this.coverartDir = context.getExternalCacheDir();
        this.serverName = "MusicMate/"+ ApplicationUtils.getVersionNumber(context);
    }

    public abstract void initServer(InetAddress bindAddress) throws Exception;
    public abstract void stopServer();

    public String getFullServerName(String suffix) {
        return String.format("%s %s %s", trimToEmpty(suffix), getServerVersion(), serverName);
    }

    protected abstract String getServerVersion();

    public Context getContext() {
        return context;
    }

    public abstract int getListenPort();

    public File getCoverartDir(String coverartName) {
        return new File(coverartDir, coverartName);
    }
}
