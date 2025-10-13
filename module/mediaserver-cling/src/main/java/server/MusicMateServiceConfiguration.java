package apincer.android.mmate.server;

import android.os.Build;

import org.fourthline.cling.DefaultUpnpServiceConfiguration;
import org.fourthline.cling.android.AndroidNetworkAddressFactory;
import org.fourthline.cling.binding.xml.DeviceDescriptorBinder;
import org.fourthline.cling.binding.xml.RecoveringUDA10DeviceDescriptorBinderImpl;
import org.fourthline.cling.binding.xml.ServiceDescriptorBinder;
import org.fourthline.cling.binding.xml.UDA10ServiceDescriptorBinderSAXImpl;
import org.fourthline.cling.model.ModelUtil;
import org.fourthline.cling.model.Namespace;
import org.fourthline.cling.model.ServerClientTokens;
import org.fourthline.cling.transport.impl.RecoveringGENAEventProcessorImpl;
import org.fourthline.cling.transport.impl.RecoveringSOAPActionProcessorImpl;
import org.fourthline.cling.transport.impl.StreamServerConfigurationImpl;
import org.fourthline.cling.transport.spi.GENAEventProcessor;
import org.fourthline.cling.transport.spi.NetworkAddressFactory;
import org.fourthline.cling.transport.spi.SOAPActionProcessor;
import org.fourthline.cling.transport.spi.StreamClient;
import org.fourthline.cling.transport.spi.StreamServer;
import org.xnio.XnioWorker;

import java.util.concurrent.ExecutorService;

import apincer.android.mmate.server.undertow.StreamClientConfigurationImpl;
import apincer.android.mmate.server.undertow.StreamClientImpl;
import apincer.android.mmate.server.undertow.StreamServerImpl;

public class MusicMateServiceConfiguration extends DefaultUpnpServiceConfiguration {
    public MusicMateServiceConfiguration() {
        this(0);
    }

    public MusicMateServiceConfiguration(int streamListenPort) {
        super(streamListenPort, false);
        System.setProperty("org.xml.sax.driver", "org.xmlpull.v1.sax2.Driver");
    }

    protected NetworkAddressFactory createNetworkAddressFactory(int streamListenPort) {
        return new AndroidNetworkAddressFactory(streamListenPort);
    }

    protected Namespace createNamespace() {
        return new Namespace("/upnp");
    }

   /* public StreamClient createStreamClient() {
        return new StreamClientImpl(new StreamClientConfigurationImpl(this.getD()) {
            public String getUserAgentValue(int majorVersion, int minorVersion) {
                ServerClientTokens tokens = new ServerClientTokens(majorVersion, minorVersion);
                tokens.setOsName("Android");
                tokens.setOsVersion(Build.VERSION.RELEASE);
                return tokens.toString();
            }
        });
    } */

    @Override
    public StreamClient<?> createStreamClient() {
        ExecutorService syncProtocolExecutorService = getSyncProtocolExecutorService();
        int timeoutSeconds = 300;
        XnioWorker worker;
        if (syncProtocolExecutorService instanceof XnioWorker)
            worker=(XnioWorker)syncProtocolExecutorService;
        else
            throw new IllegalArgumentException("syncProtocolExecutorService should be a XnioWorker class and not : "+syncProtocolExecutorService.getClass());

        return new StreamClientImpl(
                new StreamClientConfigurationImpl(
                        worker
                        ,timeoutSeconds
                ) {
                    @Override
                    public String getUserAgentValue(int majorVersion, int minorVersion) {
                        if (ModelUtil.ANDROID_EMULATOR || ModelUtil.ANDROID_RUNTIME) {
                            // TODO: UPNP VIOLATION: Synology NAS requires User-Agent to contain
                            // "Android" to return DLNA protocolInfo required to stream to Samsung TV
                            // see: http://two-play.com/forums/viewtopic.php?f=6&t=81
                            ServerClientTokens tokens = new ServerClientTokens(majorVersion, minorVersion);
                            tokens.setOsName("Android");
                            tokens.setOsVersion(Build.VERSION.RELEASE);
                            return tokens.toString();
                        }
                        else
                            return super.getUserAgentValue(majorVersion, minorVersion);
                    }
                }
        );
    }

    public StreamServer<?> createStreamServer(NetworkAddressFactory networkAddressFactory) {
        return new StreamServerImpl(
                new StreamServerConfigurationImpl(
                        networkAddressFactory.getStreamListenPort()
                )
        );
       // return new AsyncServletStreamServerImpl(new AsyncServletStreamServerConfigurationImpl(JettyServletContainer.INSTANCE, networkAddressFactory.getStreamListenPort()));
    }

    protected DeviceDescriptorBinder createDeviceDescriptorBinderUDA10() {
        return new RecoveringUDA10DeviceDescriptorBinderImpl();
    }

    protected ServiceDescriptorBinder createServiceDescriptorBinderUDA10() {
        return new UDA10ServiceDescriptorBinderSAXImpl();
    }

    protected SOAPActionProcessor createSOAPActionProcessor() {
        return new RecoveringSOAPActionProcessorImpl();
    }

    protected GENAEventProcessor createGENAEventProcessor() {
        return new RecoveringGENAEventProcessorImpl();
    }

    public int getRegistryMaintenanceIntervalMillis() {
        return 3000;
    }
}
