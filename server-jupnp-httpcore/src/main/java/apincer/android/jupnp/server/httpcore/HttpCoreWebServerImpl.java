package apincer.android.jupnp.server.httpcore;

import android.content.Context;
import android.util.Log;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.EndpointDetails;
import org.apache.hc.core5.http.EntityDetails;
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.HttpException;
import org.apache.hc.core5.http.HttpHeaders;
import org.apache.hc.core5.http.HttpRequest;
import org.apache.hc.core5.http.HttpResponse;
import org.apache.hc.core5.http.HttpStatus;
import org.apache.hc.core5.http.Message;
import org.apache.hc.core5.http.MethodNotSupportedException;
import org.apache.hc.core5.http.URIScheme;
import org.apache.hc.core5.http.impl.bootstrap.HttpAsyncServer;
import org.apache.hc.core5.http.message.BasicHttpResponse;
import org.apache.hc.core5.http.nio.AsyncEntityProducer;
import org.apache.hc.core5.http.nio.AsyncRequestConsumer;
import org.apache.hc.core5.http.nio.AsyncServerExchangeHandler;
import org.apache.hc.core5.http.nio.AsyncServerRequestHandler;
import org.apache.hc.core5.http.nio.CapacityChannel;
import org.apache.hc.core5.http.nio.DataStreamChannel;
import org.apache.hc.core5.http.nio.ResponseChannel;
import org.apache.hc.core5.http.nio.entity.AsyncEntityProducers;
import org.apache.hc.core5.http.nio.entity.BasicAsyncEntityConsumer;
import org.apache.hc.core5.http.nio.support.AsyncResponseBuilder;
import org.apache.hc.core5.http.nio.support.BasicRequestConsumer;
import org.apache.hc.core5.http.protocol.HttpContext;
import org.apache.hc.core5.http.protocol.HttpCoreContext;
import org.apache.hc.core5.http2.impl.nio.bootstrap.H2ServerBootstrap;
import org.apache.hc.core5.reactor.IOEventHandler;
import org.apache.hc.core5.reactor.IOReactorConfig;
import org.apache.hc.core5.reactor.IOSession;
import org.apache.hc.core5.reactor.IOSessionListener;
import org.apache.hc.core5.util.TimeValue;
import org.apache.hc.core5.util.Timeout;
import org.apache.hc.core5.util.VersionInfo;
import org.jupnp.transport.spi.InitializationException;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;

import apincer.music.core.http.WebSocket;
import apincer.music.core.model.Track;
import apincer.music.core.repository.FileRepository;
import apincer.music.core.repository.TagRepository;
import apincer.music.core.server.BaseServer;
import apincer.music.core.server.ContentHolder;
import apincer.music.core.server.spi.WebServer;
import apincer.music.server.jupnp.transport.DLNAHeaderHelper;

public class HttpCoreWebServerImpl extends BaseServer implements WebServer {
    private static final String TAG = "HttpCoreWebServerImpl";

    private HttpAsyncServer server;
    private final Object serverLock = new Object();
    private static final Map<SocketAddress, IOSession> sessionMap = new ConcurrentHashMap<>();

    public HttpCoreWebServerImpl(Context context, FileRepository fileRepos, TagRepository tagRepos) {
        super(context, fileRepos, tagRepos);
        addLibInfo("HttpCore5", getVersion());
    }

