package apincer.music.server.jupnp;

import org.jupnp.transport.impl.NetworkAddressFactoryImpl;

import java.net.InetAddress;
import java.net.NetworkInterface;

import apincer.music.core.utils.NetworkUtils;

public class MediaServerAddressFactory extends NetworkAddressFactoryImpl {
    public MediaServerAddressFactory(int streamListenPort, int multicastResponsePort) {
        super(streamListenPort, multicastResponsePort);
    }

    @Override
    protected boolean isUsableAddress(NetworkInterface networkInterface, InetAddress address) {
        // First, perform all the standard checks from the base jUPnP library.
        // This includes checks for isUp(), supportsMulticast(), isLoopbackAddress(), etc.
        boolean isGenerallyUsable = super.isUsableAddress(networkInterface, address);
        if (!isGenerallyUsable) {
            return false;
        }

        // Allow WiFi client interfaces (wlan*) AND hotspot/AP interfaces (ap*, swlan*).
        return NetworkUtils.isOnWifiNetwork(networkInterface, address)
                || NetworkUtils.isOnHotspotInterface(networkInterface, address)
                || NetworkUtils.isOnCellularNetwork(networkInterface, address);
    }
}
