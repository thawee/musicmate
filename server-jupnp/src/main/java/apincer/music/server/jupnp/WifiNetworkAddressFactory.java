package apincer.music.server.jupnp;

import static apincer.music.server.jupnp.MediaServerImpl.IPV4_PATTERN;

import org.jupnp.android.AndroidNetworkAddressFactory;

import java.net.InetAddress;
import java.net.NetworkInterface;

public class WifiNetworkAddressFactory extends AndroidNetworkAddressFactory {
    public WifiNetworkAddressFactory(int streamListenPort, int multicastResponsePort) {
        super(streamListenPort, multicastResponsePort);
    }

    @Override
    protected boolean isUsableAddress(NetworkInterface networkInterface, InetAddress address) {
        boolean result = false;
        if (!networkInterface.getName().startsWith("rmnet")) {
            if (!address.isLoopbackAddress() && address
                    .getHostAddress() != null
                    && IPV4_PATTERN.matcher(address
                    .getHostAddress()).matches()) {
                result = true;
            }
        }
        return result;
    }
}
