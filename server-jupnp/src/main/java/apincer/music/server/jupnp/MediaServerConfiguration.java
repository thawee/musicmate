package apincer.music.server.jupnp;

import static apincer.music.core.server.BaseServer.UPNP_SERVER_PORT;

import android.content.Context;

import org.jupnp.android.AndroidUpnpServiceConfiguration;
import org.jupnp.model.Namespace;
import org.jupnp.model.meta.Device;
import org.jupnp.model.meta.Service;
import org.jupnp.model.types.ServiceType;
import org.jupnp.transport.spi.NetworkAddressFactory;
import org.jupnp.transport.spi.StreamClient;
import org.jupnp.transport.spi.StreamServer;
import org.jupnp.transport.spi.StreamServerConfiguration;

import java.net.URI;

import apincer.music.core.server.spi.UpnpServer;
import apincer.music.core.server.spi.WebServer;
import apincer.music.server.jupnp.transport.OKHttpStreamingClient;
import apincer.music.server.jupnp.transport.StreamClientConfigurationImpl;
import apincer.music.server.jupnp.transport.StreamServerConfigurationImpl;
import apincer.music.server.jupnp.transport.StreamServerImpl;

public class MediaServerConfiguration extends AndroidUpnpServiceConfiguration {

    //Injected
    private final Context context;
    private  final UpnpServer upnpServer;
    private final WebServer contentServer;

    public MediaServerConfiguration(Context context, UpnpServer upnpServer, WebServer contentServer) {
        super(UPNP_SERVER_PORT, 0);
        this.context = context;
        this.upnpServer = upnpServer;
        this.contentServer = contentServer;
    }

    @Override
    protected NetworkAddressFactory createNetworkAddressFactory(int streamListenPort, int multicastResponsePort) {
        return new MediaServerAddressFactory(streamListenPort, multicastResponsePort);
    }

    @Override
    public ServiceType[] getExclusiveServiceTypes() {
        // should return empty array, to keep all services for endpoint/renderer
        return new ServiceType[0];
    }

    @Override
    public StreamServer<StreamServerConfiguration> createStreamServer(NetworkAddressFactory networkAddressFactory) {
        return new StreamServerImpl(context,upnpServer, contentServer, new StreamServerConfigurationImpl(networkAddressFactory.getStreamListenPort()));
    }

    @Override
    public StreamClient<StreamClientConfigurationImpl> createStreamClient() {
        // Increased timeouts for better reliability with RoPieeeXL streaming
        return new OKHttpStreamingClient(
                new StreamClientConfigurationImpl(
                        getSyncProtocolExecutorService(),
                        15,    // Increased from 10 to 15 seconds for connection timeout
                        8      // Increased from 5 to 8 seconds for data timeout
                ));
      /*  return new JettyStreamingClientImpl(
                new StreamClientConfigurationImpl(
                getSyncProtocolExecutorService(),
                        15,    // Increased from 10 to 15 seconds for connection timeout
                        8      // Increased from 5 to 8 seconds for data timeout
        )); */
       /* return new NettyStreamingClientImpl(
                new StreamClientConfigurationImpl(
                        getSyncProtocolExecutorService(),
                        15,    // Increased from 10 to 15 seconds for connection timeout
                        8      // Increased from 5 to 8 seconds for data timeout
                )); */
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
         final String DESC_XML = "/description.xml";
        final String SCPD_XML = "/scpd.xml";
        return new Namespace("/dms") {
            @Override
            public URI getDescriptorPath(Device device) {
                return appendPathToBaseURI(getDevicePath(device.getRoot()) + DESC_XML);
            }
            @Override
            public String getDescriptorPathString(Device device) {
                return decodedPath + getDevicePath(device.getRoot()) + DESC_XML;
            }

            @Override
            public URI getDescriptorPath(Service service) {
                return appendPathToBaseURI(getServicePath(service) + SCPD_XML);
            }

            @Override
            protected String getDevicePath(Device device) {
                if (device.getIdentity().getUdn() == null) {
                    throw new IllegalStateException("Can't generate local URI prefix without UDN");
                }
                // return empty string
                return "";
            }
        };
    }
}
