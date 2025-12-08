package apincer.android.jupnp.transport.jetty;

import android.content.Context;
import android.util.Log;

import org.eclipse.jetty.http.HttpHeader;
import org.eclipse.jetty.http.HttpStatus;
import org.eclipse.jetty.io.Content;
import org.eclipse.jetty.server.Connector;
import org.eclipse.jetty.server.Handler;
import org.eclipse.jetty.server.HttpConfiguration;
import org.eclipse.jetty.server.HttpConnectionFactory;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.Response;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.util.Callback;
import org.eclipse.jetty.util.thread.QueuedThreadPool;
import org.jupnp.model.message.StreamRequestMessage;
import org.jupnp.model.message.StreamResponseMessage;
import org.jupnp.model.message.UpnpHeaders;
import org.jupnp.model.message.UpnpMessage;
import org.jupnp.model.message.UpnpRequest;
import org.jupnp.protocol.ProtocolFactory;
import org.jupnp.protocol.ReceivingSync;
import org.jupnp.transport.Router;
import org.jupnp.transport.spi.InitializationException;

import java.io.IOException;
import java.net.InetAddress;
import java.net.URI;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.Map;

import apincer.music.core.repository.FileRepository;
import apincer.music.core.repository.TagRepository;
import apincer.music.core.server.BaseServer;
import apincer.music.core.server.spi.UpnpServer;

public class JettyUPnpServerImpl extends BaseServer implements UpnpServer {
    private static final String TAG = "JettyUPnpServer";

    // Jetty tuning parameters
    private static final int OUTPUT_BUFFER_SIZE = 65536; // 64KB
    private static final int MIN_THREADS = 4;
    private static final int IDLE_TIMEOUT = 30000; // 30 seconds

    private Server server;
    private Router router;

    public JettyUPnpServerImpl(Context context, FileRepository fileRepos, TagRepository tagRepos) {
        super(context,fileRepos, tagRepos);
        addLibInfo("Jetty", "12.1.2");
    }

    @Override
    public void initServer(InetAddress bindAddress, Object router) throws InitializationException {
        this.router = (Router) router;
        Thread serverThread = new Thread(() -> {
            try {
               // Log.i(TAG, "Starting UPnPServer (Jetty) on " + bindAddress.getHostAddress() + ":" + getListenPort());

                // Calculate threads based on cores
                int cores = Runtime.getRuntime().availableProcessors();
                int maxThreads = Math.max(MIN_THREADS, cores * 4); // giving enough threads for blocking operations

                QueuedThreadPool threadPool = new QueuedThreadPool(maxThreads, MIN_THREADS, IDLE_TIMEOUT);
                threadPool.setName("jetty-upnp-server");

                server = new Server(threadPool);

                HttpConfiguration httpConfig = new HttpConfiguration();
                httpConfig.setOutputBufferSize(OUTPUT_BUFFER_SIZE);
                httpConfig.setSendServerVersion(false);
                httpConfig.setRequestHeaderSize(8192); // Increased header size

                // IMPROVEMENT (CRITICAL BUG FIX): Do not use try-with-resources on the connector.
                ServerConnector connector = new ServerConnector(server, new HttpConnectionFactory(httpConfig));
                connector.setHost("0.0.0.0");
                connector.setPort(getListenPort());
                connector.setIdleTimeout(60000);
                connector.setAcceptQueueSize(32);

                server.setConnectors(new Connector[]{connector});

                // Set the UPnP handler for all contexts
                server.setHandler(new UPnpHandler());
                server.start();
                Log.i(TAG, "UPnPServer started on " + bindAddress.getHostAddress() + ":" + WEB_SERVER_PORT +" successfully.");

                server.join();

            } catch (Exception e) {
                Log.e(TAG, "Failed to start or run UPnPServer", e);
            }
        });
        serverThread.setName("jetty-upnp-runner");
        serverThread.start();
    }

    @Override
    public void stopServer() {
        Log.i(TAG, "Stopping UPnPServer (Jetty)");
        try {
            if (server != null && server.isRunning()) {
                server.stop();
            }
        } catch (Exception e) {
            Log.e(TAG, "Error stopping UPnPServer", e);
        }
    }

