package apincer.music.server.jupnp.transport;

import android.content.Context;
import android.util.Log;

import org.jupnp.transport.Router;
import org.jupnp.transport.spi.InitializationException;
import org.jupnp.transport.spi.StreamServer;
import org.jupnp.transport.spi.StreamServerConfiguration;

import java.net.InetAddress;

import apincer.music.core.server.spi.WebServer;
import apincer.music.core.server.spi.UpnpServer;

public class StreamServerImpl implements StreamServer<StreamServerConfiguration> {
    private static final String TAG = "StreamServerImpl";
    final private StreamServerConfiguration configuration;

    // Injected
    private final Context context;
    private final WebServer contentServer;
    private final UpnpServer upnpServer;

    public StreamServerImpl(Context context, UpnpServer upnpServer, WebServer contentServer, StreamServerConfiguration configuration) {
        this.configuration = configuration;
        this.context = context;
        this.upnpServer = upnpServer;
        this.contentServer = contentServer;
    }

    public StreamServerConfiguration getConfiguration() {
        return configuration;
    }

    synchronized public void init(InetAddress bindAddress, final Router router) throws InitializationException {
        Log.i(TAG, TAG+" - Initialise Stream Servers");

       // Log.i(TAG, "  Stop servers before start if server already running.");
        if(upnpServer != null) {
            upnpServer.stopServer();
        }
        if(contentServer != null) {
            contentServer.stopServer();
        }

        try {
            this.contentServer.initServer(bindAddress);
            this.upnpServer.initServer(bindAddress, router);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    synchronized public int getPort() {
        return this.configuration.getListenPort();
    }

    synchronized public void stop() {
       // Log.i(TAG, "Stop Stream Servers");
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