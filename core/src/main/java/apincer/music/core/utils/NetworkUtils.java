package apincer.music.core.utils;

import android.Manifest;
import android.content.Context;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresPermission;

import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.Collections;
import java.util.regex.Pattern;

public class NetworkUtils {
    private static final String TAG = "NetworkUtils";
    // This pattern should be defined in your class
    public static final Pattern IPV4_PATTERN = Pattern.compile(
            "^(" + "([0-9]|[1-9][0-9]|1[0-9]{2}|2[0-4][0-9]|25[0-5])\\." +
                    "([0-9]|[1-9][0-9]|1[0-9]{2}|2[0-4][0-9]|25[0-5])\\." +
                    "([0-9]|[1-9][0-9]|1[0-9]{2}|2[0-4][0-9]|25[0-5])\\." +
                    "([0-9]|[1-9][0-9]|1[0-9]{2}|2[0-4][0-9]|25[0-5])" + ")$");

    /**
     * Checks if the device is currently connected to a WiFi network.
     * This is the recommended modern approach using ConnectivityManager.
     *
     * @param context The application context to access system services.
     * @return true if connected to WiFi, false otherwise.
     */
    @RequiresPermission(Manifest.permission.ACCESS_NETWORK_STATE)
    public static boolean isWifiConnected(@NonNull Context context) {
        ConnectivityManager connectivityManager = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        if (connectivityManager == null) {
            Log.e(TAG, "ConnectivityManager not available.");
            return false;
        }

        Network activeNetwork = connectivityManager.getActiveNetwork();
        if (activeNetwork == null) {
            Log.d(TAG, "No active network.");
            return false; // No network connection at all.
        }

        NetworkCapabilities capabilities = connectivityManager.getNetworkCapabilities(activeNetwork);
        if (capabilities == null) {
            Log.d(TAG, "Could not get network capabilities.");
            return false;
        }

        // Check if the active network has the WiFi transport capability.
        return capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI);
    }

    /**
     * Retrieves the primary non-loopback IPv4 address of the device.
     * <p>
     * This method prioritizes active WiFi interfaces first, as the media server is
     * intended for WiFi use. If no WiFi IP is found, it checks other interfaces
     * as a fallback, ignoring mobile data connections.
     *
     * @return A non-null String representing the IPv4 address. Returns "0.0.0.0" if
     * no suitable network connection is found.
     */
    @NonNull
    public static String getIpAddress() {
        try {
            // --- Step 1: Prioritize WiFi Interfaces ---
            // Iterate through all network interfaces on the device.
            for (NetworkInterface ni : Collections.list(NetworkInterface.getNetworkInterfaces())) {
                // Check if the interface is up and is a WiFi interface (common prefix is "wlan").
                if (ni.isUp() && ni.getName().startsWith("wlan")) {
                    for (InetAddress address : Collections.list(ni.getInetAddresses())) {
                        // Find the first valid, non-loopback IPv4 address.
                        if (!address.isLoopbackAddress()) {
                            String hostAddress = address.getHostAddress();
                            if (hostAddress != null && IPV4_PATTERN.matcher(hostAddress).matches()) {
                                Log.d(TAG, "Found WiFi IP address: " + hostAddress);
                                return hostAddress; // Return immediately with the WiFi IP.
                            }
                        }
                    }
                }
            }

            // --- Step 2: Fallback to Other Interfaces (if no WiFi IP was found) ---
            for (NetworkInterface ni : Collections.list(NetworkInterface.getNetworkInterfaces())) {
                // Skip interfaces that are down, loopback, or mobile data ("rmnet").
                if (!ni.isUp() || ni.isLoopback() || ni.getName().startsWith("rmnet")) {
                    continue;
                }
                for (InetAddress address : Collections.list(ni.getInetAddresses())) {
                    if (!address.isLoopbackAddress()) {
                        String hostAddress = address.getHostAddress();
                        if (hostAddress != null && IPV4_PATTERN.matcher(hostAddress).matches()) {
                            Log.d(TAG, "Found fallback IP on interface '" + ni.getName() + "': " + hostAddress);
                            return hostAddress; // Return the first valid IP on any other suitable interface.
                        }
                    }
                }
            }
        } catch (SocketException ex) {
            Log.e(TAG, "Error retrieving network interfaces", ex);
        }

        // --- Step 3: Default if No IP Found ---
        // This indicates the device is likely offline.
        Log.w(TAG, "No suitable IP address found. Device may be offline.");
        return "0.0.0.0";
    }

    public static boolean isOnWifiNetwork(NetworkInterface networkInterface, InetAddress address) {
        // On Android, WiFi interfaces are typically named "wlan0", "wlan1", etc.
        String interfaceName = networkInterface.getName();
        return (interfaceName != null && interfaceName.startsWith("wlan"));
    }

    public static boolean isOnCellularNetwork(NetworkInterface networkInterface, InetAddress address) {
        // On Android, Cellular interfaces are typically named "rmnet0", "rmnet1", etc.
        String interfaceName = networkInterface.getName();
        return (interfaceName != null && interfaceName.startsWith("rmnet"));
    }
}
