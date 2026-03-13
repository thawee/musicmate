package apincer.android.jupnp.server.httpcore;

import static apincer.music.core.utils.StringUtils.isEmpty;

import android.content.Context;
import android.util.Log;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.EndpointDetails;
import org.apache.hc.core5.http.EntityDetails;
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.HttpException;
import org.apache.hc.core5.http.HttpHeaders;
import org.apache.hc.core5.http.HttpRequest;
import org.apache.hc.core5.http.HttpStatus;
import org.apache.hc.core5.http.Message;
import org.apache.hc.core5.http.MethodNotSupportedException;
import org.apache.hc.core5.http.URIScheme;
import org.apache.hc.core5.http.impl.bootstrap.HttpAsyncServer;
import org.apache.hc.core5.http.nio.AsyncEntityProducer;
import org.apache.hc.core5.http.nio.AsyncRequestConsumer;
import org.apache.hc.core5.http.nio.AsyncServerRequestHandler;
import org.apache.hc.core5.http.nio.entity.AsyncEntityProducers;
import org.apache.hc.core5.http.nio.entity.BasicAsyncEntityConsumer;
import org.apache.hc.core5.http.nio.support.AsyncResponseBuilder;
import org.apache.hc.core5.http.nio.support.BasicRequestConsumer;
import org.apache.hc.core5.http.protocol.HttpContext;
import org.apache.hc.core5.http.protocol.HttpCoreContext;
import org.apache.hc.core5.http2.impl.nio.bootstrap.H2ServerBootstrap;
import org.apache.hc.core5.reactor.IOReactorConfig;
import org.apache.hc.core5.reactor.IOSession;
import org.apache.hc.core5.util.TimeValue;
import org.apache.hc.core5.util.Timeout;
import org.jupnp.transport.spi.InitializationException;

import java.io.File;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArraySet;

import apincer.music.core.database.MusicTag;
import apincer.music.core.repository.FileRepository;
import apincer.music.core.repository.TagRepository;
import apincer.music.core.server.BaseServer;
import apincer.music.core.server.spi.WebServer;
import apincer.music.core.utils.MimeTypeUtils;
import apincer.music.server.jupnp.transport.DLNAHeaderHelper;

public class HttpCoreWebServerImpl extends BaseServer implements WebServer {
    private static final String TAG = "HttpCoreWebServerImpl";

    private HttpAsyncServer server;
    private final Object serverLock = new Object();

    public HttpCoreWebServerImpl(Context context, FileRepository fileRepos, TagRepository tagRepos) {
        super(context, fileRepos, tagRepos);
        addLibInfo("HttpCore5", "5.4.2");
    }

    @Override
    public void restartServer(InetAddress bindAddress) {
        synchronized (serverLock) {
            Log.d(TAG, "Restarting HttpCore5 Server...");

            // 1. Full Stop
            stopServer();

            // 2. Small grace period for OS to release the socket
            try {
                Thread.sleep(200);
            } catch (InterruptedException ignored) {
            }

            // 3. Start New Instance
            try {
                initServer(bindAddress);
            } catch (Exception e) {
                Log.e(TAG, "Failed to restart server: " + e.getMessage());
            }
        }
    }

    @Override
    public void initServer(InetAddress bindAddress) throws Exception {
                try {
                    Log.v(TAG, "Running HttpCore5 Content Server: " + bindAddress.getHostAddress() + ":" + WEB_SERVER_PORT);

                    IOReactorConfig config = IOReactorConfig.custom()
                            .setIoThreadCount(1) // for small memory and 10 tps
                            .setSoTimeout(Timeout.ofSeconds(30))
                            .setTcpNoDelay(true) //to reduce latency
                            .setSoKeepAlive(true)
                            //.setSelectInterval(TimeValue.ofSeconds(1))
                            .setSelectInterval(TimeValue.ofMicroseconds(50)) //1s is too slow; it can cause the "client stop" bug during handshakes.
                            .setSndBufSize(65536*2) //Smoother delivery of Hi-Res FLAC/DSD peaks.
                            .setRcvBufSize(65536)
                            .setSoReuseAddress(true)
                            .build();

                    server = H2ServerBootstrap.bootstrap()
                            .setCanonicalHostName(bindAddress.getHostAddress())
                            .setIOReactorConfig(config)
                            .register("/*", new ResourceHandler())
                            .create();
                    server.listen(new InetSocketAddress(WEB_SERVER_PORT), URIScheme.HTTP);
                    server.start();
                } catch (Exception ex) {
                    throw new InitializationException("Could not initialize " + getClass().getSimpleName() + ": " + ex, ex);
                }
    }

