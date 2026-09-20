package apincer.android.jupnp.server.httpcore;

import android.content.Context;
import android.util.Log;



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
import java.io.RandomAccessFile;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.atomic.AtomicInteger;

import apincer.music.core.http.WebSocket;
import apincer.music.core.model.Track;
import apincer.music.core.repository.FileRepository;
import apincer.music.core.repository.TagRepository;
import apincer.music.core.server.BaseServer;
import apincer.music.core.server.ContentHolder;
import apincer.music.core.server.spi.WebServer;
import apincer.music.server.jupnp.transport.DLNAHeaderHelper;

/**
 * HttpCore 5.4.2 Web Server - Production Grade Optimized
 * 
 * Enhanced with:
 * - Object pooling for buffers and connection states
 * - Zero-copy file streaming via FileChannel.transferTo()
 * - GC-optimized memory management
 * - Proper connection state reuse
 * 
 * @author MusicMate Team
 * @version 2.0 (Production Grade)
 */
public class HttpCoreWebServerImpl extends BaseServer implements WebServer {
    private static final String TAG = "HttpCoreWebServerImpl";

    // Connection pool configuration
    private static final int MAX_CONNECTION_STATE_POOL = 100;
    private static final int MAX_BUFFER_POOL = Runtime.getRuntime().availableProcessors() * 2;
    
    // Bounded stream sizes to prevent memory exhaustion
    private static final int MAX_REQUEST_SIZE = 2 * 1024 * 1024; // 2MB
    private static final int MAX_WS_FRAME_SIZE = 1024 * 1024; // 1MB
    
    private HttpAsyncServer server;
    private final Object serverLock = new Object();
    private static final Map<SocketAddress, IOSession> sessionMap = new ConcurrentHashMap<>();
    
    // Connection state pool for reuse
    private final ObjectPool<ConnectionState> connectionPool;
    
    public HttpCoreWebServerImpl(Context context, FileRepository fileRepos, TagRepository tagRepos) {
        super(context, fileRepos, tagRepos);
        addLibInfo("HttpCore55", getVersion());
        
        // Initialize connection state pool
        this.connectionPool = new ObjectPool<>(
            () -> new ConnectionState(MAX_REQUEST_SIZE), 
            ConnectionState::reset,
            MAX_CONNECTION_STATE_POOL
        );
    }

