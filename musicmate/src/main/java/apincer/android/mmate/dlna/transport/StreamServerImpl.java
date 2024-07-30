package apincer.android.mmate.dlna.transport;

import android.util.Log;

import org.apache.hc.core5.http.URIScheme;
import org.apache.hc.core5.http.impl.bootstrap.HttpAsyncServer;
import org.apache.hc.core5.http2.impl.nio.bootstrap.H2ServerBootstrap;
import org.apache.hc.core5.reactor.IOReactorConfig;
import org.apache.hc.core5.util.TimeValue;
import org.jupnp.transport.Router;
import org.jupnp.transport.spi.InitializationException;
import org.jupnp.transport.spi.StreamServer;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.concurrent.TimeUnit;

public class StreamServerImpl implements StreamServer<StreamServerConfigurationImpl> {
    private static final String TAG = "StreamServerImpl";

    final protected StreamServerConfigurationImpl configuration;
    protected int localPort;
    private HttpAsyncServer server;

    public StreamServerImpl(StreamServerConfigurationImpl configuration) {
        this.configuration = configuration;
        this.localPort = configuration.getListenPort();
    }

    public StreamServerConfigurationImpl getConfiguration() {
        return configuration;
    }

    synchronized public void init(InetAddress bindAddress, final Router router) throws InitializationException {
        /*
        if(!MediaServerService.getIpAddress().equals(bindAddress.getHostAddress())) {
            // start ssdp on wifi network only
            Log.d(TAG, "Skip stream server connector: " + bindAddress + ":" + getConfiguration().getListenPort());
            return;
        } */

        Thread thread = new Thread(new Runnable() {

            @Override
            public void run() {
                    try {
                        Log.d(TAG, "Adding multicast server connector: " + bindAddress.getHostAddress() + ":" + getConfiguration().getListenPort());

                        IOReactorConfig config = IOReactorConfig.custom()
                                .setSoTimeout(getConfiguration().getAsyncTimeoutSeconds(), TimeUnit.SECONDS)
                                .setTcpNoDelay(true)
                                .build();
                        server = H2ServerBootstrap.bootstrap()
                                .setCanonicalHostName(bindAddress.getHostAddress())
                                .setIOReactorConfig(config)
                                .register(router.getConfiguration().getNamespace().getBasePath().getPath() + "/*", new StreamServerHandler(router.getProtocolFactory()))
                                .create();
                        server.listen(new InetSocketAddress(getConfiguration().getListenPort()), URIScheme.HTTP);
                        server.start();
                    } catch (Exception ex) {
                        throw new InitializationException("Could not initialize " + getClass().getSimpleName() + ": " + ex, ex);
                    }
            }
        });

        thread.start();

    }

    synchronized public int getPort() {
        return this.localPort;
    }

    synchronized public void stop() {
        if(server != null) {
            server.initiateShutdown();
            try {
                server.awaitShutdown(TimeValue.ofSeconds(3));
            } catch (InterruptedException e) {
                Log.w(TAG, "got exception on stream server stop ", e);
            }
        }
    }

    public void run() {
        //do nothing all stuff done in init
    }

}