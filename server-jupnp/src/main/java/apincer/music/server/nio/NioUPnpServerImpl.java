package apincer.music.server.nio;
import android.content.Context;
import android.util.Log;

import org.jupnp.model.message.*;
import org.jupnp.protocol.ProtocolFactory;
import org.jupnp.protocol.ReceivingSync;
import org.jupnp.transport.Router;

import java.net.InetAddress;
import java.net.URI;
import java.util.*;

import apincer.music.core.http.NioHttpServer;
import apincer.music.core.repository.FileRepository;
import apincer.music.core.repository.TagRepository;
import apincer.music.core.server.BaseServer;
import apincer.music.core.server.spi.UpnpServer;

public class NioUPnpServerImpl extends BaseServer implements UpnpServer {
    private final String serverSignature;

    /**
     * The Handler that bridges our NIO server with the jUPnP protocol stack.
     */
    class UpnpHandler implements NioHttpServer.Handler {
        private final ProtocolFactory protocolFactory;
        private long lastDateUpdate = 0;
        private String cachedDateString = "";

        public UpnpHandler(ProtocolFactory protocolFactory) {
            this.protocolFactory = protocolFactory;
        }
        
        private String getCachedDate() {
            long now = System.currentTimeMillis();
            // Cache the formatted date for 1 second to reduce GC churn
            if (now - lastDateUpdate > 1000) {
                cachedDateString = formatDate(now);
                lastDateUpdate = now;
            }
            return cachedDateString;
        }

        @Override
        public NioHttpServer.HttpResponse handle(NioHttpServer.HttpRequest request) {
            try {
                StreamRequestMessage requestMessage = readRequestMessage(request);
                // System.out.println("Processing new request message: " + requestMessage);

                ReceivingSync protocol = protocolFactory.createReceivingSync(requestMessage);
                protocol.run();
                StreamResponseMessage responseMessage = protocol.getOutputMessage();

                if (responseMessage != null) {
                    // System.out.println("Preparing HTTP response message: " + responseMessage);
                    return writeResponseMessage(responseMessage);
                } else {
                   // System.out.println("Sending HTTP response status: 404 - No response from protocol stack");
                    return new NioHttpServer.HttpResponse().setStatus(404, "Not Found");
                }
            } catch (Exception t) {
                Log.i(TAG, TAG+" - Exception occurred during UPnP stream processing: " + t.getMessage());
                return new NioHttpServer.HttpResponse().setStatus(500, "Internal Server Error").setBody(t.getMessage().getBytes());
            }
        }

        private NioHttpServer.HttpResponse writeResponseMessage(StreamResponseMessage responseMessage) {
            NioHttpServer.HttpResponse response = new NioHttpServer.HttpResponse();
            response.setStatus(responseMessage.getOperation().getStatusCode(), responseMessage.getOperation().getStatusMessage());

            // Headers
            for (Map.Entry<String, List<String>> entry : responseMessage.getHeaders().entrySet()) {
                response.addHeader(entry.getKey(), entry.getValue().get(entry.getValue().size() - 1));
            }

            //override header
            response.addHeader("Server", serverSignature);
            response.addHeader("Date", getCachedDate());

            // Body
            if (responseMessage.hasBody()) {
                response.setBody(responseMessage.getBodyBytes());
            }

            return response;
        }

        private StreamRequestMessage readRequestMessage(NioHttpServer.HttpRequest req) {
            StreamRequestMessage requestMessage;
            try {
                requestMessage = new StreamRequestMessage(
                        UpnpRequest.Method.getByHttpName(req.getMethod()),
                        URI.create(req.getPath())
                );
            } catch (IllegalArgumentException ex) {
                throw new RuntimeException("Invalid request URI: " + req.getPath(), ex);
            }

            if (requestMessage.getOperation().getMethod().equals(UpnpRequest.Method.UNKNOWN)) {
                throw new RuntimeException("Method not supported: " + req.getMethod());
            }

            UpnpHeaders headers = new UpnpHeaders();
            req.getHeaders().forEach(headers::add);
            requestMessage.setHeaders(headers);

            // Body
            byte[] bodyBytes = req.getBody();
            if (bodyBytes != null && bodyBytes.length > 0) {
                if (requestMessage.isContentTypeMissingOrText()) {
                    requestMessage.setBodyCharacters(bodyBytes);
                } else {
                    requestMessage.setBody(UpnpMessage.BodyType.BYTES, bodyBytes);
                }
            }
            return requestMessage;
        }
    }

    private static final String TAG = "NioUPnpServer";
    private final Object serverLock = new Object();
    private NioHttpServer server;

    private Thread serverThread;

    // Simplified constructor for pure Java environment
    public NioUPnpServerImpl(Context context, FileRepository fileRepos, TagRepository tagRepos) {
        super(context, fileRepos, tagRepos);
       // this.configuration = configuration;
        addLibInfo("NioHttpServer", "");
        serverSignature = getServerSignature();
    }

    @Override
    public void restartServer(InetAddress bindAddress, Object router) {
        synchronized (serverLock) {
            Log.d(TAG, "Restarting NioUPnp Server...");

            // 1. Full Stop
            stopServer();

            // 2. Small grace period for OS to release the socket
            try {
                Thread.sleep(200);
            } catch (InterruptedException ignored) {}

            // 3. Start New Instance
            try {
                initServer(bindAddress, router);
            } catch (Exception e) {
                Log.e(TAG, "Failed to restart server: " + e.getMessage());
            }
        }
    }

    public void initServer(InetAddress bindAddress, Object router) throws Exception {
        Router router1 = (Router) router;
        server = new NioHttpServer(getListenPort());

        NioHttpServer.Handler upnpHandler = new UpnpHandler(router1.getProtocolFactory());
        // Register the outermost layer as the fallback handler
        server.registerHttpHandler(upnpHandler);
        server.setMaxThread(2);
        
        // UPnP messages are small SOAP XMLs, we don't need the default 2MB request size
        server.setMaxRequestSize(64 * 1024); // 64KB is plenty for UPnP control messages
        server.setClientReadBufferSize(4 * 1024); // 4KB read buffer per connection to save memory
        server.setTcpNoDelay(true);

        serverThread = new Thread(server);
        serverThread.setName("nio-upnp-runner");
        serverThread.start();
        Log.i(TAG, TAG+" - NioUPnp Server running on " + bindAddress.getHostAddress() + ":" + getListenPort());
    }

    public void stopServer() {
        //System.out.println(TAG + ": Stopping UPnPServer");
        synchronized (serverLock) {
            if (server != null) {
                try {
                    server.stop();
                    Log.i(TAG, "NioUPnp Server stopped successfully.");
                } catch (Exception e) {
                    Log.e(TAG, "Error during server shutdown", e);
                } finally {
                    server = null;
                }

                if (serverThread != null) {
                    try {
                        serverThread.join(2000); // Wait for thread to die
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        Log.i(TAG, TAG + " - Interrupted while waiting for server thread to stop.");
                    }
                }
            }
        }
    }

    @Override
    public int getListenPort() {
        return UPNP_SERVER_PORT;
    }

}