    private String getVersion() {
        VersionInfo versionInfo = VersionInfo.loadVersionInfo("org.apache.hc.core5", null);
        if(versionInfo!= null) {
            return versionInfo.getRelease();
        }
        return "5.5.0-beta2";
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

    public void initServer(InetAddress bindAddress) throws Exception {
        try {
            Log.v(TAG, "Running HttpCore5 Content Server: " + bindAddress.getHostAddress() + ":" + WEB_SERVER_PORT);

            IOReactorConfig config = IOReactorConfig.custom()
                    .setIoThreadCount(2) // Optimized for better concurrency
                    .setSoTimeout(Timeout.ofSeconds(120)) // Increased from 30s to 120s for slow network tolerance
                    .setTcpNoDelay(true) // Reduce latency
                    .setSoKeepAlive(true)
                    .setSndBufSize(524288) // 512KB send buffer for high-rate audio streaming
                    .setSoReuseAddress(true)
                    .setTrafficClass(0x10) // 0x10 = Low Delay
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
                            if (isClientDisconnect(ex)) {
                                Log.d(TAG, "Client disconnected: " + session.getRemoteAddress() + " (" + ex.getMessage() + ")");
                            } else {
                                Log.e(TAG, "Session exception: " + session.getRemoteAddress(), ex);
                            }
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

    private static boolean isClientDisconnect(Exception ex) {
        if (ex == null) return false;
        String msg = ex.getMessage();
        if (msg != null) {
            String lower = msg.toLowerCase();
            if (lower.contains("connection reset") || lower.contains("broken pipe")
                    || lower.contains("connection abort") || lower.contains("closed by peer")
                    || lower.contains("shutdown")) {
                return true;
            }
        }
        Throwable cause = ex.getCause();
        if (cause instanceof Exception && cause != ex) {
            return isClientDisconnect((Exception) cause);
        }
        return ex instanceof java.nio.channels.ClosedChannelException
                || ex instanceof java.io.EOFException
                || ex instanceof java.net.SocketTimeoutException
                || ex instanceof java.net.SocketException;
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
        destroy();
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

                HttpCoreContext coreContext = HttpCoreContext.cast(context);
                EndpointDetails endpoint = coreContext.getEndpointDetails();
                IOSession session = null;
                if (endpoint != null) {
                    session = sessionMap.get(endpoint.getRemoteAddress());
                }

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

    private class ResourceHandler extends WebSocketContent implements AsyncServerRequestHandler<Message<HttpRequest, byte[]>> {
        CopyOnWriteArraySet<IOSession> wsSessions = new CopyOnWriteArraySet<>();
        private final AtomicInteger activeStreams = new AtomicInteger(0);
        
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

            // Send welcome messages
            for (Map<String, Object> msg : getWelcomeMessages()) {
                if (msg == null) continue;
                try {
                    String jsonResponse = apincer.music.core.utils.JsonUtils.toJson(msg);
                    sendText(session, jsonResponse);
                } catch (Exception e) {
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
                    int totalWritten = 0;
                    int totalLimit = buffer.limit();
                    int retries = 0;
                    int maxRetries = 500; // ~10 seconds max with backoff
                    while (buffer.hasRemaining() && session.isOpen() && retries < maxRetries) {
                        int written = session.write(buffer);
                        if (written <= 0) {
                            // Non-blocking socket write buffer full: yield with exponential backoff
                            session.setEvent(java.nio.channels.SelectionKey.OP_WRITE);
                            retries++;
                            long sleepMs = Math.min(20L * retries, 200); // 20ms → 200ms cap
                            try {
                                Thread.sleep(sleepMs);
                            } catch (InterruptedException ignored) {
                                Thread.currentThread().interrupt();
                                break;
                            }
                        } else {
                            totalWritten += written;
                            retries = 0; // reset on successful write
                        }
                    }
                   /* if (buffer.hasRemaining()) {
                        Log.w(TAG, "WS sendText incomplete: wrote " + totalWritten + "/" + totalLimit + " bytes to " + session.getRemoteAddress());
                    } else {
                        Log.d(TAG, "WS sendText wrote " + totalWritten + " bytes (total buffer: " + totalLimit + ") to " + session.getRemoteAddress());
                    } */
                } catch (IOException e) {
                    Log.e(TAG, "Error in WS sendText", e);
                }
            } else {
                Log.w(TAG, "WS sendText skipped, session is closed");
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
                    Map<String, Object> map = (Map<String, Object>) (Map<?, ?>) apincer.music.core.utils.JsonUtils.toMap(jsonString);
                    String command = String.valueOf(map.get("command"));
                    if (!command.isEmpty()) {
                        Map<String, Object> response = handleCommand(command, map);
                        if (response != null) {
                            String jsonResponse = apincer.music.core.utils.JsonUtils.toJson(response);
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
            String requestMethod = request.getHead().getMethod().toUpperCase(Locale.ENGLISH);

            Header uaHeader = request.getHead().getFirstHeader("User-Agent");
            String userAgent = (uaHeader != null) ? uaHeader.getValue() : "Unknown";

            HttpCoreContext coreContext = HttpCoreContext.cast(context);
            EndpointDetails endpoint = coreContext.getEndpointDetails();
            String remoteAddr = endpoint.getRemoteAddress().toString();

            if (!requestMethod.equals("GET") && !requestMethod.equals("HEAD")) {
                Log.d(TAG, "HTTP request isn't GET or HEAD stop! Method was: " + requestMethod);
                throw new MethodNotSupportedException(requestMethod + " method not supported");
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

            // 3. Handle Range Header (Seeking) - RFC 7233 compliant
            Header rangeHeader = request.getHead().getFirstHeader("Range");
            long fileLength = file.length();
            HttpRange range = parseRange(rangeHeader != null ? rangeHeader.getValue() : null, fileLength);

            if (!range.satisfiable) {
                final AsyncResponseBuilder errBuilder = AsyncResponseBuilder.create(HttpStatus.SC_REQUESTED_RANGE_NOT_SATISFIABLE);
                errBuilder.addHeader(HttpHeaders.SERVER, getServerSignature());
                errBuilder.addHeader(HttpHeaders.CONTENT_RANGE, "bytes */" + fileLength);
                responseTrigger.submitResponse(errBuilder.build(), context);
                return;
            }

            long start = range.start;
            long end = range.end;
            long contentLength = range.getContentLength();
            boolean isPartial = range.partial;

            // 4. Build Response with Zero-Copy Streaming
            final AsyncResponseBuilder responseBuilder = AsyncResponseBuilder.create(isPartial ? HttpStatus.SC_PARTIAL_CONTENT : HttpStatus.SC_OK);
            responseBuilder.addHeader(HttpHeaders.SERVER, getServerSignature());
            responseBuilder.addHeader(HttpHeaders.ACCEPT_RANGES, "bytes");
            responseBuilder.addHeader(HttpHeaders.CONNECTION, "keep-alive");
            responseBuilder.addHeader("X-Content-Type-Options", "nosniff");
            responseBuilder.addHeader(HttpHeaders.CACHE_CONTROL, "no-store, no-cache, must-revalidate");

            // Dynamic ETag support
            String etag = generateETag(file);
            responseBuilder.addHeader(HttpHeaders.ETAG, etag);

            if (isPartial) {
                responseBuilder.addHeader(HttpHeaders.CONTENT_RANGE, "bytes " + start + "-" + end + "/" + fileLength);
            }

            if ("HEAD".equalsIgnoreCase(requestMethod)) {
                responseBuilder.addHeader(HttpHeaders.CONTENT_LENGTH, String.valueOf(contentLength));
                if (contentHolder.getContentType() != null) {
                    responseBuilder.addHeader(HttpHeaders.CONTENT_TYPE, contentHolder.getContentType());
                }
            } else {
                responseBuilder.setEntity(new PartialFileProducer(
                        file, start, contentLength, ContentType.parse(contentHolder.getContentType())
                ));
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

        private void submitError(ResponseTrigger responseTrigger, HttpContext context, int status, String message) {
            try {
                final AsyncResponseBuilder responseBuilder = AsyncResponseBuilder.create(status);
                responseBuilder.addHeader(HttpHeaders.SERVER, getServerSignature());
                String entity = "<html><body><h1>" + status + "</h1><p>" + message + "</p></body></html>";
                responseBuilder.setEntity(AsyncEntityProducers.create(entity, ContentType.TEXT_HTML));
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
            private final ConnectionState connectionState;
            private final AtomicInteger messageSize = new AtomicInteger(0);
            
            private final Object stateLock = new Object();
            
            private int fragmentedOpcode = 0;
            private boolean currentFrameIsFin;
            private int currentFrameOpcode;

            WebSocketIOHandler(IOSession session) {
                this.session = session;
                this.connectionState = connectionPool.acquire();
            }

            @Override
            public void connected(IOSession session) {}

            @Override
            public void inputReady(IOSession session, ByteBuffer src) {
                try {
                    if (src == null) {
                        ByteBuffer readBuffer = connectionState.getReadBuffer();
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
                                // Limit frame size
                                if (payloadLength > MAX_WS_FRAME_SIZE) {
                                    Log.w(TAG, "WebSocket frame too large: " + payloadLength);
                                    session.close();
                                    return;
                                }
                            }

                            @Override
                            public void onFramePayloadData(ByteBuffer payloadChunk) {
                                BoundedByteArrayOutputStream reassemblyBuffer = connectionState.getRequestData();
                                try {
                                    if (payloadChunk.hasArray()) {
                                        reassemblyBuffer.write(payloadChunk.array(), 
                                            payloadChunk.arrayOffset() + payloadChunk.position(), 
                                            payloadChunk.remaining());
                                        payloadChunk.position(payloadChunk.limit());
                                    } else {
                                        byte[] data = new byte[payloadChunk.remaining()];
                                        payloadChunk.get(data);
                                        reassemblyBuffer.write(data, 0, data.length);
                                    }
                                } catch (Exception e) {
                                    Log.w(TAG, "WebSocket message size exceeded limit: " + e.getMessage());
                                    session.close();
                                    return;
                                }
                                
                                // Track message size for GC monitoring
                                messageSize.addAndGet(payloadChunk.remaining());
                            }

                            @Override
                            public void onFrameEnd() {
                                if (currentFrameIsFin) {
                                    BoundedByteArrayOutputStream reassemblyBuffer = connectionState.getRequestData();
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
                    Log.d(TAG, "WS Received text: " + text);
                        apincer.music.core.utils.MusicMateExecutors.getExecutorService().execute(() -> {
                            try {
                                Map<String, Object> commandMap = (Map<String, Object>) (Map<?, ?>) apincer.music.core.utils.JsonUtils.toMap(text);
                                String command = (String) commandMap.get("command");
                                Map<String, Object> response = handleCommand(command, commandMap);
                                if (response != null) {
                                    sendText(session, apincer.music.core.utils.JsonUtils.toJson(response));
                                }
                            } catch (Exception e) {
                                Log.e(TAG, "Error handling WS command async", e);
                            }
                        });
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
                
                // Release to pool
                connectionPool.release(connectionState);
                messageSize.set(0);
            }
        }
    }
    
    /**
     * Connection state class for pooling
     */
    private static class ConnectionState {
        private final BoundedByteArrayOutputStream requestData;
        private final ByteBuffer readBuffer;
        private volatile int state = 0; // 0=READING_HEADERS, 1=READING_BODY, 2=WEBSOCKET
        private HttpRequest request;
        private HttpResponse response;
        private long lastActivityTime;
        
        public ConnectionState(int maxRequestSize) {
            this.requestData = new BoundedByteArrayOutputStream(maxRequestSize);
            this.readBuffer = ByteBuffer.allocateDirect(8192);
            this.lastActivityTime = System.currentTimeMillis();
        }
        
        public void reset() {
            requestData.reset();
            readBuffer.clear();
            request = null;
            response = null;
            state = 0;
            lastActivityTime = System.currentTimeMillis();
        }

        public BoundedByteArrayOutputStream getRequestData() {
            return requestData;
        }

        public ByteBuffer getReadBuffer() {
            return readBuffer;
        }
    }
    
    /**
     * Simple Object Pool for reusing objects
     */
    private static class ObjectPool<T> {
        private final java.util.concurrent.ArrayBlockingQueue<T> pool;
        private final java.util.function.Supplier<T> creator;
        private final java.util.function.Consumer<T> resetter;
        
        public ObjectPool(java.util.function.Supplier<T> creator, java.util.function.Consumer<T> resetter, int maxSize) {
            this.creator = creator;
            this.resetter = resetter;
            this.pool = new java.util.concurrent.ArrayBlockingQueue<>(maxSize);
        }
        
        public T acquire() {
            T item = pool.poll();
            if (item == null) {
                item = creator.get();
            }
            return item;
        }
        
        public void release(T item) {
            if (item != null) {
                if (resetter != null) {
                    try {
                        resetter.accept(item);
                    } catch (Exception ignored) {}
                }
                pool.offer(item);
            }
        }
    }
    
    private static class BoundedByteArrayOutputStream extends ByteArrayOutputStream {
        private final int maxSize;
        
        BoundedByteArrayOutputStream(int maxSize) {
            super(Math.min(maxSize, 4096)); // start with 4KB to save memory initially
            this.maxSize = maxSize;
        }
        
        @Override
        public void write(byte[] b, int off, int len) {
            if (count + len > maxSize) {
                throw new IllegalStateException("Buffer size exceeded maximum limit of " + maxSize);
            }
            super.write(b, off, len);
        }
        
        @Override
        public void write(int b) {
            if (count + 1 > maxSize) {
                throw new IllegalStateException("Buffer size exceeded maximum limit of " + maxSize);
            }
            super.write(b);
        }
        
        @Override
        public byte[] toByteArray() {
            return super.toByteArray();
        }
    }

    static class HttpRange {
        public final long start;
        public final long end;
        public final boolean partial;
        public final boolean satisfiable;

        public HttpRange(long start, long end, boolean partial, boolean satisfiable) {
            this.start = start;
            this.end = end;
            this.partial = partial;
            this.satisfiable = satisfiable;
        }

        public long getContentLength() {
            return end - start + 1;
        }
    }

    static HttpRange parseRange(String headerValue, long fileLength) {
        if (headerValue == null || !headerValue.startsWith("bytes=")) {
            return new HttpRange(0, Math.max(0, fileLength - 1), false, true);
        }

        try {
            String value = headerValue.substring(6).trim();
            if (value.contains(",")) {
                value = value.split(",")[0].trim();
            }

            long start = 0;
            long end = fileLength - 1;

            if (value.startsWith("-")) {
                // Suffix range: bytes=-500 (last 500 bytes)
                long suffix = Long.parseLong(value.substring(1).trim());
                start = Math.max(fileLength - suffix, 0);
                end = fileLength - 1;
            } else if (value.endsWith("-")) {
                // Prefix range: bytes=500- (from byte 500 to EOF)
                start = Long.parseLong(value.substring(0, value.length() - 1).trim());
                end = fileLength - 1;
            } else {
                // Explicit range: bytes=500-1000
                String[] parts = value.split("-");
                start = Long.parseLong(parts[0].trim());
                end = Long.parseLong(parts[1].trim());
            }

            // RFC 7233 4.4: If start is past fileLength, range is unsatisfiable
            if (start >= fileLength) {
                return new HttpRange(start, end, true, false);
            }

            // RFC 7233 2.1: Clamp end to fileLength - 1
            if (end >= fileLength) end = fileLength - 1;
            if (start < 0) start = 0;
            if (start > end) start = 0;

            return new HttpRange(start, end, true, true);
        } catch (Exception e) {
            return new HttpRange(0, Math.max(0, fileLength - 1), false, true);
        }
    }
}
