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
import java.util.stream.Collectors;

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



    @Override
    synchronized public void init(InetAddress bindAddress, final Router router) throws InitializationException {
        Log.i(TAG, TAG+" - Initialise Stream Servers");

            try {
                this.webServer.restartServer(bindAddress);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }

            try {
                upnpServer.restartServer(bindAddress, router);
            } catch (Exception e) {
                Log.e(TAG, "Delayed start failed", e);
            }

            writeLibrariesInfo();
    }

    synchronized public int getPort() {
        return this.configuration.getListenPort();
    }

    @Override
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
            libs.add("jUPnP");
            libs.addAll(upnpServer.getLibInfos());
            libs.addAll(webServer.getLibInfos());

            // Stream, deduplicate, and join in one chain
            String info = libs.stream()
                    .distinct()
                    .collect(Collectors.joining(", "));

            ApplicationUtils.deleteFilesFromAndroidFilesDir(context, LIBRARIES_INFO_FILE);
            ApplicationUtils.writeToAndroidFilesDir(context, LIBRARIES_INFO_FILE, info);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}