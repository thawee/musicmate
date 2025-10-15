package apincer.android.mmate.dlna.transport;

import android.content.Context;
import android.util.Log;

import org.jupnp.UpnpServiceConfiguration;
import org.jupnp.protocol.ProtocolFactory;
import org.jupnp.transport.Router;
import org.jupnp.transport.spi.InitializationException;
import org.jupnp.transport.spi.StreamServer;

import java.io.File;
import java.net.InetAddress;
import java.text.SimpleDateFormat;
import java.util.Locale;
import java.util.TimeZone;

import apincer.android.mmate.Settings;
import apincer.android.mmate.dlna.transport.jetty.JettyContentServerImpl;
import apincer.android.mmate.dlna.transport.jetty.JettyUPnpServerImpl;
import apincer.android.mmate.dlna.transport.jetty.JettyWebServerImpl;
import apincer.android.mmate.dlna.transport.netty.NettyContentServerImpl;
import apincer.android.mmate.dlna.transport.netty.NettyUPnpServerImpl;
import apincer.android.mmate.dlna.transport.netty.NettyWebServerImpl;
import apincer.android.mmate.utils.ApplicationUtils;

public class StreamServerImpl implements StreamServer<StreamServerConfigurationImpl> {

    public abstract static class StreamServer {
        public static final String RFC1123_PATTERN = "EEE, dd MMM yyyy HH:mm:ss z";
        public static final String SERVER_SUFFIX = "UPnP/1.0 jUPnP/3.0.3";
        protected static final String CONTEXT_PATH_WEBSOCKET = "/ws";
        protected static final String CONTEXT_PATH_ROOT = "/";
        protected static final String CONTEXT_PATH_COVERART = "/coverart/";
        private final static TimeZone GMT_ZONE = TimeZone.getTimeZone("GMT");
        static final SimpleDateFormat dateFormatter = new SimpleDateFormat(RFC1123_PATTERN, Locale.US);
        static {
            dateFormatter.setTimeZone(GMT_ZONE);
        }

        private final StreamServerConfigurationImpl configuration;
        private final Router router;
        private final Context context;
        private final File coverartDir;
        private final String serverName;

        public StreamServer(Context context, Router router, StreamServerConfigurationImpl configuration) {
            this.configuration = configuration;
            this.router = router;
            this.context = context;
            this.coverartDir = context.getExternalCacheDir();
            this.serverName = "MusicMate/"+ ApplicationUtils.getVersionNumber(context);
        }

        public abstract void initServer(InetAddress bindAddress) throws InitializationException;
        public abstract void stopServer();

        public String getFullServerName( ) {
            return String.format("%s %s %s",SERVER_SUFFIX, getServerVersion(), serverName);
        }

        protected abstract String getServerVersion();

        public Context getContext() {
            return context;
        }

        public int getListenPort() {
            return configuration.getListenPort();
        }

        public File getCoverartDir(String coverartName) {
            return new File(coverartDir, coverartName);
        }

        public ProtocolFactory getProtocolFactory() {
            return router.getProtocolFactory();
        }

        public UpnpServiceConfiguration getConfiguration() {
            return router.getConfiguration();
        }
    }

    private static final String TAG = "StreamServerImpl";
    final private StreamServerConfigurationImpl configuration;
    private StreamServer contentServer;
    private StreamServer upnpServer;
    private StreamServer webServer;
    private final Context context;

    public static String streamServerHost = "";
   // private static final List<String> transCodeList = new ArrayList<>();

    public StreamServerImpl(Context context, StreamServerConfigurationImpl configuration) {
        this.configuration = configuration;
        this.context = context;
       // transCodeList.add("AAC");
    }

    public StreamServerConfigurationImpl getConfiguration() {
        return configuration;
    }

    synchronized public void init(InetAddress bindAddress, final Router router) throws InitializationException {
        Log.i(TAG, "Initialise Stream Servers");

       // Log.i(TAG, "  Stop servers before start if server already running.");
        if(upnpServer != null) {
            upnpServer.stopServer();
        }
        if(contentServer != null) {
            contentServer.stopServer();
        }
        if(webServer != null) {
            webServer.stopServer();
        }

        streamServerHost = bindAddress.getHostAddress();

        if(Settings.isUseNettyLibrary(context)) {
            //this.webServer = new NettyWebServerImpl(context, router, configuration);
            this.webServer = new JettyWebServerImpl(context, router, configuration);
            this.webServer.initServer(bindAddress);

            //this.contentServer = new NettyContentServerImpl(context, router, configuration);
            this.contentServer = new JettyContentServerImpl(context, router, configuration);
            this.contentServer.initServer(bindAddress);

            this.upnpServer = new NettyUPnpServerImpl(context,router, configuration);
            this.upnpServer.initServer(bindAddress);
        }else {
            this.webServer = new JettyWebServerImpl(context, router, configuration);
            this.webServer.initServer(bindAddress);

            this.contentServer = new JettyContentServerImpl(context, router, configuration);
            this.contentServer.initServer(bindAddress);

            this.upnpServer = new JettyUPnpServerImpl(context,router, configuration);
            this.upnpServer.initServer(bindAddress);
        }
    }

    synchronized public int getPort() {
        return this.configuration.listenPort;
    }

    synchronized public void stop() {
       // Log.i(TAG, "Stop Stream Servers");
        if(upnpServer != null) {
            upnpServer.stopServer();
        }
        if(contentServer != null) {
            contentServer.stopServer();
        }
        if(webServer != null) {
            webServer.stopServer();
        }
    }

    @Override
    public void run() {

    }
}