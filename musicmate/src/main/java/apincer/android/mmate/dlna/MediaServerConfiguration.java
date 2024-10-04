package apincer.android.mmate.dlna;

import android.content.Context;

import org.jupnp.android.AndroidUpnpServiceConfiguration;
import org.jupnp.model.Namespace;
import org.jupnp.model.message.UpnpHeaders;
import org.jupnp.model.message.header.UpnpHeader;
import org.jupnp.model.meta.Device;
import org.jupnp.model.meta.RemoteDevice;
import org.jupnp.model.meta.RemoteDeviceIdentity;
import org.jupnp.model.meta.Service;
import org.jupnp.model.types.ServiceType;
import org.jupnp.model.types.UDAServiceType;
import org.jupnp.registry.DefaultRegistryListener;
import org.jupnp.registry.Registry;
import org.jupnp.registry.RegistryListener;
import org.jupnp.transport.spi.NetworkAddressFactory;
import org.jupnp.transport.spi.StreamClient;
import org.jupnp.transport.spi.StreamServer;

import java.net.URI;

import apincer.android.mmate.dlna.android.WifiNetworkAddressFactory;
import apincer.android.mmate.dlna.transport.JLHStreamServerImpl;
import apincer.android.mmate.dlna.transport.OKHttpUPnpStreamingClient;
import apincer.android.mmate.dlna.transport.StreamClientConfigurationImpl;
import apincer.android.mmate.dlna.transport.StreamServerConfigurationImpl;
import apincer.android.mmate.dlna.transport.StreamServerImpl;

public class MediaServerConfiguration extends AndroidUpnpServiceConfiguration {
    public static final int STREAM_SERVER_PORT = 49152; //2869;
    private final Context context;

    MediaServerConfiguration(Context context) {
        super(STREAM_SERVER_PORT, 0);
        this.context = context;
    }

    @Override
    protected NetworkAddressFactory createNetworkAddressFactory(int streamListenPort, int multicastResponsePort) {
        return new WifiNetworkAddressFactory(streamListenPort, multicastResponsePort);
    }

    @Override
    public ServiceType[] getExclusiveServiceTypes() {
        return new ServiceType[]{new UDAServiceType("ContentDirectory"), new UDAServiceType("ConnectionManager"), new UDAServiceType("X_MS_MediaReceiverRegistrar")};
    }

    @Override
    public StreamServer<StreamServerConfigurationImpl> createStreamServer(NetworkAddressFactory networkAddressFactory) {
       // return new HttpCoreStreamServer(context, new StreamServerConfigurationImpl(networkAddressFactory.getStreamListenPort()));
      //  return new StreamServerImpl(context, new StreamServerConfigurationImpl(networkAddressFactory.getStreamListenPort()));
          return new JLHStreamServerImpl(context, new StreamServerConfigurationImpl(networkAddressFactory.getStreamListenPort()));

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
                //StringBuilder s = new StringBuilder();
               // s.append(DEVICE); //.append("/");

               // s.append(URIUtil.encodePathSegment(device.getIdentity().getUdn().getIdentifierString()));
               // return s.toString();
                return "";
            }
        };
    }

    @Override
    public UpnpHeaders getDescriptorRetrievalHeaders(RemoteDeviceIdentity identity) {
        if (identity.getUdn().getIdentifierString().equals("aa-bb-cc-dd-ee-ff")) {
            UpnpHeaders headers = new UpnpHeaders();
            headers.add(UpnpHeader.Type.USER_AGENT.getHttpName(), "MyCustom/Agent");
            headers.add("X-Custom-Header", "foo");
            return headers;
        }
        return null;
    }

}