    @Override
    public void stopServer() {
        Log.v(TAG, "Shutting down HttpCore5 Web Server");

        synchronized (serverLock) {
            if (server != null) {
                try {
                    // Close WebSocket sessions first if you have the list
                    /*for (WebSocketChannel session : activeWebsocketSessions) {
                        IoUtils.safeClose(session);
                    }
                    activeWebsocketSessions.clear();
                    */

                    server.initiateShutdown();
                    try {
                        server.awaitShutdown(TimeValue.ofSeconds(3));
                    } catch (InterruptedException e) {
                        Log.d(TAG, "got exception on content server stop ", e);
                    }
                    Log.i(TAG, "HttpCore Server stopped successfully.");
                } catch (Exception e) {
                    Log.e(TAG, "Error during server shutdown", e);
                } finally {
                    server = null;
                }
            }
        }
    }

    @Override
    public int getListenPort() {
        return WEB_SERVER_PORT;
    }

    private class ResourceHandler extends WebSocketContent implements AsyncServerRequestHandler<Message<HttpRequest, byte[]>>  {
        private static final ObjectMapper MAPPER = new ObjectMapper()
                .setDefaultPropertyInclusion(JsonInclude.Include.ALWAYS);
        CopyOnWriteArraySet<IOSession> wsSessions = new CopyOnWriteArraySet<IOSession>();
        public ResourceHandler() {
        }

        @Override
        protected void broadcastMessage(String jsonResponse) {
            if (wsSessions != null) {
                for (IOSession session : wsSessions) {
                    if (session.isOpen()) {
                        sendText(session, jsonResponse);
                        // Duplicate buffer for each session to avoid position conflicts
                        // session.enqueue(frame.duplicate(), org.apache.hc.core5.reactor.Command.Priority.NORMAL);
                    }
                }
            }
        }

        @Override
        public AsyncRequestConsumer<Message<HttpRequest, byte[]>> prepare(HttpRequest request, EntityDetails entityDetails, HttpContext context) {
            return new BasicRequestConsumer<>(entityDetails != null ? new BasicAsyncEntityConsumer() : null);
        }


        private void onWSConnect(IOSession session) {
            Log.d(TAG, "WS Connected: " + session.getRemoteAddress());
            wsSessions.add(session);

            // Send welcome messages (reusing your getWelcomeMessages logic)
            for (Map<String, Object> msg : getWelcomeMessages()) {
                try {
                    String jsonResponse = MAPPER.writeValueAsString(msg);
                    sendText(session, jsonResponse);
                } catch (JsonProcessingException e) {
                    Log.e(TAG, "Error serializing welcome message", e);
                }
            }
        }

        /**
         * Helper to send encoded frames back to the client
         */
        private void sendText(IOSession session, String text) {
            if (session.isOpen()) {
                try {
                    ByteBuffer frame = WebSocketFrameEncoder.encodeTextFrame(text);
                    // We write directly to the session.
                    // Note: In NIO, this might not write everything if the buffer is full.
                    int written = session.write(frame);

                    // If not all data was written, we must register for OP_WRITE
                    if (frame.hasRemaining()) {
                        session.setEvent(java.nio.channels.SelectionKey.OP_WRITE);
                        // You would then need to handle the remaining bytes in your EventListener
                    }
                } catch (IOException e) {
                   // onClose(session);
                }
            }
        }