    @Override
    public int getListenPort() {
        return UPNP_SERVER_PORT;
    }

    @Override
    public String getComponentName() {
        return "UPnP/1.0";
    }

    private class UPnpHandler extends Handler.Abstract {
        private final ProtocolFactory protocolFactory;

        private UPnpHandler() {
            // Get the protocol factory from the base class
            this.protocolFactory = getProtocolFactory();
        }

        @Override
        public boolean handle(Request request, Response response, Callback callback) {
            try {
                StreamRequestMessage requestMessage = readRequestMessage(request);

                // IMPROVEMENT: Use the protocol factory to create and run the right processor.
                // This is the most robust way to handle requests in jupnp.
                ReceivingSync protocol = protocolFactory.createReceivingSync(requestMessage);
                protocol.run();
                StreamResponseMessage responseMessage = protocol.getOutputMessage();

                if (responseMessage != null) {
                    writeResponseMessage(responseMessage, response, callback);
                } else {
                    sendErrorResponse(response, HttpStatus.NOT_FOUND_404, "Resource Not Found", callback);
                }
                return true;
            } catch (Exception ex) {
                Log.e(TAG, "Error processing UPnP request: " + request.getHttpURI(), ex);
                sendErrorResponse(response, HttpStatus.INTERNAL_SERVER_ERROR_500, "Internal Server Error", callback);
                return true;
            }
        }

        private void writeResponseMessage(StreamResponseMessage responseMessage, Response resp, Callback callback) {
            resp.setStatus(responseMessage.getOperation().getStatusCode());

            for (Map.Entry<String, List<String>> entry : responseMessage.getHeaders().entrySet()) {
                for (String value : entry.getValue()) {
                    resp.getHeaders().add(entry.getKey(), value);
                }
            }
            resp.getHeaders().put(HttpHeader.SERVER, getServerSignature(getComponentName()));

            resp.getHeaders().put(HttpHeader.CACHE_CONTROL, "no-cache, no-store, must-revalidate");
            resp.getHeaders().put(HttpHeader.PRAGMA, "no-cache");

            if (responseMessage.hasBody()) {
                // Use non-blocking write with callback
                Content.Sink.write(resp, true, ByteBuffer.wrap(responseMessage.getBodyBytes()).toString(), callback);
            } else {
                callback.succeeded();
            }
        }

        private StreamRequestMessage readRequestMessage(Request req) throws IOException {
            URI uri;
            try {
                uri = URI.create(req.getHttpURI().getPathQuery());
            } catch (IllegalArgumentException ex) {
                throw new IOException("Invalid request URI: " + req.getHttpURI().getPathQuery(), ex);
            }

            StreamRequestMessage requestMessage = new StreamRequestMessage(
                    UpnpRequest.Method.getByHttpName(req.getMethod()),
                    uri
            );

            UpnpHeaders headers = new UpnpHeaders();
            req.getHeaders().stream().forEach(field -> headers.add(field.getName(), field.getValue()));
            requestMessage.setHeaders(headers);

            if (req.getLength() > 0) {
                 // Efficiently read content using InputStream rather than buffering everything if possible,
                 // but StreamRequestMessage often requires a full body.
                 // We stick to simple reading here as optimization for huge UPnP bodies is rarely needed (metadata is small).
                 // However, we avoid deprecated or inefficient calls.
                 byte[] bodyBytes = Content.Source.asByteBuffer(req).array();
                if (requestMessage.isContentTypeMissingOrText()) {
                    requestMessage.setBodyCharacters(bodyBytes);
                } else {
                    requestMessage.setBody(UpnpMessage.BodyType.BYTES, bodyBytes);
                }
            }
            return requestMessage;
        }

        private void sendErrorResponse(Response response, int status, String message, Callback callback) {
            response.setStatus(status);
            response.getHeaders().put(HttpHeader.CONTENT_TYPE, "text/html; charset=utf-8");
            String body = "<h1>" + status + " - " + message + "</h1>";
            Content.Sink.write(response, true, body, callback);
        }
    }

    private ProtocolFactory getProtocolFactory() {
        return router.getProtocolFactory();
    }
}