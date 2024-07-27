package apincer.android.mmate.dlna;

import static apincer.android.mmate.dlna.MediaServerService.IPV4_PATTERN;

import org.jupnp.android.AndroidNetworkAddressFactory;
import org.jupnp.android.AndroidUpnpServiceConfiguration;
import org.jupnp.model.types.ServiceType;
import org.jupnp.model.types.UDAServiceType;
import org.jupnp.transport.spi.NetworkAddressFactory;
import org.jupnp.transport.spi.StreamClient;
import org.jupnp.transport.spi.StreamServer;

import java.net.InetAddress;
import java.net.NetworkInterface;

import apincer.android.mmate.dlna.spi.StreamServerConfigurationImpl;
import apincer.android.mmate.dlna.spi.StreamServerImpl;
import apincer.android.mmate.dlna.spi.StreamingClientConfigurationImpl;
import apincer.android.mmate.dlna.spi.StreamingClientImpl;

class MediaServerServiceConfiguration extends AndroidUpnpServiceConfiguration {

    MediaServerServiceConfiguration() {
        super();
    }

    @Override
    protected NetworkAddressFactory createNetworkAddressFactory(int streamListenPort, int multicastResponsePort) {
       // return super.createNetworkAddressFactory(MediaServerService.STREAM_SERVER_PORT, multicastResponsePort);
        return new MusicMateNetworkAddressFactory(streamListenPort, multicastResponsePort);
    }

    @Override
    public ServiceType[] getExclusiveServiceTypes() {

        return new ServiceType[]{new UDAServiceType("ContentDirectory"), new UDAServiceType("ConnectionManager"), new UDAServiceType("X_MS_MediaReceiverRegistrar")};
    }

    @Override
    public StreamServer<StreamServerConfigurationImpl> createStreamServer(NetworkAddressFactory networkAddressFactory) {
        return new StreamServerImpl(new StreamServerConfigurationImpl(networkAddressFactory.getStreamListenPort())
        );
    }

    @Override
    public StreamClient<StreamingClientConfigurationImpl> createStreamClient() {
        return new StreamingClientImpl(
                new StreamingClientConfigurationImpl(
                        getSyncProtocolExecutorService()
                )
        );
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