        @Override
        public void handle(Message<HttpRequest, byte[]> request, ResponseTrigger responseTrigger, HttpContext context) throws IOException, HttpException {
            final HttpRequest head = request.getHead();
            final String uri = head.getRequestUri();
            final String method = head.getMethod();

            // 1. CHECK FOR WEBSOCKET UPGRADE
            Header upgradeHeader = head.getFirstHeader("Upgrade");
            if (upgradeHeader != null && "websocket".equalsIgnoreCase(upgradeHeader.getValue())) {
                Header keyHeader = head.getFirstHeader("Sec-WebSocket-Key");
                if (keyHeader != null) {
                    String acceptKey = generateWebSocketAccept(keyHeader.getValue());

                    final AsyncResponseBuilder responseBuilder = AsyncResponseBuilder.create(HttpStatus.SC_SWITCHING_PROTOCOLS);
                    responseBuilder.addHeader("Upgrade", "websocket");
                    responseBuilder.addHeader("Connection", "Upgrade");
                    responseBuilder.addHeader("Sec-WebSocket-Accept", acceptKey);

                    // Submit handshake response
                   // responseTrigger.submitResponse(responseBuilder.build(), context);

                    // Hijack the session for the WebSocketHandler
                    HttpCoreContext coreContext = HttpCoreContext.cast(context);
                    IOSession session = (IOSession) coreContext.getAttribute("http.io-session");
                    if (session != null) {
                        onWSConnect(session);
                    }
                    return;
                }
            }

            // 2. HANDLE JSON COMMANDS (POST/PUT)
            if (method.equalsIgnoreCase("POST") || method.equalsIgnoreCase("PUT")) {
                byte[] body = request.getBody();
                if (body != null && body.length > 0) {
                    String jsonString = new String(body, StandardCharsets.UTF_8);
                    // Process the JSON command (e.g., volume, play, pause)
                    Map<String, Object> map = MAPPER.readValue(jsonString, Map.class);
                    String command = String.valueOf(map.get("command"));
                    if (!command.isEmpty()) {
                        // 3. Handle command (reusing your handleCommand logic)
                        Map<String, Object> response = handleCommand(command, map);
                        if (response != null) {
                            String jsonResponse = MAPPER.writeValueAsString(response);
                            final AsyncResponseBuilder rb = AsyncResponseBuilder.create(HttpStatus.SC_OK);
                            rb.setEntity(jsonResponse, ContentType.APPLICATION_JSON);
                            responseTrigger.submitResponse(rb.build(), context);
                        }
                    }

                    return;
                }
            }

            // 3. HANDLE MUSIC STREAMING/Web Contents
            String requestMethod = request.getHead().getMethod()
                    .toUpperCase(Locale.ENGLISH);

            Header uaHeader = request.getHead().getFirstHeader("User-Agent");
            String userAgent = (uaHeader != null) ? uaHeader.getValue() : "Unknown";

            HttpCoreContext coreContext = HttpCoreContext.cast(context);
            EndpointDetails endpoint = coreContext.getEndpointDetails();
            String remoteAddr = endpoint.getRemoteAddress().toString();

            if (!requestMethod.equals("GET") && !requestMethod.equals("HEAD")) {
                Log.d(TAG,
                        "HTTP request isn't GET or HEAD stop! Method was: "
                                + requestMethod);
                throw new MethodNotSupportedException(requestMethod
                        + " method not supported");
            }

           // String requestUri = Uri.parse(request.getHead().getRequestUri()).getPath();
            String requestUri = uri;
            if (isEmpty(requestUri) || requestUri.equals("/")) {
                requestUri = "/index.html";
            }

            ContentHolder contentHolder;
            if (requestUri.startsWith(CONTEXT_PATH_COVERART)) {
                contentHolder = lookupAlbumArt(requestUri);
            } else if (requestUri.startsWith(CONTEXT_PATH_MUSIC)) {
                contentHolder = lookupContent(requestUri, userAgent, remoteAddr);
            } else {
                contentHolder = lookupWebResource(requestUri);
            }

            if (contentHolder == null) {
                submitError(responseTrigger, context, HttpStatus.SC_FORBIDDEN, "Access denied");
                return;
            }

            File file = new File(contentHolder.getFilePath());
            if (!file.exists()) {
                submitError(responseTrigger, context, HttpStatus.SC_NOT_FOUND, "File not found");
                return;
            }

            // 3. Handle Range Header (Seeking)
            Header rangeHeader = request.getHead().getFirstHeader("Range");
            long fileLength = file.length();
            long start = 0;
            long end = fileLength - 1;

            boolean isPartial = false;
            if (rangeHeader != null && rangeHeader.getValue().startsWith("bytes=")) {
                try {
                    String rangeValue = rangeHeader.getValue().substring(6);
                    String[] parts = rangeValue.split("-");
                    start = Long.parseLong(parts[0]);
                    if (parts.length > 1 && !parts[1].isEmpty()) {
                        end = Long.parseLong(parts[1]);
                    }
                    isPartial = true;
                } catch (Exception e) {
                    Log.e(TAG, "Failed to parse range header: " + rangeHeader.getValue());
                }
            }

            // 4. Build Response
            final AsyncResponseBuilder responseBuilder = AsyncResponseBuilder.create(isPartial ? HttpStatus.SC_PARTIAL_CONTENT : HttpStatus.SC_OK);
            responseBuilder.addHeader(HttpHeaders.ACCEPT_RANGES, "bytes");
            //responseBuilder.addHeader(HttpHeaders.CACHE_CONTROL, "public, max-age=3600");
            responseBuilder.addHeader(HttpHeaders.CONNECTION, "keep-alive");
            responseBuilder.addHeader("X-Content-Type-Options", "nosniff");
            responseBuilder.addHeader(HttpHeaders.CACHE_CONTROL, "no-store, no-cache, must-revalidate");

            // Updated Partial Content logic for 5.3.6
            if (isPartial) {
              //  responseBuilder.addHeader("Cache-Control", "no-store, no-cache, must-revalidate");

                long contentLength = end - start + 1;
                responseBuilder.addHeader(HttpHeaders.CONTENT_RANGE, "bytes " + start + "-" + end + "/" + fileLength);

                // Use the custom 5.3.6 compatible producer
                responseBuilder.setEntity(new PartialFileProducer(
                        file, start, contentLength, ContentType.parse(contentHolder.contentType)
                ));
            } else {
                // 604800 seconds = 7 days
                //responseBuilder.addHeader("Cache-Control", "public, max-age=604800, immutable");
                responseBuilder.setEntity(contentHolder.getEntityProducer(getContext()));
            }

            // 5. Add DLNA Headers
            responseBuilder.addHeader("transferMode.dlna.org", "Streaming");
            if (contentHolder.dlnaContentFeatures != null) {
                responseBuilder.addHeader("contentFeatures.dlna.org", contentHolder.dlnaContentFeatures);
            }

            responseTrigger.submitResponse(responseBuilder.build(), context);
        }