    private String getVersion() {
        VersionInfo versionInfo = VersionInfo.loadVersionInfo("org.apache.hc.core5", null);
        if(versionInfo!= null) {
            return versionInfo.getRelease();
        }
        return "5.4.2";
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
                            //.setSndBufSize(65536*2) //Smoother delivery of Hi-Res FLAC/DSD peaks.
                            .setSndBufSize(1024 * 1024)
                            .setRcvBufSize(65536)
                            .setSoReuseAddress(true)
                            .build();

                    final ResourceHandler resourceHandler = new ResourceHandler();
                    server = H2ServerBootstrap.bootstrap()
                            .setCanonicalHostName(bindAddress.getHostAddress())
                            .setIOReactorConfig(config)
                            .setIOSessionListener(new IOSessionListener() {
                                @Override
                                public void connected(IOSession session) {
                                    SocketAddress address = session.getRemoteAddress();
                                    if (address != null) {
                                        Log.d(TAG, "Session connected: " + address);
                                        sessionMap.put(address, session);
                                    }
                                }

                                @Override
                                public void disconnected(IOSession session) {
                                    SocketAddress address = session.getRemoteAddress();
                                    Log.d(TAG, "Session disconnected: " + (address != null ? address : "unknown"));
                                    if (address != null) {
                                        sessionMap.remove(address);
                                    }
                                }

                                @Override
                                public void exception(IOSession session, Exception ex) {
                                    Log.e(TAG, "Session exception: " + session.getRemoteAddress(), ex);
                                }

                                @Override
                                public void timeout(IOSession session) {
                                    Log.d(TAG, "Session timeout: " + session.getRemoteAddress());
                                }

                                @Override
                                public void inputReady(IOSession session) {}

                                @Override
                                public void outputReady(IOSession session) {}

                                @Override
                                public void startTls(IOSession session) {}
                            })
                            .register("/ws", () -> new WebSocketExchangeHandler(resourceHandler))
                            .register("/*", resourceHandler)
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
                    sessionMap.clear();
                }
            }
        }
    }

    @Override
    public int getListenPort() {
        return WEB_SERVER_PORT;
    }

    private class WebSocketExchangeHandler implements AsyncServerExchangeHandler {
        private final ResourceHandler resourceHandler;

        public WebSocketExchangeHandler(ResourceHandler resourceHandler) {
            this.resourceHandler = resourceHandler;
        }

        @Override
        public void handleRequest(HttpRequest request, EntityDetails entityDetails, ResponseChannel responseChannel, HttpContext context) throws HttpException, IOException {
            Header keyHeader = request.getFirstHeader("Sec-WebSocket-Key");
            if (keyHeader != null) {
                String acceptKey = resourceHandler.generateWebSocketAccept(keyHeader.getValue());
                Log.d(TAG, "WebSocket Handshake: Sending 101 Switching Protocols");

                HttpResponse response = new BasicHttpResponse(HttpStatus.SC_SWITCHING_PROTOCOLS, "Switching Protocols");
                response.addHeader(HttpHeaders.UPGRADE, "websocket");
                response.addHeader(HttpHeaders.CONNECTION, "Upgrade");
                response.addHeader("Sec-WebSocket-Accept", acceptKey);
                response.addHeader(HttpHeaders.SERVER, getServerSignature());

                // Try to find the session in our map using remote address from EndpointDetails
                HttpCoreContext coreContext = HttpCoreContext.cast(context);
                EndpointDetails endpoint = coreContext.getEndpointDetails();
                IOSession session = null;
                if (endpoint != null) {
                    session = sessionMap.get(endpoint.getRemoteAddress());
                }

                // Fallback to standard context keys if map fails
                if (session == null) {
                    session = (IOSession) coreContext.getAttribute("http.iosession");
                }
                if (session == null) {
                    session = (IOSession) coreContext.getAttribute("http.io-session");
                }

                if (session != null) {
                    responseChannel.sendInformation(response, context);
                    resourceHandler.onWSConnect(session);
                } else {
                    Log.w(TAG, "Could not find IOSession for upgrade (RemoteAddr: " + (endpoint != null ? endpoint.getRemoteAddress() : "unknown") + ")");
                    responseChannel.sendResponse(new BasicHttpResponse(HttpStatus.SC_INTERNAL_SERVER_ERROR, "Internal Server Error"), null, context);
                }
            } else {
                responseChannel.sendResponse(new BasicHttpResponse(HttpStatus.SC_BAD_REQUEST, "Missing Sec-WebSocket-Key"), null, context);
            }
        }

        @Override
        public void consume(ByteBuffer src) throws IOException { }

        @Override
        public void streamEnd(List<? extends Header> trailers) throws HttpException, IOException { }

        @Override
        public void updateCapacity(CapacityChannel capacityChannel) throws IOException {
            capacityChannel.update(Integer.MAX_VALUE);
        }

        @Override
        public int available() {
            return 0;
        }

        @Override
        public void produce(DataStreamChannel channel) throws IOException { }

        @Override
        public void failed(Exception cause) {
            Log.e(TAG, "WebSocket exchange failed", cause);
        }

        @Override
        public void releaseResources() { }
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

            // Set up the WebSocket IO Handler to handle incoming frames
            session.upgrade(new WebSocketIOHandler(session));

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
                    WebSocket.Frame frame = new WebSocket.Frame(true, WebSocket.OPCODE_TEXT, text.getBytes(StandardCharsets.UTF_8));
                    ByteBuffer buffer = frame.toByteBuffer();
                    session.write(buffer);
                    if (buffer.hasRemaining()) {
                        session.setEvent(java.nio.channels.SelectionKey.OP_WRITE);
                    }
                } catch (IOException e) {
                   // onClose(session);
                }
            }
        }

        private void sendFrame(IOSession session, WebSocket.Frame frame) {
            if (session.isOpen()) {
                try {
                    ByteBuffer buffer = frame.toByteBuffer();
                    session.write(buffer);
                    if (buffer.hasRemaining()) {
                        session.setEvent(java.nio.channels.SelectionKey.OP_WRITE);
                    }
                } catch (IOException e) {
                    Log.e(TAG, "Error sending frame", e);
                }
            }
        }

        @Override
        public void handle(Message<HttpRequest, byte[]> request, ResponseTrigger responseTrigger, HttpContext context) throws IOException, HttpException {
            final HttpRequest head = request.getHead();
            final String uri = head.getRequestUri();
            final String method = head.getMethod();

            // 1. HANDLE JSON COMMANDS (POST/PUT)
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
                            rb.addHeader(HttpHeaders.SERVER, getServerSignature());
                            rb.setEntity(jsonResponse, ContentType.APPLICATION_JSON);
                            responseTrigger.submitResponse(rb.build(), context);
                        }
                    }

                    return;
                }
            }

            // 2. HANDLE MUSIC STREAMING/Web Contents
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

            ContentHolder contentHolder = resolveRequest(uri, remoteAddr, userAgent);

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
            responseBuilder.addHeader(HttpHeaders.SERVER, getServerSignature());
            responseBuilder.addHeader(HttpHeaders.ACCEPT_RANGES, "bytes");
            //responseBuilder.addHeader(HttpHeaders.CACHE_CONTROL, "public, max-age=3600");
            responseBuilder.addHeader(HttpHeaders.CONNECTION, "keep-alive");
            responseBuilder.addHeader("X-Content-Type-Options", "nosniff");
            responseBuilder.addHeader(HttpHeaders.CACHE_CONTROL, "no-store, no-cache, must-revalidate");

            // Dynamic ETag support
            String etag = generateETag(file);
            responseBuilder.addHeader(HttpHeaders.ETAG, etag);

            // Updated Partial Content logic for 5.3.6
            if (isPartial) {
              //  responseBuilder.addHeader("Cache-Control", "no-store, no-cache, must-revalidate");

                long contentLength = end - start + 1;
                responseBuilder.addHeader(HttpHeaders.CONTENT_RANGE, "bytes " + start + "-" + end + "/" + fileLength);

                // Use the custom 5.3.6 compatible producer
                responseBuilder.setEntity(new PartialFileProducer(
                        file, start, contentLength, ContentType.parse(contentHolder.getContentType())
                ));
            } else {
                // 604800 seconds = 7 days
                //responseBuilder.addHeader("Cache-Control", "public, max-age=604800, immutable");
                responseBuilder.setEntity(getEntityProducer(getContext(), contentHolder));
            }

            // 5. Add Audiophile/DLNA Headers
            if(contentHolder.getTrack() != null) {
                Track tag = contentHolder.getTrack();
                responseBuilder.addHeader("transferMode.dlna.org", "Streaming");
                responseBuilder.addHeader("contentFeatures.dlna.org", DLNAHeaderHelper.getDLNAContentFeatures(tag));
                if (tag.getAudioSampleRate() > 0)
                    responseBuilder.addHeader("X-Audio-Sample-Rate", tag.getAudioSampleRate() + " Hz");
                if (tag.getAudioBitsDepth() > 0)
                    responseBuilder.addHeader("X-Audio-Bit-Depth", tag.getAudioBitsDepth() + " bit");
                if (tag.getAudioBitRate() > 0)
                    responseBuilder.addHeader("X-Audio-Bitrate", tag.getAudioBitRate() / 1000 + " kbps");
                responseBuilder.addHeader("X-Audio-Format", String.valueOf(tag.getFileType()));
                responseBuilder.addHeader("X-Audio-Bit-Perfect", "true");
            }

            responseTrigger.submitResponse(responseBuilder.build(), context);
        }

        private AsyncEntityProducer getEntityProducer(Context context, ContentHolder contentHolder) {
            File file = new File(contentHolder.getFilePath());
            return AsyncEntityProducers.create(file, ContentType.parse(contentHolder.getContentType()));
        }

        private void submitError(ResponseTrigger responseTrigger, HttpContext context, int status, String message) {
            try {
                final AsyncResponseBuilder responseBuilder = AsyncResponseBuilder.create(status);
                responseBuilder.addHeader(HttpHeaders.SERVER, getServerSignature());
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

        public String generateWebSocketAccept(String key) {
            String GUID = "258EAFA5-E914-47DA-95CA-C5AB0DC85B11";
            try {
                java.security.MessageDigest md = java.security.MessageDigest.getInstance("SHA-1");
                byte[] hash = md.digest((key + GUID).getBytes(java.nio.charset.StandardCharsets.UTF_8));
                return android.util.Base64.encodeToString(hash, android.util.Base64.NO_WRAP);
            } catch (Exception e) {
                return "";
            }
        }

        private class WebSocketIOHandler implements IOEventHandler {
            private final IOSession session;
            private final WebSocket.FrameParser parser = new WebSocket.FrameParser();
            private final ByteArrayOutputStream reassemblyBuffer = new ByteArrayOutputStream(4096);
            private final ByteBuffer readBuffer = ByteBuffer.allocateDirect(65536);
            private int fragmentedOpcode = 0;
            private boolean currentFrameIsFin;
            private int currentFrameOpcode;

            WebSocketIOHandler(IOSession session) {
                this.session = session;
            }

            @Override
            public void connected(IOSession session) {}

            @Override
            public void inputReady(IOSession session, ByteBuffer src) {
                try {
                    if (src == null) {
                        // In some HttpCore states, src might be null, requiring manual read
                        readBuffer.clear();
                        int bytesRead = session.read(readBuffer);
                        if (bytesRead > 0) {
                            readBuffer.flip();
                            src = readBuffer;
                        }
                    }

                    if (src != null && src.hasRemaining()) {
                        parser.parse(src, new WebSocket.FrameParser.FrameDataHandler() {
                            @Override
                            public void onFrameStart(boolean isFin, int opcode, long payloadLength) {
                                currentFrameIsFin = isFin;
                                currentFrameOpcode = opcode;
                                if (opcode != WebSocket.OPCODE_CONTINUATION) {
                                    fragmentedOpcode = opcode;
                                }
                            }

                            @Override
                            public void onFramePayloadData(ByteBuffer payloadChunk) {
                                if (payloadChunk.hasArray()) {
                                    reassemblyBuffer.write(payloadChunk.array(), payloadChunk.arrayOffset() + payloadChunk.position(), payloadChunk.remaining());
                                    payloadChunk.position(payloadChunk.limit());
                                } else {
                                    byte[] data = new byte[payloadChunk.remaining()];
                                    payloadChunk.get(data);
                                    reassemblyBuffer.write(data, 0, data.length);
                                }
                            }

                            @Override
                            public void onFrameEnd() {
                                if (currentFrameIsFin) {
                                    byte[] payload = reassemblyBuffer.toByteArray();
                                    reassemblyBuffer.reset();
                                    handleCompleteMessage(fragmentedOpcode, payload);
                                }
                            }
                        });
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Error parsing WS", e);
                    session.close();
                }
            }

            private void handleCompleteMessage(int opcode, byte[] payload) {
                if (opcode == WebSocket.OPCODE_TEXT) {
                    String text = new String(payload, StandardCharsets.UTF_8);
                    try {
                        Map<String, Object> commandMap = MAPPER.readValue(text, new TypeReference<Map<String, Object>>() {});
                        String command = (String) commandMap.get("command");
                        Map<String, Object> response = handleCommand(command, commandMap);
                        if (response != null) {
                            sendText(session, MAPPER.writeValueAsString(response));
                        }
                    } catch (Exception e) {
                        Log.e(TAG, "Error handling WS command", e);
                    }
                } else if (opcode == WebSocket.OPCODE_CLOSE) {
                    session.close();
                } else if (opcode == WebSocket.OPCODE_PING) {
                    sendFrame(session, new WebSocket.Frame(true, WebSocket.OPCODE_PONG, payload));
                }
            }

            @Override
            public void outputReady(IOSession session) {}

            @Override
            public void timeout(IOSession session, Timeout timeout) {
                session.close();
            }

            @Override
            public void exception(IOSession session, Exception cause) {
                Log.e(TAG, "WS Exception", cause);
                session.close();
            }

            @Override
            public void disconnected(IOSession session) {
                wsSessions.remove(session);
                Log.d(TAG, "WS Disconnected: " + session.getRemoteAddress());
            }
        }
    }
}
