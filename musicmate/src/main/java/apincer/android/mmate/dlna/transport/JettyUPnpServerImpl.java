package apincer.android.mmate.dlna.transport;

import static java.nio.charset.StandardCharsets.UTF_8;
import static java.nio.file.StandardCopyOption.REPLACE_EXISTING;
import static apincer.android.mmate.Constants.COVER_ARTS;
import static apincer.android.mmate.Constants.DEFAULT_COVERART_DLNA_RES;
import static apincer.android.mmate.Constants.DEFAULT_COVERART_FILE;
import static apincer.android.mmate.utils.StringUtils.isEmpty;

import android.content.Context;
import android.util.Log;

import org.eclipse.jetty.http.HttpHeader;
import org.eclipse.jetty.http.HttpMethod;
import org.eclipse.jetty.http.HttpStatus;
import org.eclipse.jetty.http.content.HttpContent;
import org.eclipse.jetty.io.Content;
import org.eclipse.jetty.server.Connector;
import org.eclipse.jetty.server.Handler;
import org.eclipse.jetty.server.HttpConfiguration;
import org.eclipse.jetty.server.HttpConnectionFactory;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.Response;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.server.handler.ContextHandler;
import org.eclipse.jetty.server.handler.ContextHandlerCollection;
import org.eclipse.jetty.server.handler.ResourceHandler;
import org.eclipse.jetty.util.Callback;
import org.eclipse.jetty.util.resource.ResourceFactory;
import org.eclipse.jetty.util.thread.QueuedThreadPool;
import org.jupnp.model.message.StreamRequestMessage;
import org.jupnp.model.message.StreamResponseMessage;
import org.jupnp.model.message.UpnpHeaders;
import org.jupnp.model.message.UpnpMessage;
import org.jupnp.model.message.UpnpRequest;
import org.jupnp.transport.Router;
import org.jupnp.transport.spi.InitializationException;
import org.jupnp.transport.spi.UpnpStream;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.util.List;
import java.util.Map;

import apincer.android.mmate.MusixMateApp;
import apincer.android.mmate.repository.FileRepository;
import apincer.android.mmate.repository.MusicTag;
import apincer.android.mmate.utils.ApplicationUtils;
import apincer.android.utils.FileUtils;

public class JettyUPnpServerImpl extends StreamServerImpl.StreamServer {
    private static final String TAG = "JettyUPnpServer";
    // Jetty tuning parameters for better streaming
    private static final int OUTPUT_BUFFER_SIZE = 65536; // 64KB buffer for better streaming
    private static final int MAX_THREADS = 50;           // Enough threads for multiple clients
    private static final int MIN_THREADS = 8;            // Keep some threads alive for responsiveness
    private static final int IDLE_TIMEOUT = 30000;       // 30 seconds idle timeout

    private Server server;

    public JettyUPnpServerImpl(Context context, Router router, StreamServerConfigurationImpl configuration) {
        super(context, router, configuration);
    }

    @Override
    public void initServer(InetAddress bindAddress) throws InitializationException {
        // Initialize the server with the specified port.
        Thread thread = new Thread(() -> {
            try {
                Log.i(TAG, "  Start UPNP Server (Jetty): " + bindAddress.getHostAddress() + ":" + getListenPort());

                // Configure thread pool
                QueuedThreadPool threadPool = new QueuedThreadPool();
                threadPool.setMaxThreads(120); // Adjust based on your server's capacity
                threadPool.setMaxThreads(MAX_THREADS);
                threadPool.setMinThreads(MIN_THREADS);
                threadPool.setIdleTimeout(IDLE_TIMEOUT);
                threadPool.setName("dlna-server");

                server = new Server(threadPool);

                // HTTP Configuration - optimized for streaming
                HttpConfiguration httpConfig = new HttpConfiguration();
                httpConfig.setOutputBufferSize(OUTPUT_BUFFER_SIZE);
                httpConfig.setSendDateHeader(true);
                httpConfig.setSendServerVersion(false); // will send custom header
                httpConfig.setSendXPoweredBy(true); // required by custom header
                httpConfig.setRequestHeaderSize(8192);

                // Create connector with tuned parameters
                try (ServerConnector connector = new ServerConnector(server,
                        new HttpConnectionFactory(httpConfig))) {
                    connector.setHost("0.0.0.0"); // Bind only to IPv4
                    connector.setPort(getListenPort());
                    connector.setIdleTimeout(60000); // 60 second timeout
                    connector.setAcceptQueueSize(128); // More reasonable queue size

                    server.setConnectors(new Connector[]{connector});
                }

                // UPnP/DLNA context handler
                ContextHandler upnpContext = new ContextHandler("/dms");
                upnpContext.setHandler(new UPnpHandler());

                // Optimized resource handler for cover art
                ResourceHandler coverartHandler = new CoverartHandler();
                coverartHandler.setBaseResource(ResourceFactory.of(coverartHandler).newResource("/"));
                coverartHandler.setDirAllowed(false);
                coverartHandler.setAcceptRanges(true); // Enable range requests
                coverartHandler.setEtags(true);        // Enable ETag support
                coverartHandler.setCacheControl("max-age=3600,public"); // Cache cover art
                coverartHandler.setServer(server);

                ContextHandler coverartContext = new ContextHandler("/coverart");
                coverartContext.setHandler(coverartHandler);

                // Collect all context handlers
                ContextHandlerCollection contexts = new ContextHandlerCollection(
                        upnpContext, coverartContext
                );

                server.setHandler(contexts);
                server.start();
            } catch (Exception e) {
                Log.e(TAG, "Failed to start DLNA server", e);
                throw new RuntimeException(e);
            }
        });

        thread.start();
    }

