package apincer.music.server.jupnp.transport;

import static apincer.music.core.Constants.LIBRARIES_INFO_FILE;

import android.annotation.SuppressLint;
import android.content.Context;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkRequest;
import android.net.wifi.WifiManager;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.util.Log;

import androidx.annotation.NonNull;

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

    private PowerManager.WakeLock wakeLock;
    private WifiManager.WifiLock wifiLock;

    private final Handler networkHandler = new Handler(Looper.getMainLooper());
    private final ConnectivityManager connectivityManager;
    private ConnectivityManager.NetworkCallback networkCallback;
    private Runnable networkMonitor;

    public StreamServerImpl(Context context, UpnpServer upnpServer, WebServer webServer, StreamServerConfiguration configuration) {
        this.configuration = configuration;
        this.context = context;
        this.upnpServer = upnpServer;
        this.webServer = webServer;
        this.connectivityManager = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
    }

    public StreamServerConfiguration getConfiguration() {
        return configuration;
    }

    /**
     * Sets up network monitoring. This will not start the server, but will
     * stop it after 30 minutes if the Wi-Fi connection is lost.
     */
    @SuppressLint("MissingPermission")
    private void startNetworkMonitoring() {
        NetworkRequest networkRequest = new NetworkRequest.Builder()
                .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
                .build();

        networkMonitor = () -> {
            Log.w(TAG, "WiFi has been disconnected, stopping server to save battery.");
            if(upnpServer != null) {
                upnpServer.stopServer();
            }
            if(webServer != null) {
                webServer.stopServer();
            }
            releaseLocks();
        };

        networkCallback = new ConnectivityManager.NetworkCallback() {
            @Override
            public void onAvailable(@NonNull Network network) {
                // Wi-Fi is connected. Cancel any pending shutdown command.
                Log.d(TAG, "WiFi connection available. Cancelling stop timer.");
                networkHandler.removeCallbacks(networkMonitor);
            }

            @Override
            public void onLost(@NonNull Network network) {
                // Wi-Fi is lost. Schedule a shutdown in 30 seconds.
                Log.d(TAG, "WiFi connection lost. Server will stop in 30 seconds.");
                networkHandler.postDelayed(networkMonitor,  30 * 1000L); // 30 seconds
            }
        };

        connectivityManager.registerNetworkCallback(networkRequest, networkCallback);
    }

    private void stopNetworkMonitoring() {
        // Unregister the callback to prevent leaks.
        if (networkCallback != null) {
            try {
                connectivityManager.unregisterNetworkCallback(networkCallback);
                networkCallback = null; // Clear the reference
            } catch (Exception e) {
                Log.w(TAG, "Error unregistering network callback", e);
            }
        }
    }

    private void acquireLocks() {
        PowerManager pm = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
        if (pm != null && wakeLock == null) {
            // PARTIAL_WAKE_LOCK keeps the CPU running even if the screen is off
            wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "MusicMate:ServerWakeLock");
            wakeLock.acquire(10*60*1000L /*10 minutes*/);
        }

        WifiManager wm = (WifiManager) context.getApplicationContext().getSystemService(Context.WIFI_SERVICE);
        if (wm != null && wifiLock == null) {
            // WIFI_MODE_FULL_HIGH_PERF is critical for 24-bit/192kHz audio (Android 10+)
            wifiLock = wm.createWifiLock(WifiManager.WIFI_MODE_FULL_LOW_LATENCY, "MusicMate:WifiLock");
            wifiLock.acquire();
        }
    }

    private void releaseLocks() {
        if (wakeLock != null && wakeLock.isHeld()) {
            wakeLock.release();
            wakeLock = null;
        }
        if (wifiLock != null && wifiLock.isHeld()) {
            wifiLock.release();
            wifiLock = null;
        }
    }

    @Override
    synchronized public void init(InetAddress bindAddress, final Router router) throws InitializationException {
        Log.i(TAG, TAG+" - Initialise Stream Servers");

        acquireLocks(); // Keep the CPU and Wifi awake

        try {
            this.webServer.restartServer(bindAddress);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        try {
            this.upnpServer.restartServer(bindAddress, router);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        writeLibrariesInfo();
        startNetworkMonitoring();
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
        releaseLocks();
        stopNetworkMonitoring();
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