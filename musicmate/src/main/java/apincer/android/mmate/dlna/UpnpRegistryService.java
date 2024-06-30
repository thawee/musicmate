package apincer.android.mmate.dlna;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;
import android.util.Log;

import org.jupnp.UpnpService;
import org.jupnp.UpnpServiceImpl;
import org.jupnp.android.AndroidUpnpServiceConfiguration;
import org.jupnp.model.types.ServiceType;
import org.jupnp.model.types.UDAServiceType;
import org.jupnp.transport.spi.NetworkAddressFactory;
import org.jupnp.transport.spi.StreamClient;
import org.jupnp.transport.spi.StreamServer;

import apincer.android.mmate.work.MusicMateExecutors;

/**
 * This is an android service to provide access to an upnp registry.
 *
 * @author Tobias Schöne (openbit)
 */
public class UpnpRegistryService extends Service {
    private static final String TAG = "UpnpRegistryService";
    public static int STREAM_SERVER_PORT = 2869;
    private UpnpService upnpService;
    protected IBinder binder = new UpnpRegistryServiceBinder();

    /**
     * Starts the UPnP service.
     */
    @Override
    public void onCreate() {
        long start = System.currentTimeMillis();
        super.onCreate();

        upnpService = new UpnpServiceImpl(new MusicMateServiceConfiguration());
        upnpService.startup();

        Log.d(TAG, "on start took: " + (System.currentTimeMillis() - start));
    }

    public UpnpService getUpnpService() {
        return upnpService;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return binder;
    }

    /**
     * Stops the UPnP service, when the last Activity unbinds from this Service.
     */
    @Override
    public void onDestroy() {
        MusicMateExecutors.db(() -> upnpService.shutdown());
        super.onDestroy();
    }

    protected class UpnpRegistryServiceBinder extends android.os.Binder {

        public UpnpRegistryService getService() {
            return UpnpRegistryService.this;
        }
    }

    private static class MusicMateServiceConfiguration extends AndroidUpnpServiceConfiguration {

        private MusicMateServiceConfiguration() {
            super();
        }

        @Override
        protected NetworkAddressFactory createNetworkAddressFactory(int streamListenPort, int multicastResponsePort) {
            return super.createNetworkAddressFactory(STREAM_SERVER_PORT, multicastResponsePort);
        }

        @Override
        public ServiceType[] getExclusiveServiceTypes() {

            return new ServiceType[]{new UDAServiceType("ContentDirectory"), new UDAServiceType("ConnectionManager"), new UDAServiceType("X_MS_MediaReceiverRegistrar")};
        }

        @Override
        public StreamServer<StreamServerConfigurationImpl> createStreamServer(NetworkAddressFactory networkAddressFactory) {
            return new StreamServerImpl( new StreamServerConfigurationImpl(networkAddressFactory.getStreamListenPort())
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
}