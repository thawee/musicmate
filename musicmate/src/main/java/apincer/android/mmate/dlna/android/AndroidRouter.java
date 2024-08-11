package apincer.android.mmate.dlna.android;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.wifi.WifiManager;
import android.util.Log;

import androidx.annotation.NonNull;


import org.jupnp.UpnpServiceConfiguration;
import org.jupnp.protocol.ProtocolFactory;
import org.jupnp.transport.RouterException;
import org.jupnp.transport.RouterImpl;
import org.jupnp.transport.spi.InitializationException;

import java.util.Objects;

public class AndroidRouter extends RouterImpl {
    private static final String TAG = "AndroidRouter";
    private final Context context;
    private final WifiManager wifiManager;
    private WifiManager.MulticastLock multicastLock;
    private WifiManager.WifiLock wifiLock;

    private Network currentNetwork;

    public AndroidRouter(UpnpServiceConfiguration configuration,
                         ProtocolFactory protocolFactory,
                         Context context) throws InitializationException {
        super(configuration, protocolFactory);
        this.context = context;
        this.wifiManager = ((WifiManager) context.getSystemService(Context.WIFI_SERVICE));
        ConnectivityManager connectivityManager = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        if (!isCellular()) {
            currentNetwork = connectivityManager.getActiveNetwork();

        }
        if (currentNetwork != null) {
            try {
                enable();
            } catch (RouterException e) {
                Log.e(TAG, String.format("RouterException network enabling %s", currentNetwork), e);
            }
        }
        connectivityManager.registerDefaultNetworkCallback(new ConnectivityManager.NetworkCallback() {
            @Override
            public void onAvailable(@NonNull android.net.Network network) {
                super.onAvailable(network);
                if (!isCellular() && !network.equals(currentNetwork)) {
                    Log.d(TAG, String.format("Network available %s", network));
                    if (currentNetwork != null) {
                        try {
                            disable();
                            Log.d(TAG, String.format("Network disabled %s", currentNetwork));
                        } catch (RouterException e) {
                            Log.e(TAG, String.format("RouterException network disabling %s", currentNetwork), e);
                        }
                    }
                    currentNetwork = network;
                    try {
                        enable();
                        Log.d(TAG, String.format("Network enabled %s", currentNetwork));
                    } catch (RouterException e) {
                        Log.e(TAG, String.format("RouterException network enabling %s", currentNetwork), e);
                    }

                }

            }

            @Override
            public void onLost(@NonNull android.net.Network network) {
                super.onLost(network);
                if (network.equals(currentNetwork)) {
                    Log.d(TAG, String.format("Network lost %s", network));
                    try {
                        disable();
                        Log.d(TAG, String.format("Network disabled %s", currentNetwork));
                    } catch (RouterException e) {
                        Log.e(TAG, String.format("RouterException network disabling %s", currentNetwork), e);
                    }
                    currentNetwork = null;
                }
            }
        });
    }

    @Override
    public boolean enable() throws RouterException {
        Log.v(TAG, "in android router enable");
        lock(writeLock);
        try {
            boolean enabled = super.enable();
            if (enabled) {
                // Enable multicast on the WiFi network interface,
                // requires android.permission.CHANGE_WIFI_MULTICAST_STATE
                if (isWifi()) {
                    setWiFiMulticastLock(true);
                    setWifiLock(true);
                }
            }
            return enabled;
        } finally {
            unlock(writeLock);
            Log.v(TAG, "leave android router enable");
        }
    }

    @Override
    public boolean disable() throws RouterException {
        Log.v(TAG, "in android router disable");
        lock(writeLock);
        try {
            // Disable multicast on WiFi network interface,
            // requires android.permission.CHANGE_WIFI_MULTICAST_STATE
            if (isWifi()) {
                setWiFiMulticastLock(false);
                setWifiLock(false);
            }
            return super.disable();
        } finally {
            unlock(writeLock);
            Log.v(TAG, "leave android router disable");
        }
    }

    private boolean isWifi() {
        ConnectivityManager connectivityManager = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        if(connectivityManager == null || connectivityManager.getActiveNetwork() == null || connectivityManager.getNetworkCapabilities(connectivityManager.getActiveNetwork()) == null) return false;
        return Objects.requireNonNull(connectivityManager.getNetworkCapabilities(connectivityManager.getActiveNetwork())).hasTransport(NetworkCapabilities.TRANSPORT_WIFI);
    }

    private boolean isCellular() {
        ConnectivityManager connectivityManager = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        if(connectivityManager == null || connectivityManager.getActiveNetwork() == null || connectivityManager.getNetworkCapabilities(connectivityManager.getActiveNetwork()) == null) return false;
        return Objects.requireNonNull(connectivityManager.getNetworkCapabilities(connectivityManager.getActiveNetwork())).hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR);
    }

    protected void setWiFiMulticastLock(boolean enable) {
        if (multicastLock == null) {
            multicastLock = wifiManager.createMulticastLock(getClass().getSimpleName());
        }

        if (enable) {
            if (multicastLock.isHeld()) {
                Log.w(TAG, "WiFi multicast lock already acquired");
            } else {
                Log.d(TAG, "WiFi multicast lock acquired");
                multicastLock.acquire();
            }
        } else {
            if (multicastLock.isHeld()) {
                Log.d(TAG, "WiFi multicast lock released");
                multicastLock.release();
            } else {
                Log.w(TAG, "WiFi multicast lock already released");
            }
        }
    }

    protected void setWifiLock(boolean enable) {
        if (wifiLock == null) {
            wifiLock = wifiManager.createWifiLock(WifiManager.WIFI_MODE_FULL_HIGH_PERF, getClass().getSimpleName());
        }

        if (enable) {
            if (wifiLock.isHeld()) {
                Log.w(TAG, "WiFi lock already acquired");
            } else {
                Log.d(TAG, "WiFi lock acquired");
                wifiLock.acquire();
            }
        } else {
            if (wifiLock.isHeld()) {
                Log.d(TAG, "WiFi lock released");
                wifiLock.release();
            } else {
                Log.w(TAG, "WiFi lock already released");
            }
        }
    }

}