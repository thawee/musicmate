package apincer.android.mmate.dlna.transport;

import android.content.Context;
import android.util.Log;

import org.jupnp.protocol.ProtocolFactory;
import org.jupnp.transport.Router;
import org.jupnp.transport.spi.InitializationException;
import org.jupnp.transport.spi.StreamServer;

import java.io.File;
import java.net.InetAddress;
import java.text.SimpleDateFormat;
import java.util.Locale;
import java.util.TimeZone;

import apincer.android.mmate.utils.ApplicationUtils;

public class StreamServerImpl implements StreamServer<StreamServerConfigurationImpl> {
    public static final String TYPE_IMAGE_PNG = "image/png";
    public static final String TYPE_IMAGE_JPEG = "image/jpeg";
    abstract static class UPnpServer {
        public static final String RFC1123_PATTERN = "EEE, dd MMM yyyy HH:mm:ss z";
        public static final String SERVER_SUFFIX = "UPnP/1.0 jUPnP/3.0";
        private final static TimeZone GMT_ZONE = TimeZone.getTimeZone("GMT");
        static final SimpleDateFormat dateFormatter = new SimpleDateFormat(RFC1123_PATTERN, Locale.US);
        static {
            dateFormatter.setTimeZone(GMT_ZONE);
        }

        private final StreamServerConfigurationImpl configuration;
        private final Router router;
        private final File coverartDir;
        private final String serverName;

        public UPnpServer(Context context, Router router, StreamServerConfigurationImpl configuration) {
            this.configuration = configuration;
            this.router = router;
            this.coverartDir = context.getExternalCacheDir();
            this.serverName = "MusicMate/"+ ApplicationUtils.getVersionNumber(context);
        }

        public abstract void initServer(InetAddress bindAddress) throws InitializationException;
        public abstract void stopServer();

        public String getFullServerName(String httpServerName) {
            return String.format("%s %s %s",serverName,httpServerName,SERVER_SUFFIX);
        }

        public int getListenPort() {
            return configuration.getListenPort();
        }

        public File getCoverartDir() {
            return coverartDir;
        }

        public String getDate() {
            return serverName;
        }
        public ProtocolFactory getProtocolFactory() {
            return router.getProtocolFactory();
        }

        public Router getRouter() {
            return  router;
        }
    }

    private static final String TAG = "StreamServerImpl";
    final private StreamServerConfigurationImpl configuration;
    private HCContentServer contentServer;
    private UPnpServer upnpServer;
    private final Context context;

    public StreamServerImpl(Context context, StreamServerConfigurationImpl configuration) {
        this.configuration = configuration;
        this.context = context;
    }

    public StreamServerConfigurationImpl getConfiguration() {
        return configuration;
    }

    synchronized public void init(InetAddress bindAddress, final Router router) throws InitializationException {
        Log.i(TAG, "Initialise Stream Servers");

        Log.i(TAG, "  Stop servers before start if server already running.");
        stop();

        //upnpServer = new JLHUPnpServer(context,router, configuration);
       // upnpServer = new NettyUPnpServer(context,router, configuration);
        upnpServer = new JettyUPnpServer(context,router, configuration);
        upnpServer.initServer(bindAddress);

        this.contentServer = new HCContentServer(context);
        contentServer.initServer(bindAddress);
    }

    synchronized public int getPort() {
        return this.configuration.listenPort;
    }

    synchronized public void stop() {
        Log.i(TAG, "Stop Stream Servers");
        if(upnpServer != null) {
            upnpServer.stopServer();
        }
        if(contentServer != null) {
            contentServer.stopServer();
        }
    }

    @Override
    public void run() {

    }
}