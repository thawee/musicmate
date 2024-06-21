package apincer.android.mmate.dlna;

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

public class MusicMateStreamServerImpl implements StreamServer<MusicMateStreamServerConfigurationImpl> {


    final protected MusicMateStreamServerConfigurationImpl configuration;
   // private final ProtocolFactory protocolFactory;
    protected int localPort;
    private HttpAsyncServer server;

    public MusicMateStreamServerImpl(MusicMateStreamServerConfigurationImpl configuration) {
        this.configuration = configuration;
        this.localPort = configuration.getListenPort();
       // this.protocolFactory = protocolFactory;
    }

    public MusicMateStreamServerConfigurationImpl getConfiguration() {
        return configuration;
    }

    synchronized public void init(InetAddress bindAddress, final Router router) throws InitializationException {
        Thread thread = new Thread(new Runnable() {

            @Override
            public void run() {
                try {
                    try {

                        Log.d(getClass().getName(), "Adding connector: " + bindAddress + ":" + getConfiguration().getListenPort());

                        IOReactorConfig config = IOReactorConfig.custom()
                                .setSoTimeout(getConfiguration().getAsyncTimeoutSeconds(), TimeUnit.SECONDS)
                                .setTcpNoDelay(true)
                                .build();
                        server = H2ServerBootstrap.bootstrap()
                                .setCanonicalHostName(bindAddress.getHostAddress())
                                .setIOReactorConfig(config)
                                .register(router.getConfiguration().getNamespace().getBasePath().getPath() + "/*", new MusicMateStreamServerRequestHandler(router.getProtocolFactory()))
                                .create();
                        server.listen(new InetSocketAddress(getConfiguration().getListenPort()), URIScheme.HTTP);
                        server.start();
                    } catch (Exception ex) {
                        throw new InitializationException("Could not initialize " + getClass().getSimpleName() + ": " + ex, ex);
                    }
                } catch (Exception e) {
                    throw new InitializationException("Could run init thread " + getClass().getSimpleName() + ": " + e, e);
                }
            }
        });

        thread.start();

    }

    synchronized public int getPort() {
        return this.localPort;
    }

    synchronized public void stop() {

        server.initiateShutdown();
        try {
            server.awaitShutdown(TimeValue.ofSeconds(3));
        } catch (InterruptedException e) {
            Log.w(getClass().getName(), "got exception on stream server stop ", e);
        }
    }

    public void run() {
        //do nothing all stuff done in init
    }

}