    @Override
    public void stopServer() {
        // Stop the server.
        try {
            Log.i(TAG, "  Stop UPNP Server (Jetty)");
            if (server != null && server.isRunning()) {
                server.stop();
            }
        } catch (Exception e) {
            Log.e(TAG, "Error stopping DLNA server", e);
            throw new RuntimeException(e);
        }
    }

    @Override
    protected String getServerVersion() {
        return "Jetty/12.0.1";
    }

    private class UPnpHandler extends Handler.Abstract {
        private final UpnpStream upnpStream;

        private UPnpHandler( ) {
            this.upnpStream = new UpnpStream(getProtocolFactory()) {
                @Override
                public void run() {
                    // Empty implementation as we handle the stream processing manually
                }
            };
        }

        @Override
        public boolean handle(Request request, Response response, Callback callback) {
            try {

                StreamRequestMessage requestMessage = readRequestMessage(request);
                StreamResponseMessage responseMessage = upnpStream.process(requestMessage);

                if (responseMessage != null) {
                    writeResponseMessage(responseMessage, response, callback);
                    return true;
                } else {
                    // No response message, return 404
                    response.getHeaders().put(HttpHeader.CONTENT_TYPE, "text/html; charset=utf-8");
                    response.setStatus(HttpStatus.NOT_FOUND_404);
                    Content.Sink.write(response, true, "<h1>Resource Not Found</h1>\n", callback);
                    return true;
                }
            } catch (Throwable t) {
                Log.e(TAG, "Exception occurred during UPnP stream processing: "+ t.getMessage(), t);
                response.getHeaders().put(HttpHeader.CONTENT_TYPE, "text/html; charset=utf-8");
                response.setStatus(HttpStatus.INTERNAL_SERVER_ERROR_500);
                Content.Sink.write(response, true, "<h1>Internal Server Error</h1><p>" + t.getMessage() + "</p>\n", callback);
                return true;
            }
        }

        private void writeResponseMessage(StreamResponseMessage responseMessage, Response resp, Callback callback) {
            int statusCode = responseMessage.getOperation().getStatusCode();

            // Set status first
            resp.setStatus(statusCode);

            // Headers from response message
            for (Map.Entry<String, List<String>> entry : responseMessage.getHeaders().entrySet()) {
                for (String value : entry.getValue()) {
                    String headerName = entry.getKey();

                    // Special handling for Server header
                    if (HttpHeader.SERVER.name().equalsIgnoreCase(headerName)) {
                        resp.getHeaders().put(HttpHeader.SERVER, getFullServerName());
                    } else {
                        resp.getHeaders().add(headerName, value);
                    }
                }
            }

            // Add DLNA-specific headers for better streaming support
            if (!resp.getHeaders().contains(HttpHeader.CONTENT_TYPE) &&
                    resp.getHeaders().get(HttpHeader.CONTENT_TYPE) != null &&
                    resp.getHeaders().get(HttpHeader.CONTENT_TYPE).contains("audio/")) {

                // Add DLNA headers for audio streaming
                resp.getHeaders().add("contentFeatures.dlna.org", "DLNA.ORG_OP=01;DLNA.ORG_CI=0;DLNA.ORG_FLAGS=01700000000000000000000000000000");
                resp.getHeaders().add("transferMode.dlna.org", "Streaming");

                // Support byte range requests for seeking
                resp.getHeaders().add(HttpHeader.ACCEPT_RANGES, "bytes");

                // Less aggressive caching for audio
                resp.getHeaders().add(HttpHeader.CACHE_CONTROL, "no-cache");
            } else {
                // Standard cache control for other resources
                resp.getHeaders().add(HttpHeader.PRAGMA, "no-cache");
                resp.getHeaders().add(HttpHeader.CACHE_CONTROL, "must-revalidate,no-cache,no-store");
            }

            // Body handling
            byte[] responseBody = responseMessage.hasBody() ? responseMessage.getBodyBytes() : null;
            if (responseBody != null && responseBody.length > 0) {
                resp.write(true, ByteBuffer.wrap(responseBody), callback);
            } else {
                // Empty response
                resp.write(true, ByteBuffer.allocate(0), callback);
            }
        }

