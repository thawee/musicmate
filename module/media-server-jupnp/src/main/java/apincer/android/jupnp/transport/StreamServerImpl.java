package apincer.android.jupnp.transport;

import android.content.Context;
import android.util.Log;

import org.jupnp.UpnpServiceConfiguration;
import org.jupnp.protocol.ProtocolFactory;
import org.jupnp.transport.Router;
import org.jupnp.transport.spi.InitializationException;
import org.jupnp.transport.spi.StreamServer;

import java.net.InetAddress;

import apincer.android.mmate.core.server.DefaultContentServerImpl;
import apincer.android.mmate.core.server.IMediaServer;
import apincer.android.mmate.core.server.WebServer;
import apincer.android.mmate.core.server.jetty.JettyContentServerImpl;
import apincer.android.mmate.core.server.jetty.JettyWebServerImpl;

public class StreamServerImpl implements StreamServer<StreamServerConfigurationImpl> {

    public abstract static class JUpnpServer extends WebServer {
        protected Router router;
        protected StreamServerConfigurationImpl configuration;
        public static final String SERVER_SUFFIX = "UPnP/1.0 jUPnP/3.0.3";
        public JUpnpServer(Context context, IMediaServer mediaServer, Router router, StreamServerConfigurationImpl configuration) {
            super(context, mediaServer);
            this.router = router;
            this.configuration = configuration;
        }

        public ProtocolFactory getProtocolFactory() {
            return router.getProtocolFactory();
        }

        public UpnpServiceConfiguration getConfiguration() {
            return router.getConfiguration();
        }

        @Override
        public int getListenPort() {
            return UPNP_SERVER_PORT;
        }
    }

   // public static final UDAServiceType AV_TRANSPORT_TYPE = new UDAServiceType("AVTransport");
    private static final String TAG = "StreamServerImpl";
    final private StreamServerConfigurationImpl configuration;
    private WebServer contentServer;
    private JUpnpServer upnpServer;
    private WebServer webServer;
    private final Context context;
    private final IMediaServer mediaServer;
   // private final TagRepository tagRepos;

    public static String streamServerHost = "";

    public StreamServerImpl(Context context, IMediaServer mediaServer, StreamServerConfigurationImpl configuration) {
        this.configuration = configuration;
        this.context = context;
        this.mediaServer = mediaServer;
      //  this.tagRepos = mediaServer.getTagReRepository();
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

        try {
            // if(Settings.isUseNettyLibrary(context)) {
            //this.webServer = new NettyWebServerImpl(context, router, configuration);
            this.webServer = new JettyWebServerImpl(context, mediaServer);
            this.webServer.initServer(bindAddress);

            //this.contentServer = new NettyContentServerImpl(context, router, configuration);
            this.contentServer = new JettyContentServerImpl(context, mediaServer);
            //this.contentServer = new DefaultContentServerImpl(context, mediaServer);
            this.contentServer.initServer(bindAddress);

            //this.upnpServer = new NettyUPnpServerImpl(context, mediaServer, router, configuration);
           // this.upnpServer = new UndertowUPnpServerImpl(context, mediaServer, router, configuration);
            this.upnpServer = new DefaultUPnpServerImpl(context, mediaServer, router, configuration);
            this.upnpServer.initServer(bindAddress);
      /*  }else {
            this.webServer = new JettyWebServerImpl(context, router, configuration);
            this.webServer.initServer(bindAddress);

            this.contentServer = new JettyContentServerImpl(context, router, configuration);
            this.contentServer.initServer(bindAddress);

            this.upnpServer = new JettyUPnpServerImpl(context,router, configuration);
            this.upnpServer.initServer(bindAddress);
        } */
        } catch (Exception e) {
            throw new RuntimeException(e);
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