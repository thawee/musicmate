package apincer.android.mmate.dlna;

import org.jupnp.android.AndroidUpnpServiceConfiguration;
import org.jupnp.model.types.ServiceType;
import org.jupnp.model.types.UDAServiceType;
import org.jupnp.transport.spi.NetworkAddressFactory;
import org.jupnp.transport.spi.StreamClient;
import org.jupnp.transport.spi.StreamServer;

class MusicMateServiceConfiguration extends AndroidUpnpServiceConfiguration {

    MusicMateServiceConfiguration() {
        super();
    }

    @Override
    protected NetworkAddressFactory createNetworkAddressFactory(int streamListenPort, int multicastResponsePort) {
        return super.createNetworkAddressFactory(MediaServerService.STREAM_SERVER_PORT, multicastResponsePort);
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
}