        private StreamRequestMessage readRequestMessage(Request req) throws IOException {
            String requestMethod = req.getMethod();
            String requestURI = req.getHttpURI().getPath();
            StreamRequestMessage requestMessage;
            try {
                requestMessage =
                        new StreamRequestMessage(
                                UpnpRequest.Method.getByHttpName(requestMethod),
                                URI.create(requestURI)
                        );

            } catch (IllegalArgumentException ex) {
                throw new RuntimeException("Invalid request URI: " + requestURI, ex);
            }

            if (requestMessage.getOperation().getMethod().equals(UpnpRequest.Method.UNKNOWN)) {
                throw new RuntimeException("Method not supported: " + requestMethod);
            }

            UpnpHeaders headers = new UpnpHeaders();
            req.getHeaders().stream().iterator().forEachRemaining(httpField -> headers.add(httpField.getName(), httpField.getValue()));
            requestMessage.setHeaders(headers);

            // Body
            String body = Content.Source.asString(req, UTF_8);

            byte[] bodyBytes = new byte[]{};
            if (body!=null && !isEmpty(body)) {
                bodyBytes = body.getBytes(UTF_8);
            }

            if (bodyBytes.length > 0 && requestMessage.isContentTypeMissingOrText()) {

                // Log.v(TAG, "Request contains textual entity body, converting then setting string on message");
                requestMessage.setBodyCharacters(bodyBytes);

            } else if (bodyBytes.length > 0) {
                requestMessage.setBody(UpnpMessage.BodyType.BYTES, bodyBytes);
            }
            return requestMessage;
        }
    }


    private class CoverartHandler extends ResourceHandler {
        File defaultCoverartDir;
        // Cache control for cover art
        private static final String COVER_ART_CACHE_CONTROL = "max-age=3600,public";
        private static final long COVER_ART_MAX_AGE = 3600 * 1000; // 1 hour in milliseconds

        private CoverartHandler( ) {
            defaultCoverartDir = new File(getCoverartDir(COVER_ARTS),DEFAULT_COVERART_FILE);
        }

        @Override
        public boolean handle(Request request, Response response, Callback callback) throws Exception {
            if (!HttpMethod.GET.is(request.getMethod()) && !HttpMethod.HEAD.is(request.getMethod())) {
                return super.handle(request, response, callback);
            }

            boolean controlCache = true;

            // Try to find the cover art
            HttpContent content = getResourceService().getContent(Request.getPathInContext(request), request);
            if (content == null) {
                // Try to find folder cover art
                String uri = request.getHttpURI().getPath();
                try {
                    String albumUniqueKey = uri.substring("/coverart/".length(), uri.indexOf(".png"));
                    MusicTag tag = MusixMateApp.getInstance().getOrmLite().findByAlbumUniqueKey(albumUniqueKey);
                    if (tag != null) {
                        File covertFile = FileRepository.getCoverArt(tag);
                        if(covertFile !=null) {
                            content = getResourceService().getContent(covertFile.getAbsolutePath(), request);
                        }
                    }
                } catch (Exception ex) {
                    Log.e(TAG, "lookupContent: - " + uri, ex);
                }
            }

            // Fallback to default cover art if necessary
            if (content == null) {
                try {
                    if(!defaultCoverartDir.exists()) {
                        FileUtils.createParentDirs(defaultCoverartDir);
                        InputStream in = ApplicationUtils.getAssetsAsStream(getContext(), DEFAULT_COVERART_DLNA_RES);
                        Files.copy(in, defaultCoverartDir.toPath(), REPLACE_EXISTING);
                    }
                    content = getResourceService().getContent(defaultCoverartDir.getAbsolutePath(), request);
                    controlCache = false;
                } catch (IOException e) {
                    Log.e(TAG, "Init default missing cover art", e);
                }
            }

            if (content == null) {
                return super.handle(request, response, callback); // no content - try other handlers
            }

            // Enable ETags and Accept-Ranges for more efficient transfers
            response.getHeaders().add(HttpHeader.ACCEPT_RANGES, "bytes");
            if(controlCache) {
                // Enable efficient caching of cover art
                response.getHeaders().add(HttpHeader.CACHE_CONTROL, COVER_ART_CACHE_CONTROL);

                // Set Last-Modified and Expires headers for better caching
                long now = System.currentTimeMillis();
                response.getHeaders().put(HttpHeader.LAST_MODIFIED, now - 86400000); // Yesterday
                response.getHeaders().put(HttpHeader.EXPIRES, now + COVER_ART_MAX_AGE); // 1 hour in future
            }

            // Add MIME type explicitly for improved compatibility
            if (request.getHttpURI().getPath().endsWith(".png")) {
                response.getHeaders().put(HttpHeader.CONTENT_TYPE, "image/png");
            } else if (request.getHttpURI().getPath().endsWith(".jpg") ||
                    request.getHttpURI().getPath().endsWith(".jpeg")) {
                response.getHeaders().put(HttpHeader.CONTENT_TYPE, "image/jpeg");
            }

            getResourceService().doGet(request, response, callback, content);
            return true;
        }
    }

}
