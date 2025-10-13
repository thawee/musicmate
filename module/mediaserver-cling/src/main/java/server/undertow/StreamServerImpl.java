package apincer.android.mmate.server.undertow;
import android.util.Log;

import org.fourthline.cling.android.AndroidNetworkAddressFactory;
import org.fourthline.cling.model.message.Connection;
import org.fourthline.cling.model.types.HostPort;
import org.fourthline.cling.transport.Router;
import org.fourthline.cling.transport.impl.StreamServerConfigurationImpl;
import org.fourthline.cling.transport.spi.InitializationException;
import org.fourthline.cling.transport.spi.NetworkAddressFactory;
import org.fourthline.cling.transport.spi.StreamServer;

import io.undertow.Undertow;
import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;

import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.List;

/**
 * @author Jason Mahdjoub
 * @since 1.2.0
 */
public class StreamServerImpl implements StreamServer<StreamServerConfigurationImpl> {
    final private static String TAG = "StreamServerImpl";

    final protected StreamServerConfigurationImpl configuration;
    protected Undertow server=null;
    private boolean started=false;
    private final List<HostPort> hostPorts=new ArrayList<>();

    public StreamServerImpl(StreamServerConfigurationImpl configuration) {
        this.configuration = configuration;
    }

    /*
    @Override
    synchronized public void init(InetAddress bindAddress, Router router, NetworkAddressFactory networkAddressFactory) throws InitializationException {
        try {
            hostPorts.add(new HostPort(bindAddress.getHostAddress(), configuration.getListenPort()));
            if (started) {
                stop();
            }
            Undertow.Builder b=Undertow.builder();
            for(HostPort hp : hostPorts)
                b.addHttpListener(hp.getPort(), hp.getHost());
            server = b.setHandler(new RequestHttpHandler(router, networkAddressFactory))
                    .build();

        } catch (Exception ex) {
            throw new InitializationException("Could not initialize " + getClass().getSimpleName() + ": " + ex, ex);
        }
    } */

    @Override
    public void init(InetAddress bindAddress, Router router) throws InitializationException {
        try {
            hostPorts.add(new HostPort(bindAddress.getHostAddress(), configuration.getListenPort()));
            if (started) {
                stop();
            }
            Undertow.Builder b=Undertow.builder();
            for(HostPort hp : hostPorts)
                b.addHttpListener(hp.getPort(), hp.getHost());
            server = b.setHandler(new RequestHttpHandler(router, new AndroidNetworkAddressFactory(configuration.getListenPort())))
                    .build();

        } catch (Exception ex) {
            throw new InitializationException("Could not initialize " + getClass().getSimpleName() + ": " + ex, ex);
        }
    }

    @Override
    synchronized public int getPort() {
        if (configuration.getListenPort()==0)
            return ((InetSocketAddress)server.getListenerInfo().get(0).getAddress()).getPort();
        return configuration.getListenPort();
    }

    @Override
    public StreamServerConfigurationImpl getConfiguration() {
        return configuration;
    }

    @Override
    synchronized public void run() {
        Log.d(TAG, "Starting StreamServer...");
        // Starts a new thread but inherits the properties of the calling thread
        server.start();
        started=true;
        Log.i(TAG, "Created server (for receiving TCP streams) on: " + server.getListenerInfo());
    }

    @Override
    synchronized public void stop() {
        if (server != null)
        {
            try {
                Log.d(TAG, "Stopping StreamServer...");
                server.stop();
            }
            finally {
                server=null;
                started=false;
            }
        }
    }

    protected class RequestHttpHandler implements HttpHandler {

        private final Router router;
        private final NetworkAddressFactory networkAddressFactory;

        public RequestHttpHandler(Router router, NetworkAddressFactory networkAddressFactory) {
            this.router = router;
            this.networkAddressFactory=networkAddressFactory;
        }

        // This is executed in the request receiving thread!
        @Override
        public void handleRequest(HttpServerExchange exchange) throws Exception {
            InetSocketAddress isa=exchange.getSourceAddress();
            if (isa==null)
                return;
            InetAddress receivedOnLocalAddress =
                    networkAddressFactory.getLocalAddress(
                            null,
                            isa.getAddress() instanceof Inet6Address,
                            isa.getAddress()
                    );
            if (receivedOnLocalAddress==null)
                return;
            // And we pass control to the service, which will (hopefully) start a new thread immediately, so we can
            // continue the receiving thread ASAP
            Log.d(TAG, "Received HTTP exchange: " + exchange.getRequestMethod() + " " + exchange.getRequestURI());
            router.received(
                    new UndertowUpnpStream(router.getProtocolFactory(), exchange) {
                        @Override
                        protected Connection createConnection() {
                            return new HttpServerConnection(exchange);
                        }
                    }
            );
        }
    }

    /**
     * Logs a warning and returns <code>true</code>, we can't access the socket using the awful JDK webserver API.
     * <p>
     * Override this method if you know how to do it.

     */
    protected boolean isConnectionOpen(HttpServerExchange exchange) {
        return exchange.getConnection().isOpen();
    }

    protected class HttpServerConnection implements Connection {

        protected HttpServerExchange exchange;

        public HttpServerConnection(HttpServerExchange exchange) {
            this.exchange = exchange;
        }

        @Override
        public boolean isOpen() {
            return isConnectionOpen(exchange);
        }

        @Override
        public InetAddress getRemoteAddress() {
            return exchange.getSourceAddress() != null
                    ? exchange.getSourceAddress().getAddress()
                    : null;
        }

        @Override
        public InetAddress getLocalAddress() {
            return exchange.getDestinationAddress() != null
                    ? exchange.getDestinationAddress().getAddress()
                    : null;
        }
    }
}