package apincer.music.server.jupnp.transport;
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
    /**
     * The Handler that bridges our NIO server with the jUPnP protocol stack.
     */
    class UpnpHandler implements NioHttpServer.Handler {
        private final ProtocolFactory protocolFactory;

        public UpnpHandler(ProtocolFactory protocolFactory) {
            this.protocolFactory = protocolFactory;
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
            response.addHeader("Server", getServerSignature(getComponentName()));
            response.addHeader("Date", formatDate(System.currentTimeMillis()));

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
    private NioHttpServer server;
    private Router router;

    private Thread serverThread;

    // Simplified constructor for pure Java environment
    public NioUPnpServerImpl(Context context, FileRepository fileRepos, TagRepository tagRepos) {
        super(context, fileRepos, tagRepos);
       // this.configuration = configuration;
        addLibInfo("java.nio", "");
    }

    public void initServer(InetAddress bindAddress, Object router) throws Exception {
        this.router = (Router) router;
        server = new NioHttpServer(getListenPort());

        // Register the UpnpNioHandler as the fallback to handle all incoming requests
        NioHttpServer.Handler upnpHandler = new UpnpHandler(this.router.getProtocolFactory());
        server.registerFallbackHandler(upnpHandler); // accept any context path
        server.setMaxThread(2);
       // server.setClientReadBufferSize();
        server.setTcpNoDelay(true);

        serverThread = new Thread(server);
        serverThread.setName("nio-upnp-runner");
        serverThread.start();
        Log.i(TAG, TAG+" - UPnPServer (NIO) running on " + bindAddress.getHostAddress() + ":" + getListenPort());
    }

    public void stopServer() {
        //System.out.println(TAG + ": Stopping UPnPServer");
        if (server != null) {
            server.stop();
        }
        if (serverThread != null) {
            try {
                serverThread.join(2000); // Wait for thread to die
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                Log.i(TAG, TAG+" - Interrupted while waiting for server thread to stop.");
            }
        }
    }

    @Override
    public int getListenPort() {
        return UPNP_SERVER_PORT;
    }

    @Override
    public String getComponentName() {
        return "UPnPServer";
    }
}