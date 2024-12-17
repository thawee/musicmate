package apincer.android.mmate.dlna.transport;

import android.content.Context;
import android.util.Log;

import org.jupnp.transport.Router;
import org.jupnp.transport.spi.InitializationException;
import org.jupnp.transport.spi.StreamServer;

import java.net.InetAddress;

public class StreamServerImpl implements StreamServer<StreamServerConfigurationImpl> {

    interface Server {
        void initServer(InetAddress bindAddress) throws InitializationException;
        void stopServer();
    }

    private static final String TAG = "StreamServerImpl";
    final private StreamServerConfigurationImpl configuration;
    private HCContentServer contentServer;
    private Server upnpServer;
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
        upnpServer = new JLHUPnpServer(context,router, configuration);
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