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
     * Checks if the device is currently connected to a WiFi or Ethernet network as a <em>client</em>.
     * This does not detect hotspot/AP mode — use {@link #isServerNetworkAvailable(Context)} for that.
     *
     * @param context The application context to access system services.
     * @return true if connected to WiFi/Ethernet/VPN, false otherwise.
     */
    @RequiresPermission(Manifest.permission.ACCESS_NETWORK_STATE)
    public static boolean isWifiConnected(@NonNull Context context) {
        ConnectivityManager connectivityManager = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        if (connectivityManager == null) {
            Log.e(TAG, "ConnectivityManager not available.");
            return !getIpAddress().equals("0.0.0.0");
        }

        Network activeNetwork = connectivityManager.getActiveNetwork();
        if (activeNetwork == null) {
            Log.d(TAG, "No active network.");
            return !getIpAddress().equals("0.0.0.0");
        }

        NetworkCapabilities capabilities = connectivityManager.getNetworkCapabilities(activeNetwork);
        if (capabilities == null) {
            Log.d(TAG, "Could not get network capabilities.");
            return !getIpAddress().equals("0.0.0.0");
        }

        return capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
               capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) ||
               capabilities.hasTransport(NetworkCapabilities.TRANSPORT_VPN) ||
               !getIpAddress().equals("0.0.0.0");
    }

    /**
     * Single entry-point for the media server to decide if it can run.
     * Returns true if any of the following are active:
     * <ul>
     *   <li>WiFi / Ethernet client connection</li>
     *   <li>WiFi hotspot (the device is the AP)</li>
     * </ul>
     */
    @RequiresPermission(Manifest.permission.ACCESS_NETWORK_STATE)
    public static boolean isServerNetworkAvailable(@NonNull Context context) {
        return isWifiConnected(context) || isHotspotActive(context);
    }

    /**
     * Returns true when the device is acting as a WiFi hotspot (AP mode).
     * <p>
     * Detects hotspot by scanning network interfaces for an active AP interface
     * ({@code ap*} on Pixel/AOSP, {@code swlan*} on Samsung) that has a live IPv4
     * address. This avoids relying on any deprecated or unavailable API.
     *
     * @param context unused — kept for API consistency with other helpers.
     * @return true if a hotspot interface is up and has a valid IPv4 address.
     */
    @RequiresPermission(Manifest.permission.ACCESS_NETWORK_STATE)
    public static boolean isHotspotActive(@NonNull Context context) {
        try {
            boolean isWifiConnected = isWifiConnected(context);
            for (NetworkInterface ni : Collections.list(NetworkInterface.getNetworkInterfaces())) {
                if (!ni.isUp() || ni.isLoopback()) continue;
                String name = ni.getName();
                if (name == null) continue;

                // Match dedicated AP interfaces (ap*, swlan*) or secondary/unconnected WiFi interfaces (wlan*, wslan*)
                boolean isApInterface = name.startsWith("ap") || 
                                        name.startsWith("swlan") || 
                                        ((name.startsWith("wlan") || name.startsWith("wslan")) && (!name.endsWith("0") || !isWifiConnected));

                if (!isApInterface) continue;

                for (InetAddress addr : Collections.list(ni.getInetAddresses())) {
                    String host = addr.getHostAddress();
                    if (host != null && IPV4_PATTERN.matcher(host).matches()) {
                        Log.d(TAG, "Hotspot detected on interface '" + name + "' with IP " + host);
                        return true;
                    }
                }
            }
        } catch (Exception e) {
            Log.w(TAG, "isHotspotActive check failed", e);
        }
        return false;
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
                // Check if the interface is up and is a WiFi client or hotspot interface.
                if (ni.isUp() && isHotspotInterfaceName(ni.getName())) {
                    for (InetAddress address : Collections.list(ni.getInetAddresses())) {
                        // Find the first valid, non-loopback IPv4 address.
                        if (!address.isLoopbackAddress()) {
                            String hostAddress = address.getHostAddress();
                            if (hostAddress != null && IPV4_PATTERN.matcher(hostAddress).matches()) {
                                //Log.d(TAG, "Found WiFi IP address: " + hostAddress);
                                return hostAddress; // Return immediately with the WiFi IP.
                            }
                        }
                    }
                }
            }

            // --- Step 2: Fallback to Other Interfaces (if no WiFi IP was found) ---
            for (NetworkInterface ni : Collections.list(NetworkInterface.getNetworkInterfaces())) {
                // Skip interfaces that are down, loopback, or mobile data.
                if (!ni.isUp() || ni.isLoopback() || isOnCellularNetwork(ni, null)) {
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

    /**
     * Returns true when the given interface is a WiFi client interface (wlan*).
     * For hotspot/AP interfaces use {@link #isOnHotspotInterface(NetworkInterface, InetAddress)}.
     */
    public static boolean isOnWifiNetwork(NetworkInterface networkInterface, InetAddress address) {
        String interfaceName = networkInterface.getName();
        return isWifiClientInterfaceName(interfaceName);
    }

    /**
     * Returns true when the given interface is a WiFi hotspot / AP interface.
     * Common names across OEMs: {@code ap0} (Pixel/AOSP), {@code swlan0} (Samsung),
     * {@code wlan1} (some Qualcomm devices in concurrent AP+STA mode).
     */
    public static boolean isOnHotspotInterface(NetworkInterface networkInterface, InetAddress address) {
        String interfaceName = networkInterface.getName();
        return isHotspotInterfaceName(interfaceName);
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    private static boolean isWifiClientInterfaceName(String name) {
        // Standard WiFi-client interface prefix on Android (including wslan for some OEMs).
        return name != null && (name.startsWith("wlan") || name.startsWith("wslan"));
    }

    public static boolean isHotspotInterfaceName(String name) {
        if (name == null) return false;
        // ap*  — Pixel / AOSP
        // swlan* — Samsung
        // wlan* / wslan* — Qualcomm or vivo concurrent/reused interfaces
        return name.startsWith("ap") || name.startsWith("swlan") || name.startsWith("wlan") || name.startsWith("wslan");
    }

    public static boolean isOnCellularNetwork(NetworkInterface networkInterface, InetAddress address) {
        // On Android, Cellular interfaces are typically named "rmnet0", "ccmni0", "pdp0", etc.
        String interfaceName = networkInterface.getName();
        if (interfaceName == null) return false;
        String lowerName = interfaceName.toLowerCase();
        return lowerName.startsWith("rmnet") || 
               lowerName.startsWith("ccmni") || 
               lowerName.startsWith("pdp") || 
               lowerName.startsWith("wwan") || 
               lowerName.startsWith("sipc") || 
               lowerName.startsWith("spipe") || 
               lowerName.startsWith("lte") || 
               lowerName.startsWith("ppp");
    }
}