        private void submitError(ResponseTrigger responseTrigger, HttpContext context, int status, String message) {
            try {
                final AsyncResponseBuilder responseBuilder = AsyncResponseBuilder.create(status);
                // Create a simple HTML error body
                String entity = "<html><body><h1>" + status + "</h1><p>" + message + "</p></body></html>";

                responseBuilder.setEntity(
                        AsyncEntityProducers.create(entity, ContentType.TEXT_HTML)
                );

                responseTrigger.submitResponse(responseBuilder.build(), context);
            } catch (Exception e) {
                Log.e(TAG, "Failed to submit error response", e);
            }
        }

        private String generateWebSocketAccept(String key) {
            String GUID = "258EAFA5-E914-47DA-95CA-C5AB0DC85B11";
            try {
                java.security.MessageDigest md = java.security.MessageDigest.getInstance("SHA-1");
                byte[] hash = md.digest((key + GUID).getBytes(java.nio.charset.StandardCharsets.UTF_8));
                return android.util.Base64.encodeToString(hash, android.util.Base64.NO_WRAP);
            } catch (Exception e) {
                return "";
            }
        }

        /**
         * Lookup content in the media library
         *
         * @param contentId the id of the content
         * @return the content description
         */
        private ContentHolder lookupContent(String contentId, String userAgent, String remoteAddr) {
            ContentHolder result = null;

            if (contentId == null) {
                return null;
            }

            try {
                MusicTag song = getSong(contentId);
                notifyPlayback(remoteAddr, userAgent, song);
                if (song != null) {
                    String contentType = MimeTypeUtils.getMimeTypeFromPath(song.getPath());
                    if (contentType == null) contentType = "audio/*";
                    String dlnaContentFeatures = DLNAHeaderHelper.getDLNAContentFeatures(song);
                    result = new ContentHolder(contentType, String.valueOf(song.getId()), song.getPath(), dlnaContentFeatures);
                }
            } catch (Exception ex) {
                Log.e(TAG, "lookupContent: - " + contentId, ex);
            }

            return result;
        }

        /**
         * Lookup content in the media library
         *
         * @param requestUri the id of the album
         * @return the content description
         */
        private ContentHolder lookupAlbumArt(String requestUri) {
            File filePath = getAlbumArt(requestUri);
            String contentType = MimeTypeUtils.getMimeTypeFromPath(filePath.getPath());
            if (contentType == null) contentType = "image/*";
            return new ContentHolder(contentType, requestUri, filePath.getAbsolutePath(), null);
        }

        private ContentHolder lookupWebResource(String requestUri) {
            File filePath = getWebResource(requestUri);
            String contentType = MimeTypeUtils.getMimeTypeFromPath(filePath.getPath());
            if (contentType == null) contentType = "text/*";
            return new ContentHolder(contentType, filePath.getName(), filePath.getPath());
        }

        /**
         * ValueHolder for media content.
         */
        static class ContentHolder {
            private final String contentType;
            private final String resId;
            private final String filePath;
            // private byte[] content;
            private String dlnaContentFeatures;

            public ContentHolder(String contentType, String resId, String filePath) {
                this.resId = resId;
                this.filePath = filePath;
                this.contentType = contentType;
            }

            public ContentHolder(String contentType, String resId, String filePath, String dlnaContentFeatures) {
                this.resId = resId;
                this.filePath = filePath;
                this.contentType = contentType;
                this.dlnaContentFeatures = dlnaContentFeatures;
            }

            /**
             * @return the uri
             */
            public String getFilePath() {
                return filePath;
            }

            public AsyncEntityProducer getEntityProducer(Context context) {
                File file = new File(getFilePath());
                return AsyncEntityProducers.create(file, ContentType.parse(contentType));
            }
        }
    }
}