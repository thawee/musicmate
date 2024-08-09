package apincer.android.mmate.dlna;

import static apincer.android.mmate.dlna.MediaServerService.IPV4_PATTERN;

import org.jupnp.android.AndroidNetworkAddressFactory;
import org.jupnp.android.AndroidUpnpServiceConfiguration;
import org.jupnp.model.Namespace;
import org.jupnp.model.meta.Device;
import org.jupnp.model.meta.Service;
import org.jupnp.model.types.ServiceType;
import org.jupnp.model.types.UDAServiceType;
import org.jupnp.transport.spi.NetworkAddressFactory;
import org.jupnp.transport.spi.StreamClient;
import org.jupnp.transport.spi.StreamServer;

import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.URI;

import apincer.android.mmate.dlna.transport.JLHttpUPnpStreamServer;
import apincer.android.mmate.dlna.transport.OKHttpUPnpStreamingClient;
import apincer.android.mmate.dlna.transport.StreamServerConfigurationImpl;
import apincer.android.mmate.dlna.transport.StreamClientConfigurationImpl;

class MediaServerConfiguration extends AndroidUpnpServiceConfiguration {
    public static int STREAM_SERVER_PORT = 2869;
    MediaServerConfiguration() {
        super(STREAM_SERVER_PORT, 0);
    }

    @Override
    protected NetworkAddressFactory createNetworkAddressFactory(int streamListenPort, int multicastResponsePort) {
        return new MusicMateNetworkAddressFactory(streamListenPort, multicastResponsePort);
    }

    @Override
    public ServiceType[] getExclusiveServiceTypes() {

        return new ServiceType[]{new UDAServiceType("ContentDirectory"), new UDAServiceType("ConnectionManager"), new UDAServiceType("X_MS_MediaReceiverRegistrar")};
    }

    @Override
    public StreamServer<StreamServerConfigurationImpl> createStreamServer(NetworkAddressFactory networkAddressFactory) {
        return new JLHttpUPnpStreamServer(new StreamServerConfigurationImpl(networkAddressFactory.getStreamListenPort()));
    }

    @Override
    public StreamClient<StreamClientConfigurationImpl> createStreamClient() {
        return new OKHttpUPnpStreamingClient(
                new StreamClientConfigurationImpl(
                        getSyncProtocolExecutorService(),
                        10,
                        5
                )
        );
    }

    @Override
    public int getRegistryMaintenanceIntervalMillis() {
        return 7000; // Preserve battery on Android, only run every 7 seconds
    }

    @Override
    public int getAliveIntervalMillis() {
        // to solve same controller not see the server, NOTIFY alive message every 5 seconds
        return 5000;
    }

    @Override
    protected Namespace createNamespace() {
        // For the Jetty server, this is the servlet context path
        return new Namespace("/upnp") {
            @Override
            public URI getDescriptorPath(Device device) {
                return appendPathToBaseURI(getDevicePath(device.getRoot()) + DESCRIPTOR_FILE+".xml");
            }
            @Override
            public String getDescriptorPathString(Device device) {
                return decodedPath + getDevicePath(device.getRoot()) + DESCRIPTOR_FILE+".xml";
            }

            @Override
            public URI getDescriptorPath(Service service) {
                return appendPathToBaseURI(getServicePath(service) + DESCRIPTOR_FILE+".xml");
            }
        };
    }

    private static class MusicMateNetworkAddressFactory extends AndroidNetworkAddressFactory {
        public MusicMateNetworkAddressFactory(int streamListenPort, int multicastResponsePort) {
            super(streamListenPort,multicastResponsePort);
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
}
