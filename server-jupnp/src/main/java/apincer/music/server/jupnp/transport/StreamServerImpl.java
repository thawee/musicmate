package apincer.music.server.jupnp.transport;

import static apincer.music.core.Constants.LIBRARIES_INFO_FILE;

import android.content.Context;
import android.util.Log;

import org.jupnp.transport.Router;
import org.jupnp.transport.spi.InitializationException;
import org.jupnp.transport.spi.StreamServer;
import org.jupnp.transport.spi.StreamServerConfiguration;

import java.io.IOException;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.List;

import apincer.music.core.server.spi.WebServer;
import apincer.music.core.server.spi.UpnpServer;
import apincer.music.core.utils.ApplicationUtils;

public class StreamServerImpl implements StreamServer<StreamServerConfiguration> {
    private static final String TAG = "StreamServerImpl";

    final private StreamServerConfiguration configuration;

    // Injected
    private final Context context;
    private final WebServer webServer;
    private final UpnpServer upnpServer;

    public StreamServerImpl(Context context, UpnpServer upnpServer, WebServer webServer, StreamServerConfiguration configuration) {
        this.configuration = configuration;
        this.context = context;
        this.upnpServer = upnpServer;
        this.webServer = webServer;
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
        if(webServer != null) {
            webServer.stopServer();
        }

        try {
            this.webServer.initServer(bindAddress);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        try {
            this.upnpServer.initServer(bindAddress, router);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        writeLibrariesInfo();
    }

    synchronized public int getPort() {
        return this.configuration.getListenPort();
    }

    synchronized public void stop() {
       // Log.i(TAG, "Stop Stream Servers");
        if(upnpServer != null) {
            upnpServer.stopServer();
        }
        if(webServer != null) {
            webServer.stopServer();
        }
    }

    @Override
    public void run() {

    }

    private void writeLibrariesInfo() {
        try {
            List<String> libs = new ArrayList<>();
            libs.add("jUPNP");
            libs.addAll(upnpServer.getLibInfos());
            libs.addAll(webServer.getLibInfos());
            String info = String.join(", ", libs);

            ApplicationUtils.deleteFilesFromAndroidFilesDir(context, LIBRARIES_INFO_FILE);
            ApplicationUtils.writeToAndroidFilesDir(context, LIBRARIES_INFO_FILE, info);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}