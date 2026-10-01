package apincer.music.core.http;


import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.StandardSocketOptions;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * SonicNIO: MusicMate's built-in HTTP/1.1 and WebSocket server, used for media streaming, the
 * WebUI and UPnP control.
 *
 * <p><b>Model.</b> One selector thread does all socket I/O: it accepts connections, reads requests,
 * and writes responses. A small worker pool runs the {@link Handler} for each complete request and
 * the WebSocket callbacks. Files are streamed with {@code FileChannel.transferTo()} in 256 KB slices,
 * so audio never passes through the Java heap, and the selector yields between slices so one
 * stream cannot starve others.
 *
 * <p><b>Ownership rules</b> (DESIGN.md ADR-036):
 * <ul>
 *   <li>Only the selector thread closes connections, changes interest ops or reads
 *       {@code selector.keys()}. Workers hand back responses through {@code responseQueue} and ask
 *       for closes through {@code pendingCloses}.</li>
 *   <li>A request is handed to a worker once ({@link #dispatch}); the body is the buffered bytes cut
 *       to {@code Content-Length}.</li>
 *   <li>Each WebSocket connection's callbacks run one at a time, in order ({@link SerialExecutor}).</li>
 *   <li>Every response is self-delimiting; unsupported framing (chunked request bodies) gets 501.</li>
 *   <li>{@link #stop()} is final and does not block; teardown releases every connection.</li>
 * </ul>
 *
 * <p><b>Supports:</b> keep-alive and {@code Connection: close}, byte ranges (suffix, open-ended,
 * clamped, 416, {@code If-Range}), ETag/304, HEAD, WebSocket (RFC 6455, 1 MB message limit), limits
 * on connections, concurrent streams (least recently active stream evicted), request size and idle
 * time. <b>Not supported:</b> TLS, HTTP/2, chunked request bodies.
 *
 * <p>Covered end to end by {@code NioHttpServerTest}, {@code NioHttpServerFuzzTest} and
 * {@code NioHttpServerSoakTest}.
 */
public class NioHttpServer implements Runnable {
    // --- HTTP Status Code Constants ---
    public static final int HTTP_SWITCHING_PROTOCOLS = 101;
    public static final int HTTP_OK = 200;
    public static final int HTTP_PARTIAL_CONTENT = 206;
    public static final int HTTP_NOT_MODIFIED = 304;
    public static final int HTTP_BAD_REQUEST = 400;
    public static final int HTTP_NOT_FOUND = 404;
    public static final int HTTP_PAYLOAD_TOO_LARGE = 413;
    public static final int HTTP_PRECONDITION_FAILED = 412;
    public static final int HTTP_RANGE_NOT_SATISFIABLE = 416;
    public static final int HTTP_INTERNAL_ERROR = 500;

    public boolean isRunning() {
        return isRunning;
    }

    private volatile boolean isRunning = false;
    // Set by stop(); a server stopped before its thread reaches run() must not start
    private volatile boolean stopped = false;
    private final int port;
    private Handler httpHandler = null;
    private WebSocket.Handler webSocketHandler = null;
    private Selector selector;
    private ExecutorService workerPool;
    private int maxThread = 0;


    private final AtomicInteger activeStreams = new AtomicInteger(0);
    // An eviction frees its slot on the selector thread shortly after; the new stream is admitted meanwhile
    private final StreamSlots streamSlots = new StreamSlots() {
        @Override
        public boolean acquire() {
            if (activeStreams.get() >= maxConcurrentStreams && !tryEvictOldestStream()) return false;
            activeStreams.incrementAndGet();
            return true;
        }

        @Override
        public void release() {
            activeStreams.decrementAndGet();
        }
    };

    private final AtomicInteger activeConnections = new AtomicInteger(0);
    private int maxConnections = 1000; // Configurable

    // --- Tuning Parameters ---
    private int socketBacklog = 128;
    private int clientReadBufferSize = 8192;
    private boolean tcpNoDelay = true;
    private long keepAliveTimeout = 120_000; // 120 seconds for music streaming on poor network
    private long lastTimeoutCheck = 0;
    private int maxRequestSize = 2 * 1024 * 1024; // 2MB for requests (not file size)
    private int maxWebSocketFrameSize = 1024 * 1024; // 1MB max WebSocket frame
    private long selectorTimeout = 1000; // Milliseconds
    private int maxConcurrentStreams = Runtime.getRuntime().availableProcessors() * 2; // 2× CPU cores

    // A thread-safe queue for worker threads to hand off completed responses to the I/O thread.
    private final Queue<ResponseTask> responseQueue = new ConcurrentLinkedQueue<>();
    private final Queue<NioWebSocketConnection> pendingWebSocketWrites = new ConcurrentLinkedQueue<>();
    // Connections currently streaming a file, maintained on the selector thread. Workers read it to
    // pick an eviction victim instead of touching selector.keys(), which is not thread-safe.
    private final Set<SelectionKey> streamingKeys = java.util.concurrent.ConcurrentHashMap.newKeySet();
    // Connections to close on the selector thread: evicted streams and forceClose() from any thread.
    private final Queue<SelectionKey> pendingCloses = new ConcurrentLinkedQueue<>();


    public NioHttpServer(int port) {
        this.port = port;
    }

    public void setMaxThread(int maxThread) {
        this.maxThread = maxThread;
    }

    public void setMaxRequestSize(int maxRequestSize) {
        this.maxRequestSize = maxRequestSize;
    }

    public void setMaxWebSocketFrameSize(int maxWebSocketFrameSize) {
        this.maxWebSocketFrameSize = maxWebSocketFrameSize;
    }

    public void setSelectorTimeout(long milliseconds) {
        this.selectorTimeout = milliseconds;
    }

    public void setMaxConnections(int max) {
        this.maxConnections = max;
    }

    public void setMaxConcurrentStreams(int max) {
        this.maxConcurrentStreams = max;
    }

    /**
     * Registers a main http handler.
     *
     * @param handler the handler instance
     */
    public void registerHttpHandler(Handler handler) {
        this.httpHandler = handler;
    }

    /**
     * Registers a WebSocket handler for the given path.
     *
     * @param handler the WebSocket handler instance
     */
    public void registerWebSocketHandler(WebSocket.Handler handler) {
        this.webSocketHandler = handler;
    }

    public void setSocketBacklog(int socketBacklog) {
        this.socketBacklog = socketBacklog;
    }

    public void setClientReadBufferSize(int clientReadBufferSize) {
        this.clientReadBufferSize = clientReadBufferSize;
    }

    public void setTcpNoDelay(boolean tcpNoDelay) {
        this.tcpNoDelay = tcpNoDelay;
    }

    public void setKeepAliveTimeout(long milliseconds) {
        this.keepAliveTimeout = milliseconds;
    }

    public void stop() {
        stopped = true;
        isRunning = false;
        // Don't wait for busy handlers here: callers may be on a UI or control thread. The pool
        // stops taking work now, and run() finishes the teardown when its loop exits.
        ExecutorService pool = workerPool;
        if (pool != null) pool.shutdown();
        if (selector != null) selector.wakeup();
    }

    /**
     * Registers OP_WRITE for WebSockets with pending outgoing frames.
     */
    private void processWebSocketWrites() {
        NioWebSocketConnection conn;
        while ((conn = pendingWebSocketWrites.poll()) != null) {
            conn.writeInterestQueued.set(false);
            // A connection that is closing still has its CLOSE frame queued; forceClose() empties the queue
            if (conn.key.isValid() && !conn.outgoingQueue.isEmpty()) {
                try {
                    int currentOps = conn.key.interestOps();
                    if ((currentOps & SelectionKey.OP_WRITE) == 0) {
                        conn.key.interestOps(currentOps | SelectionKey.OP_WRITE);
                    }
                } catch (java.nio.channels.CancelledKeyException e) {
                    // Key was cancelled concurrently, ignore.
                }
            }
        }
    }

    @Override
    public void run() {
        isRunning = true;
        // Checked after setting isRunning: whichever order stop() and run() interleave in, the server ends stopped
        if (stopped) {
            isRunning = false;
            return;
        }


        if (maxThread <= 0) {
            maxThread = Runtime.getRuntime().availableProcessors();
        }
        int coreCount = Math.max(2, maxThread);
        ThreadPoolExecutor executor = new ThreadPoolExecutor(
                coreCount, // corePoolSize
                coreCount, // maximumPoolSize (queue is unbounded, so setting maximumPoolSize higher is a no-op)
                60L, // keepAliveTime: Time for idle threads to live
                TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(), // The queue for waiting tasks
                r -> {
                    Thread t = new Thread(r, "NIO-Worker");
                    t.setDaemon(true);
                    return t;
                }
        );
        executor.allowCoreThreadTimeOut(true); // Allow idle core threads to time out and release native stack memory
        workerPool = executor;
        //System.out.println("Worker pool started with " + coreCount + " threads.");

        // The "supervisor" loop is now on the outside.
        while (isRunning) {
            // The try-with-resources is now INSIDE the loop.
            try (ServerSocketChannel serverSocketChannel = ServerSocketChannel.open();
                 Selector newSelector = Selector.open()) {

                this.selector = newSelector; // Assign to the class-level field

                serverSocketChannel.setOption(StandardSocketOptions.SO_REUSEADDR, true);
                serverSocketChannel.bind(new InetSocketAddress(port), socketBacklog);
                serverSocketChannel.configureBlocking(false);
                serverSocketChannel.register(selector, SelectionKey.OP_ACCEPT);

                // System.out.println("Multi-threaded NIO Server started on port: " + port);
                lastTimeoutCheck = System.currentTimeMillis();

                // This is the inner I/O processing loop.
                try {
                    int selectCnt = 0;
                    while (isRunning) {
                        processResponseQueue();
                        processWebSocketWrites();
                        processPendingCloses();

                        long selectStart = System.currentTimeMillis();
                        int selectedKeysCount = selector.select(selectorTimeout);
                        long selectDuration = System.currentTimeMillis() - selectStart;

                        if (selectedKeysCount == 0 && isRunning) {
                            handleIdleConnections();

                            // Check for spin bug: select returned 0 too quickly
                            if (selectDuration < 50) { // Should have blocked for selectorTimeout (e.g. 1000ms)
                                selectCnt++;
                                if (selectCnt > 10) {
                                    // Spurious selector wakeup, apply a small backoff sleep to protect CPU
                                    try {
                                        Thread.sleep(20);
                                    } catch (InterruptedException e) {
                                        Thread.currentThread().interrupt();
                                    }
                                }
                            } else {
                                selectCnt = 0;
                            }
                            continue;
                        }
                        selectCnt = 0;

                        if (!isRunning) break;

                        Set<SelectionKey> selectedKeys = selector.selectedKeys();
                        Iterator<SelectionKey> keyIterator = selectedKeys.iterator();

                        while (keyIterator.hasNext()) {
                            SelectionKey key = keyIterator.next();
                            keyIterator.remove();
                            try {
                                if (!key.isValid()) continue;
                                if (key.isAcceptable()) {
                                    handleAccept(key);
                                } else if (key.isReadable()) {
                                    handleRead(key);
                                } else if (key.isWritable()) {
                                    handleWrite(key);
                                }
                            } catch (IOException e) {
                                // String msg = e.getMessage();
                                //if (msg != null && (msg.contains("Connection reset by peer") || msg.contains("Broken pipe"))) {
                                // Quietly log common client disconnects
                                //} else {
                                //    System.err.println("I/O error handling key: " + e.getMessage());
                                // }
                                closeConnection(key);
                            } catch (Exception e) {
                                System.err.println("Error handling key: " + e.getClass().getSimpleName() + " - " + e.getMessage());
                                closeConnection(key);
                            }
                        }
                        handleIdleConnections();
                    }
                } finally {
                    // Release every client connection registered with this selector, on stop or before
                    // recreating it after an error. closeConnection() also closes open file streams and
                    // updates the stream/connection counters; closing only the channels leaked them.
                    try {
                        for (SelectionKey key : new java.util.ArrayList<>(newSelector.keys())) {
                            try {
                                if (key.channel() instanceof SocketChannel) {
                                    closeConnection(key);
                                } else if (key.channel() != null) {
                                    key.channel().close();
                                }
                            } catch (Exception ignored) {}
                        }
                    } catch (Exception ignored) {}
                    pendingCloses.clear();
                }
            } catch (Exception e) {
                // This now catches errors with binding the socket or with the selector itself.
                System.err.println("Server main loop error, will try to recover: " + e.getMessage());
                e.printStackTrace();
                try {
                    // Wait a moment before trying to re-bind the socket to prevent a fast spin-loop.
                    Thread.sleep(1000);
                } catch (InterruptedException interruptedException) {
                    Thread.currentThread().interrupt();
                }
            }
        } // The outer while-loop will restart here if there was a major error.

        // Final cleanup when isRunning is set to false.
        if (workerPool != null && !workerPool.isShutdown()) workerPool.shutdownNow();
        System.out.println("NIO Server stopped.");
    }

    private void handleIdleConnections() {
        long now = System.currentTimeMillis();
        if (now - lastTimeoutCheck > keepAliveTimeout) {
            long totalRequestBufferSize = 0;
            int activeFileStreams = 0;

            for (SelectionKey key : selector.keys()) {
                if (key.isValid() && key.attachment() instanceof ConnectionAttachment attachment) {
                    // Skip connections currently being processed on worker threads (interestOps is 0)
                    try {
                        if (key.interestOps() == 0) {
                            continue;
                        }
                    } catch (java.nio.channels.CancelledKeyException e) {
                        continue;
                    }

                    // Track memory usage
                    if (attachment.requestData != null) {
                        totalRequestBufferSize += attachment.requestData.size();
                    }

                    // Count active file streams
                    if (attachment.response instanceof FileResponse) {
                        activeFileStreams++;
                    }

                    long idleTimeout = (attachment.state == ConnectionAttachment.ParseState.WEBSOCKET_FRAME)
                            ? keepAliveTimeout * 4  // WebSockets: 4× the HTTP idle timeout
                            : keepAliveTimeout;     // HTTP keep-alive idle timeout (default 120 s)
                    if (now - attachment.lastActivityTime > idleTimeout) {
                        if (attachment.state == ConnectionAttachment.ParseState.WEBSOCKET_FRAME) {
                            System.out.println("Closing idle WebSocket connection.");
                        } else {
                            System.out.println("Closing idle HTTP connection.");
                        }
                        closeConnection(key);
                    }
                }
            }

            // Memory warning
            if (totalRequestBufferSize > 100 * 1024 * 1024) { // 100MB threshold
                System.err.println("WARNING: High memory usage in request buffers: " +
                        (totalRequestBufferSize / 1024 / 1024) + "MB across " +
                        selector.keys().size() + " connections");
            }

            // Stream count warning
            if (activeFileStreams > maxConcurrentStreams * 0.8) {
                System.err.println("WARNING: High concurrent stream count: " + activeFileStreams +
                        "/" + maxConcurrentStreams);
            }

            lastTimeoutCheck = now;
        }
    }

    private void processResponseQueue() {
        ResponseTask task;
        while ((task = responseQueue.poll()) != null) {
            SelectionKey key = task.key;
            if (key.isValid() && key.attachment() instanceof ConnectionAttachment attachment) {
                attachment.response = task.response;
                attachment.wsHandler = task.wsHandler; // Carry over the handler for handshake
                if (task.response instanceof FileResponse) {
                    streamingKeys.add(key);
                }
                key.interestOps(SelectionKey.OP_WRITE);
            } else {
                // Client disconnected before the response was delivered.
                // Close the response to release any open FileChannel and decrement activeStreams.
                if (task.response != null) {
                    try {
                        task.response.close();
                    } catch (IOException ignore) {
                    }
                }
            }
        }
    }

    private void handleAccept(SelectionKey key) throws IOException {
        ServerSocketChannel serverChannel = (ServerSocketChannel) key.channel();
        SocketChannel clientChannel = serverChannel.accept();

        // In NIO non-blocking mode accept() can return null on a spurious wakeup.
        if (clientChannel == null) return;

        if (activeConnections.get() >= maxConnections) {
            // Send 503 Service Unavailable and drop the connection immediately.
            ByteBuffer response = ByteBuffer.wrap(
                    "HTTP/1.1 503 Service Unavailable\r\nConnection: close\r\nContent-Length: 0\r\n\r\n"
                            .getBytes(StandardCharsets.UTF_8)
            );
            try {
                clientChannel.write(response);
            } finally {
                clientChannel.close();
            }
            return;
        }

        // Increment AFTER a successful accept so the counter is never inflated if
        // accept() throws or returns null.
        activeConnections.incrementAndGet();

        clientChannel.configureBlocking(false);
        clientChannel.setOption(StandardSocketOptions.TCP_NODELAY, tcpNoDelay);

        clientChannel.setOption(StandardSocketOptions.SO_REUSEADDR, true);
        clientChannel.setOption(StandardSocketOptions.SO_SNDBUF, 524288); // 512KB for hi-res streaming
        clientChannel.setOption(StandardSocketOptions.IP_TOS, 0x18); // 0x18 = Low Delay (0x10) | High Throughput (0x08)

        ConnectionAttachment attachment = new ConnectionAttachment(clientReadBufferSize);
        clientChannel.register(selector, SelectionKey.OP_READ, attachment);
    }

    private void handleRead(SelectionKey key) throws IOException {
        ConnectionAttachment attachment = (ConnectionAttachment) key.attachment();
        attachment.lastActivityTime = System.currentTimeMillis();
        /*
        if (attachment.state == ConnectionAttachment.ParseState.WEBSOCKET_FRAME) {
            handleWebSocketRead(key);
            return;
        }*/

        // Explicit state validation
        ConnectionAttachment.ParseState currentState = attachment.state;

        if (currentState == ConnectionAttachment.ParseState.WEBSOCKET_FRAME) {
            if (!attachment.isWebSocketState()) {
                // State changed! Close connection
                closeConnection(key);
                return;
            }
            handleWebSocketRead(key);
            return;
        }

        // HTTP reading...
        SocketChannel clientChannel = (SocketChannel) key.channel();
        int bytesRead = clientChannel.read(attachment.readBuffer);
        if (bytesRead == -1) {
            closeConnection(key);
            return;
        }
        if (bytesRead == 0) return;

        attachment.readBuffer.flip();
        attachment.requestData.write(attachment.readBuffer.array(), 0, attachment.readBuffer.limit());
        attachment.readBuffer.clear();

        // if (attachment.state == ConnectionAttachment.ParseState.READING_HEADERS) {
        if (currentState == ConnectionAttachment.ParseState.READING_HEADERS) {
            // Scan only the bytes added since the last read; copy the buffer once the headers are complete
            int headerEnd = attachment.requestData.indexOfHeaderEnd(attachment.headerScanFrom);
            if (headerEnd == -1) {
                attachment.headerScanFrom = Math.max(0, attachment.requestData.size() - 3);
            } else {
                byte[] requestBytes = attachment.requestData.toByteArray();
                // --- Acquire and parse ---
                HttpRequest request = new HttpRequest();
                request.parse(requestBytes, headerEnd, ((InetSocketAddress) clientChannel.getRemoteAddress()).getAddress().getHostAddress());

                // Chunked request bodies are not supported; misreading them would turn the chunks into a bogus next request
                if (request.getHeader("transfer-encoding", "").toLowerCase().contains("chunked")) {
                    attachment.response = new HttpResponse()
                            .setStatus(501, "Not Implemented")
                            .addHeader("Connection", "close")
                            .setBody("Chunked request bodies are not supported".getBytes());
                    key.interestOps(SelectionKey.OP_WRITE);
                    return;
                }

                // Validate content length
                int contentLength = Integer.parseInt(request.getHeader("content-length", "0"));
                if (contentLength < 0 || contentLength > maxRequestSize) {
                    attachment.response = contentLength < 0
                            ? new HttpResponse().setStatus(HTTP_BAD_REQUEST, "Bad Request")
                                    .addHeader("Connection", "close")
                                    .setBody("Invalid Content-Length".getBytes())
                            : new HttpResponse().setStatus(HTTP_PAYLOAD_TOO_LARGE, "Payload Too Large")
                                    .addHeader("Connection", "close")
                                    .setBody("Content-Length exceeds limit".getBytes());
                    key.interestOps(SelectionKey.OP_WRITE);
                    return;
                }

                attachment.wsUpgradeHeaderEnd = headerEnd; // Save before worker recycles the request

                if (requestBytes.length - headerEnd >= contentLength) {
                    // Exactly Content-Length bytes; anything after belongs to the next request
                    request.setBody(Arrays.copyOfRange(requestBytes, headerEnd, headerEnd + contentLength));
                    dispatch(key, attachment, request);
                } else {
                    attachment.request = request;
                    attachment.state = ConnectionAttachment.ParseState.READING_BODY;
                }
            }
            //} else if (attachment.state == ConnectionAttachment.ParseState.READING_BODY) {
        } else if (currentState == ConnectionAttachment.ParseState.READING_BODY) {
            // parse body...
            int contentLength = Integer.parseInt(attachment.request.getHeader("content-length", "0"));
            if (attachment.bodyReadStartTime == 0) {
                attachment.bodyReadStartTime = System.currentTimeMillis();
            }

            // Check timeout
            if (System.currentTimeMillis() - attachment.bodyReadStartTime > ConnectionAttachment.BODY_READ_TIMEOUT) {
                closeConnection(key);
                return;
            }

            HttpRequest request = attachment.request;
            int headerEnd = request.getHeaderEnd();
            if (attachment.requestData.size() - headerEnd >= contentLength) {
                // The body parsed with the headers was only the first segment; take it from all buffered bytes
                byte[] allBytes = attachment.requestData.toByteArray();
                request.setBody(Arrays.copyOfRange(allBytes, headerEnd, headerEnd + contentLength));
                attachment.bodyReadStartTime = 0;  // Reset
                dispatch(key, attachment, request);
            }
        } else {
            // Unexpected state!
            System.err.println("Unexpected state: " + currentState);
            closeConnection(key);
        }
    }

    /**
     * Hands a complete request to a worker, which owns it from here; the attachment drops its
     * reference so nothing on the selector thread touches the request again.
     */
    private void dispatch(SelectionKey key, ConnectionAttachment attachment, HttpRequest request) {
        attachment.request = null;
        key.interestOps(0);
        if (workerPool == null || workerPool.isShutdown()) {
            closeConnection(key); // Silently close connection if server is stopping
            return;
        }
        workerPool.submit(() -> processRequest(key, request));
    }

    private void processRequest(SelectionKey key, HttpRequest request) {
        try {
            // Reject malformed request lines (parse() sets method/path to null).
            if (request.getMethod() == null || request.getPath() == null) {
                HttpResponse badRequest = new HttpResponse()
                        .setStatus(HTTP_BAD_REQUEST, "Bad Request")
                        .addHeader("Connection", "close")
                        .setBody("Malformed request line".getBytes(StandardCharsets.UTF_8));
                responseQueue.add(new ResponseTask(key, badRequest));
                selector.wakeup();
                return;
            }

            String path = request.getPath();

            String normalizedPath = path.contains("?") ? path.substring(0, path.indexOf("?")) : path;
            if (normalizedPath.endsWith("/") && normalizedPath.length() > 1) {
                normalizedPath = normalizedPath.substring(0, normalizedPath.length() - 1);
            }

            if (webSocketHandler != null && webSocketHandler.getNamespace().equals(normalizedPath)) {
                // Check for WebSocket upgrade first
                String upgradeHeader = request.getHeader("upgrade", "");
                String connectionHeader = request.getHeader("connection", "");

                if ("websocket".equalsIgnoreCase(upgradeHeader) &&
                        connectionHeader.toLowerCase().contains("upgrade")) {
                    try {
                        HttpResponse handshakeResponse = WebSocketHandshake.createHandshakeResponse(request);
                        responseQueue.add(new ResponseTask(key, handshakeResponse, webSocketHandler));
                        selector.wakeup();
                    } catch (NoSuchAlgorithmException e) {
                        HttpResponse errorResponse = new HttpResponse()
                                .setStatus(HTTP_INTERNAL_ERROR, "Internal Server Error")
                                .setBody("WebSocket handshake failed".getBytes());
                        responseQueue.add(new ResponseTask(key, errorResponse));
                        selector.wakeup();
                    }
                    return; // Early return after WebSocket handling
                }
            }

            HttpResponse response; // Declare response outside the try block
            if (httpHandler != null) {
                try {
                    // Execute the handler directly on this worker thread
                    response = httpHandler.handle(request);
                } catch (Exception e) {
                    // Handle exceptions from the handler
                    System.err.println("Handler error: " + e.getMessage());
                    response = new HttpResponse()
                            .setStatus(HTTP_INTERNAL_ERROR, "Internal Server Error");
                    if (e.getMessage() != null) {
                        response.setBody(e.getMessage().getBytes());
                    }
                }
            } else {
                // No handler found - return 404
                response = new HttpResponse()
                        .setStatus(HTTP_NOT_FOUND, "Not Found")
                        .setBody("404 Not Found".getBytes());
            }

            // The client asked to close after this response (HTTP/1.1 Connection: close)
            if ("close".equalsIgnoreCase(request.getHeader("connection", ""))) {
                response.addHeader("Connection", "close");
            }

            // Queue the response (either success, 404, or 500)
            responseQueue.add(new ResponseTask(key, response));
            selector.wakeup();

        } catch (Exception e) {
            System.err.println("Error processing request: " + e.getMessage());
            HttpResponse errorResponse = new HttpResponse()
                    .setStatus(HTTP_INTERNAL_ERROR, "Internal Server Error");
            if (e.getMessage() != null) {
                errorResponse.setBody(e.getMessage().getBytes());
            }
            responseQueue.add(new ResponseTask(key, errorResponse));
            selector.wakeup();
        }
    }


    private void handleWrite(SelectionKey key) throws IOException {
        ConnectionAttachment attachment = (ConnectionAttachment) key.attachment();
        attachment.lastActivityTime = System.currentTimeMillis();

        if (attachment.state == ConnectionAttachment.ParseState.WEBSOCKET_FRAME) {
            handleWebSocketWrite(key);
            return;
        }

        if (attachment.response == null) return;

        SocketChannel clientChannel = (SocketChannel) key.channel();
        attachment.response.write(clientChannel);

        if (attachment.response.isFullySent()) {

            if (attachment.response.statusCode == HTTP_SWITCHING_PROTOCOLS && attachment.wsHandler != null) {
                // Case 1: The connection was just upgraded to a WebSocket.
                // ATOMIC UPGRADE with proper handler cleanup and volatile flag check
                synchronized (attachment) {
                    attachment.upgradeToWebSocket(key);

                    WebSocket.Handler currentWsHandler = attachment.wsHandler;

                    // Check if WebSocket has queued messages (using volatile flag)
                    if (attachment.wsConnection != null && attachment.wsConnection.hasOutgoingQueue) {
                        attachment.wsConnection.hasOutgoingQueue = false;
                        selector.wakeup();
                    }

                    // Queue onOpen first on this connection's serial executor, ahead of any message
                    if (currentWsHandler != null) {
                        final NioWebSocketConnection connection = attachment.wsConnection;
                        attachment.wsTasks.execute(() -> {
                            try {
                                currentWsHandler.onOpen(connection);
                            } catch (Exception e) {
                                currentWsHandler.onError(connection, e);
                            }
                        });
                    }

                    // Only NOW enable reads
                    key.interestOps(SelectionKey.OP_READ);
                }

            } else if ("close".equalsIgnoreCase(attachment.response.headers.get("Connection"))) {
                // Case 2: The response headers indicate the connection should be closed.

                closeConnection(key);
            } else {
                // Case 3: It's a standard HTTP keep-alive connection. Reset for the next request.

                streamingKeys.remove(key);
                attachment.reset();
                key.interestOps(SelectionKey.OP_READ);
            }
        }
    }

    /**
     * Called on a worker thread when the stream limit is reached. Picks the least recently active
     * stream and queues it; the selector thread closes it in {@link #processPendingCloses()}.
     * @return true if a stream was queued for eviction
     */
    private boolean tryEvictOldestStream() {
        SelectionKey oldestKey = null;
        long oldestActivityTime = Long.MAX_VALUE;
        for (SelectionKey key : streamingKeys) {
            if (key.isValid() && key.attachment() instanceof ConnectionAttachment attachment
                    && attachment.lastActivityTime < oldestActivityTime) {
                oldestActivityTime = attachment.lastActivityTime;
                oldestKey = key;
            }
        }
        if (oldestKey == null || !streamingKeys.remove(oldestKey)) {
            return false; // nothing to evict, or another worker already took it
        }
        System.out.println("Evicting oldest active stream connection. Last activity: " +
                (System.currentTimeMillis() - oldestActivityTime) + "ms ago.");
        requestClose(oldestKey);
        return true;
    }

    /** Any thread: asks the selector thread to write this connection's queued frames. */
    void requestWebSocketWrite(NioWebSocketConnection connection) {
        if (connection.writeInterestQueued.compareAndSet(false, true)) {
            pendingWebSocketWrites.add(connection);
        }
        Selector current = selector;
        if (current != null) current.wakeup();
    }

    /** Any thread: asks the selector thread to close this connection through closeConnection(). */
    void requestClose(SelectionKey key) {
        pendingCloses.add(key);
        Selector current = selector;
        if (current != null) current.wakeup();
    }

    /** Closes connections queued by {@link #tryEvictOldestStream()} or forceClose(), on the selector thread. */
    private void processPendingCloses() {
        SelectionKey key;
        while ((key = pendingCloses.poll()) != null) {
            if (key.isValid()) closeConnection(key);
        }
    }

    private void closeConnection(SelectionKey key) {
        if (key == null) return;
        streamingKeys.remove(key);

        if (key.channel() instanceof SocketChannel socketChannel) {
            Object attachmentObj = key.attachment();
            if (attachmentObj instanceof ConnectionAttachment attachment) {
                key.attach(null); // Clear attachment immediately to prevent double close

                attachment.request = null;

                if (attachment.state == ConnectionAttachment.ParseState.WEBSOCKET_FRAME && attachment.wsHandler != null) {
                    WebSocket.Handler currentWsHandler = attachment.wsHandler;
                    NioWebSocketConnection currentWsConn = attachment.wsConnection;
                    SerialExecutor tasks = attachment.wsTasks;
                    attachment.wsHandler = null; // Clear to prevent double calls
                    // After this connection's pending messages; dropped quietly once the pool is shut down
                    if (tasks != null) {
                        tasks.execute(() -> {
                            try {
                                currentWsHandler.onClose(currentWsConn, WebSocket.CLOSE_ABNORMAL, "Connection closed abnormally");
                            } catch (Exception e) {
                                // Log error during close if necessary
                            }
                        });
                    }
                }
                if (attachment.response != null) {
                    try {
                        attachment.response.close();
                    } catch (IOException ignore) {}
                    attachment.response = null;
                }
                // Clean up WebSocket buffers
                attachment.cleanup();
                attachment.reset();

                try {
                    socketChannel.close();
                } catch (IOException e) { /* ignore */ }
                key.cancel();
                activeConnections.decrementAndGet();
            } else {
                // If attachment is already null, but channel is open, close it without double decrementing
                try {
                    socketChannel.close();
                } catch (IOException e) { /* ignore */ }
                key.cancel();
            }
        }
    }

    // --- WebSocket Specific Methods ---
    private void handleWebSocketRead(SelectionKey key) throws IOException {
        ConnectionAttachment attachment = (ConnectionAttachment) key.attachment();
        SocketChannel channel = (SocketChannel) key.channel();

        int bytesRead = channel.read(attachment.readBuffer);
        if (bytesRead == -1) {
            closeConnection(key);
            return;
        }
        if (bytesRead == 0) return;

        attachment.readBuffer.flip();

        // MODIFIED: Call the new parser with the attachment itself as the handler
        try {
            attachment.wsFrameParser.parse(attachment.readBuffer, attachment);
        } catch (RuntimeException protocolError) {
            // Malformed or oversized frame: stop reading and end with a CLOSE frame queued last
            // (1002, or the 1009 onFrameStart already queued). Writing that frame closes the
            // connection; replies queued before it (e.g. a PONG) still go out first.
            attachment.readBuffer.clear();
            NioWebSocketConnection connection = attachment.wsConnection;
            if (connection != null) {
                connection.close(WebSocket.CLOSE_PROTOCOL_ERROR, "Protocol error");
            }
            if (connection != null && !connection.getOutgoingQueue().isEmpty()) {
                key.interestOps(SelectionKey.OP_WRITE);
            } else {
                closeConnection(key);
            }
            return;
        }

        attachment.readBuffer.compact();

        // Bug fix: a WebSocket CLOSE frame sets pendingClose=true inside processCompleteFrame
        // so that closeConnection() is called here (after the parse chain has fully returned)
        // rather than from inside the parse callbacks. This ensures activeConnections is
        // decremented and the attachment is released back to the pool correctly.
        if (attachment.pendingClose) {
            closeConnection(key);
        }
    }

    private void handleWebSocketWrite(SelectionKey key) throws IOException {
        ConnectionAttachment attachment = (ConnectionAttachment) key.attachment();
        SocketChannel channel = (SocketChannel) key.channel();
        Queue<WebSocket.Frame> queue = attachment.wsConnection.getOutgoingQueue();

        while (true) {
            // 1. Get a buffer to write.
            // If we have a partially written one, use it. Otherwise, poll the queue.
            if (attachment.pendingWriteBuffer == null) {
                WebSocket.Frame frame = queue.poll();
                if (frame == null) {
                    // Queue is empty. Remove OP_WRITE and stop.
                    key.interestOps(key.interestOps() & ~SelectionKey.OP_WRITE);
                    return;
                }
                // Generate the buffer ONCE per frame.
                attachment.pendingWriteBuffer = frame.toByteBuffer();
                attachment.closeAfterWrite = frame.getOpcode() == WebSocket.OPCODE_CLOSE;
            }

            // 2. Continuous write attempt
            channel.write(attachment.pendingWriteBuffer);

            // 3. If the buffer still has data, the TCP window is full.
            // Exit and wait for the next OP_WRITE signal.
            if (attachment.pendingWriteBuffer.hasRemaining()) {
                return;
            }

            // 4. Frame finished. Clear the pending buffer to allow the next loop
            // to poll the next frame from the queue.
            attachment.pendingWriteBuffer = null;

            // 5. Our CLOSE frame is out: end the connection here, on the selector thread
            if (attachment.closeAfterWrite) {
                closeConnection(key);
                return;
            }
        }
    }

    public HttpResponse createFileResponse(File file, HttpRequest request) throws IOException {
        try {
            return new FileResponse(file, request, streamSlots);
        } catch (IOException e) {
            if (e.getMessage() != null && e.getMessage().startsWith("Service Unavailable")) {
                return new HttpResponse()
                        .setStatus(503, "Service Unavailable")
                        .addHeader("Retry-After", "5")
                        .setBody(e.getMessage().getBytes());
            }
            return new HttpResponse()
                    .setStatus(HTTP_NOT_FOUND, "Not Found")
                    .setBody("File not found".getBytes());
        }
    }

    // --- INNER CLASSES AND INTERFACES ---
    @FunctionalInterface
    public interface Handler {
        HttpResponse handle(HttpRequest request);
    }

    private class ConnectionAttachment implements WebSocket.FrameParser.FrameDataHandler { // MODIFIED: implements handler
        enum ParseState {READING_HEADERS, READING_BODY, WEBSOCKET_FRAME}

        final ByteBuffer readBuffer;
        BoundedByteArrayOutputStream requestData;
        int headerScanFrom = 0; // where the next header-end scan starts
        private volatile ParseState state = ParseState.READING_HEADERS;
        HttpRequest request;
        HttpResponse response;
        volatile long lastActivityTime; // read by workers choosing an eviction victim

        // WebSocket specific fields
        WebSocket.Handler wsHandler;
        NioWebSocketConnection wsConnection;
        WebSocket.FrameParser wsFrameParser;
        // Runs this connection's onOpen/onMessage/onClose one at a time, in order
        SerialExecutor wsTasks;

        private ByteArrayOutputStream reassemblyBuffer;
        private volatile int fragmentedOpcode = 0;
        private volatile boolean currentFrameIsFin;
        private volatile int currentFrameOpcode;
        private final Object wsFrameLock = new Object();

        // Control frame buffer for immediate handling
        private ByteArrayOutputStream controlFrameBuffer;

        ByteBuffer pendingWriteBuffer = null;
        // Set while the frame being written is our CLOSE frame; the socket closes once it is out
        boolean closeAfterWrite = false;
        private final Object stateLock = new Object();
        private long bodyReadStartTime = 0;
        public static final long BODY_READ_TIMEOUT = 120_000; // 120 seconds for slow networks
        // Set when a WebSocket CLOSE frame is received; triggers proper closeConnection()
        // after the current parse cycle finishes, ensuring activeConnections is decremented.
        volatile boolean pendingClose = false;
        // Saved at HTTP parse time so upgradeToWebSocket() never reads headerEnd from the
        // request object (which the worker thread's finally-block zeroes via request.reset()
        // before the I/O thread calls upgradeToWebSocket()).
        int wsUpgradeHeaderEnd = 0;

        public ConnectionAttachment(int readBufferSize) {
            this.readBuffer = ByteBuffer.allocate(readBufferSize);
            // Use bounded stream with max request size
            this.requestData = createByteArrayOutputStream();
            this.lastActivityTime = System.currentTimeMillis();
        }

        // Atomic state validation
        private boolean isWebSocketState() {
            return state == ParseState.WEBSOCKET_FRAME;
        }

        private boolean isHttpState() {
            return state == ParseState.READING_HEADERS ||
                    state == ParseState.READING_BODY;
        }

        public void reset() {
            // HTTP cleanup
            if (requestData != null) {
                try {
                    requestData.close();
                } catch (IOException ignore) {
                }
            }
            requestData = createByteArrayOutputStream();
            headerScanFrom = 0;
            request = null;

            if (response != null) {
                try {
                    response.close();
                } catch (IOException ignore) {
                }
                response = null;
            }

            // ✅ WEBSOCKET CLEANUP (was missing!)
            if (wsConnection != null) {
                try {
                    wsConnection.forceClose();
                } catch (Exception ignore) {
                }
            }
            wsConnection = null;

            if (reassemblyBuffer != null) {
                try {
                    reassemblyBuffer.close();
                } catch (IOException ignore) {
                }
            }
            reassemblyBuffer = null;

            if (controlFrameBuffer != null) {
                try {
                    controlFrameBuffer.close();
                } catch (IOException ignore) {
                }
            }
            controlFrameBuffer = null;

            if (wsFrameParser != null) {
                wsFrameParser.reset();  // Reset parser state
            }
            wsFrameParser = null;

            wsHandler = null;
            wsTasks = null;
            pendingWriteBuffer = null;
            closeAfterWrite = false;
            pendingClose = false;
            wsUpgradeHeaderEnd = 0;

            // ✅ Reset WebSocket frame state
            fragmentedOpcode = 0;
            currentFrameIsFin = false;
            currentFrameOpcode = 0;

            // ✅ Finally, reset to HTTP state
            state = ParseState.READING_HEADERS;
        }

        public void resetOld() {
            // Close and recreate the stream to free memory
            if (requestData != null) {
                try {
                    requestData.close();
                } catch (IOException ignore) {
                }
            }
            requestData = createByteArrayOutputStream(); // Fresh small buffer

            state = ParseState.READING_HEADERS;
            request = null;

            // Also clean up response
            if (response != null) {
                try {
                    response.close();
                } catch (IOException ignore) {
                }
                response = null;
            }
            pendingWriteBuffer = null;
        }

        public void upgradeToWebSocket(SelectionKey key) {
            this.state = ParseState.WEBSOCKET_FRAME;
            this.wsFrameParser = new WebSocket.FrameParser();
            this.wsConnection = new NioWebSocketConnection(NioHttpServer.this, key);
            this.wsTasks = new SerialExecutor(() -> workerPool);

            // Use bounded streams with the configured max frame size
            this.reassemblyBuffer = createByteArrayOutputStream();
            this.controlFrameBuffer = new BoundedByteArrayOutputStream(125, NioHttpServer.this.maxWebSocketFrameSize); // Control frames max 125 bytes

            // Recover pipelined WebSocket frames that arrived in the same TCP segment as
            // the HTTP Upgrade request. Use wsUpgradeHeaderEnd (saved on the attachment
            // before the worker thread called request.reset()) rather than
            // this.request.getHeaderEnd(), which is 0 after recycling.
            if (this.requestData != null && this.wsUpgradeHeaderEnd > 0) {
                byte[] fullData = this.requestData.toByteArray();
                if (fullData.length > this.wsUpgradeHeaderEnd) {
                    ByteBuffer leftover = ByteBuffer.wrap(fullData, this.wsUpgradeHeaderEnd, fullData.length - this.wsUpgradeHeaderEnd);
                    this.wsFrameParser.parse(leftover, this);
                }
                try {
                    this.requestData.close();
                } catch (IOException ignore) {
                }
            }

            this.request = null;
            this.response = null;

            if (this.requestData != null) {
                try {
                    this.requestData.close();
                } catch (IOException ignore) {
                }
            }
            this.requestData = null; // No longer needed
        }

        public void cleanup() {
            if (reassemblyBuffer != null) {
                try {
                    reassemblyBuffer.close();
                    reassemblyBuffer = null; // Help GC
                } catch (IOException ignore) {
                }
            }
            if (controlFrameBuffer != null) {
                try {
                    controlFrameBuffer.close();
                    controlFrameBuffer = null; // Help GC
                } catch (IOException ignore) {
                }
            }
            // Clean up requestData
            if (requestData != null) {
                try {
                    requestData.close();
                    requestData = null;
                } catch (IOException ignore) {
                }
            }
            // Clean up response
            if (response != null) {
                try {
                    response.close();
                    response = null;
                } catch (IOException ignore) {
                }
            }
        }

        // --- Implementation of FrameDataHandler ---

        @Override
        public void onFrameStart(boolean isFin, int opcode, long payloadLength) {
            synchronized (wsFrameLock) {
                this.currentFrameIsFin = isFin;
                this.currentFrameOpcode = opcode;

                // Handle control frames (opcodes 0x8-0xF)
                if (opcode > 0x7) {
                    // Control frames are handled separately and cannot be fragmented
                    controlFrameBuffer.reset();
                    return;
                }

                // Handle continuation frames (opcode 0x0)
                if (opcode == WebSocket.OPCODE_CONTINUATION) {
                    // This is a continuation frame, use the existing fragmentedOpcode
                    if (fragmentedOpcode == 0) {
                        throw new RuntimeException("Continuation frame without initial frame");
                    }

                    // Check total message size
                    if (reassemblyBuffer.size() + payloadLength > NioHttpServer.this.maxWebSocketFrameSize) {
                        wsConnection.close(WebSocket.CLOSE_TOO_LARGE, "Message too large");
                        throw new RuntimeException("WebSocket message exceeds size limit");
                    }
                } else {
                    // This is a new message (TEXT or BINARY)
                    if (fragmentedOpcode != 0) {
                        throw new RuntimeException("New frame started before previous fragmented message completed");
                    }
                    fragmentedOpcode = opcode;

                    // Validate initial frame size
                    if (payloadLength > NioHttpServer.this.maxWebSocketFrameSize) {
                        wsConnection.close(WebSocket.CLOSE_TOO_LARGE, "Message too large");
                        throw new RuntimeException("WebSocket message exceeds size limit");
                    }
                }
            }
        }

        @Override
        public void onFramePayloadData(ByteBuffer payloadChunk) {
            synchronized (wsFrameLock) {
                // Write the unmasked payload chunk to the appropriate buffer
                byte[] chunkBytes = new byte[payloadChunk.remaining()];
                payloadChunk.get(chunkBytes);
                try {
                    if (currentFrameOpcode > 0x7) {
                        // Control frame payload
                        controlFrameBuffer.write(chunkBytes);
                    } else {
                        // Data frame payload
                        reassemblyBuffer.write(chunkBytes);
                    }
                } catch (IOException e) {
                    // This is a memory stream, should not happen.
                    throw new RuntimeException(e);
                }
            }
        }

        @Override
        public void onFrameEnd() {
            // Handle control frames immediately
            if (currentFrameOpcode > 0x7) {
                byte[] controlPayload = controlFrameBuffer.toByteArray();
                WebSocket.Frame controlFrame = new WebSocket.Frame(true, currentFrameOpcode, controlPayload);
                processCompleteFrame(controlFrame);
                controlFrameBuffer.reset();
                return;
            }

            // Handle data frames (TEXT/BINARY/CONTINUATION)
            if (currentFrameIsFin) {
                // This is the final frame of a message, process the reassembled payload
                byte[] fullPayload = reassemblyBuffer.toByteArray();
                int finalOpcode = fragmentedOpcode;

                // Create a logical frame representing the complete message
                WebSocket.Frame completeFrame = new WebSocket.Frame(true, finalOpcode, fullPayload);
                processCompleteFrame(completeFrame);

                // Reset for the next message
                reassemblyBuffer.reset();
                fragmentedOpcode = 0;
            }
            // If !isFin, we just keep accumulating data in reassemblyBuffer
        }

        // Method to process a complete logical frame
        private void processCompleteFrame(final WebSocket.Frame frame) {
            switch (frame.getOpcode()) {
                case WebSocket.OPCODE_TEXT: // TEXT
                case WebSocket.OPCODE_BINARY: // BINARY
                    {
                        final WebSocket.Handler currentWsHandler = wsHandler;
                        if (currentWsHandler == null || wsTasks == null) break;
                        // Capture now: the attachment may be reset or reused before the task runs
                        final NioWebSocketConnection connection = wsConnection;
                        final String msg = (frame.getOpcode() == WebSocket.OPCODE_TEXT) ? frame.getPayloadAsText() : null;
                        final byte[] binMsg = (frame.getOpcode() == WebSocket.OPCODE_BINARY) ? frame.getPayload() : null;
                        // Serial per connection, so commands are handled in the order they were sent
                        wsTasks.execute(() -> {
                            try {
                                if (msg != null) currentWsHandler.onMessage(connection, msg);
                                else currentWsHandler.onMessage(connection, binMsg);
                            } catch (Exception e) {
                                currentWsHandler.onError(connection, e);
                            }
                        });
                    }

                    // Reset buffer if it's grown too large (prevent memory fragmentation)
                    if (reassemblyBuffer.size() > 1024 * 1024) { // 1MB threshold
                        reassemblyBuffer = createByteArrayOutputStream();
                    }

                    break;
                case WebSocket.OPCODE_CLOSE: // CLOSE
                    int closeCode = WebSocket.CLOSE_NORMAL;
                    String closeReason = "";
                    if (frame.getPayload().length >= 2) {
                        closeCode = ((frame.getPayload()[0] & 0xFF) << 8) | (frame.getPayload()[1] & 0xFF);

                        // Validate close code per RFC 6455
                        if (!isValidCloseCode(closeCode)) {
                            wsConnection.close(WebSocket.CLOSE_PROTOCOL_ERROR,
                                    "Invalid close code");
                            return;
                        }

                        if (frame.getPayload().length > 2) {
                            closeReason = new String(frame.getPayload(), 2, frame.getPayload().length - 2, StandardCharsets.UTF_8);
                        }
                    }
                    final int code = closeCode;
                    final String reason = closeReason;
                    final WebSocket.Handler currentWsHandlerForClose = wsHandler;
                    final NioWebSocketConnection closingConnection = wsConnection;
                    if (currentWsHandlerForClose != null && wsTasks != null) {
                        wsTasks.execute(() -> currentWsHandlerForClose.onClose(closingConnection, code, reason));
                    }
                    // Null out wsHandler BEFORE scheduling pendingClose so that
                    // closeConnection() (called by handleWebSocketRead after parse returns)
                    // does not fire a second onClose with WebSocket.CLOSE_ABNORMAL.
                    wsHandler = null;
                    wsConnection.forceClose();
                    // Signal handleWebSocketRead to call closeConnection() once we return
                    // from the parse chain. This ensures activeConnections is decremented
                    // and the attachment is released back to the pool.
                    pendingClose = true;
                    break;
                case WebSocket.OPCODE_PING: // PING
                    wsConnection.send(new WebSocket.Frame(true, WebSocket.OPCODE_PONG, frame.getPayload())); // Send PONG
                    break;
            }
        }

        /**
         * Validates WebSocket close code per RFC 6455 §7.4.1
         */
        private static boolean isValidCloseCode(int code) {
            // Valid ranges:
            // 1. 1000-1011 (standard codes)
            // 2. 3000-3999 (registered codes for custom use)
            // 3. 4000-4999 (available for private use)

            // Explicitly forbidden codes
            if (code == 1004 || code == 1005 || code == 1006 ||
                    code == 1015 || (code >= 1012 && code <= 1014)) {
                return false;
            }

            // Valid standard codes
            if (code >= 1000 && code <= 1011) {
                return true;
            }

            // Valid custom ranges
            if ((code >= 3000 && code <= 3999) ||
                    (code >= 4000 && code <= 4999)) {
                return true;
            }

            // Everything else is invalid
            return false;
        }
    }

    private BoundedByteArrayOutputStream createByteArrayOutputStream() {
        return new BoundedByteArrayOutputStream(
                8192, // Initial 8KB
                NioHttpServer.this.maxRequestSize // Max 2MB for requests
        );
    }

    private record ResponseTask(SelectionKey key, HttpResponse response,
                                WebSocket.Handler wsHandler) {
        ResponseTask(SelectionKey key, HttpResponse response) {
            this(key, response, null);
        }
    }

    public static class HttpResponse {
        protected int statusCode = HTTP_OK;
        protected String statusText = "OK";
        protected final Map<String, String> headers = new HashMap<>();
        protected ByteBuffer headerBuffer;
        protected ByteBuffer bodyBuffer;
        protected boolean headersSent = false;

        public HttpResponse() {
            headers.put("Connection", "keep-alive");
        }

        public HttpResponse setStatus(int code, String text) {
            this.statusCode = code;
            this.statusText = text;
            return this;
        }

        public HttpResponse addHeader(String name, String value) {
            this.headers.put(name, value);
            return this;
        }

        public HttpResponse setBody(byte[] body) {
            byte[] bodyData = (body == null) ? new byte[0] : body;
            this.addHeader("Content-Length", String.valueOf(bodyData.length));
            this.bodyBuffer = ByteBuffer.wrap(bodyData);
            return this;
        }

        protected void buildHeaders() {
            // A keep-alive client can only find the end of a bodyless response from Content-Length
            // (RFC 9112 §6.3); 1xx, 204 and 304 never carry a body.
            if (bodyBuffer == null && !headers.containsKey("Content-Length")
                    && statusCode >= 200 && statusCode != 204 && statusCode != HTTP_NOT_MODIFIED) {
                headers.put("Content-Length", "0");
            }
            StringBuilder sb = new StringBuilder();
            sb.append("HTTP/1.1 ").append(statusCode).append(" ").append(statusText).append("\r\n");
            headers.forEach((k, v) -> sb.append(k).append(": ").append(v).append("\r\n"));
            sb.append("\r\n");
            this.headerBuffer = ByteBuffer.wrap(sb.toString().getBytes(StandardCharsets.UTF_8));
        }

        public void write(SocketChannel channel) throws IOException {
            if (headerBuffer == null) buildHeaders();
            if (!headersSent) {
                channel.write(headerBuffer);
                if (!headerBuffer.hasRemaining()) headersSent = true;
            }
            if (headersSent && bodyBuffer != null) channel.write(bodyBuffer);
        }

        public boolean isFullySent() {
            return headersSent && (bodyBuffer == null || !bodyBuffer.hasRemaining());
        }

        public void close() throws IOException {
        }
    }

    public static class HttpRequest {
        private String method;
        private String path;
        private String remoteHost;
        private final Map<String, String> headers = new HashMap<>(); // Reused
        private byte[] body;
        private int headerEnd;

        public HttpRequest() {

        }

        // The parsing logic is moved here.
        public void parse(byte[] requestBytes, int headerEnd, String remoteHost) {
            this.remoteHost = remoteHost;
            this.headerEnd = headerEnd;
            String headerPart = new String(requestBytes, 0, headerEnd, StandardCharsets.US_ASCII);
            this.body = Arrays.copyOfRange(requestBytes, headerEnd, requestBytes.length);
            String[] headerLines = headerPart.split("\r\n");
            String[] requestLine = headerLines[0].split(" ");
            if (requestLine.length < 2) {
                // Malformed request line — flag with nulls so the caller can return 400.
                this.method = null;
                this.path = null;
                return;
            }
            this.method = requestLine[0];
            this.path = requestLine[1];
            for (int i = 1; i < headerLines.length; i++) {
                String line = headerLines[i];
                if (line.isEmpty()) continue;
                int separator = line.indexOf(":");
                if (separator != -1) {
                    headers.put(line.substring(0, separator).trim().toLowerCase(), line.substring(separator + 1).trim());
                }
            }
        }

        // A method to clean the object for reuse.
        public void reset() {
            headers.clear();
            method = null;
            path = null;
            remoteHost = null;
            body = null;
            headerEnd = 0;
        }

        public String getMethod() {
            return method;
        }

        public String getPath() {
            return path;
        }

        public String getRemoteHost() {
            return remoteHost;
        }

        public Map<String, String> getHeaders() {
            return Collections.unmodifiableMap(headers);
        }

        public String getHeader(String name, String defaultValue) {
            return headers.getOrDefault(name.toLowerCase(), defaultValue);
        }

        public byte[] getBody() {
            return body;
        }

        void setBody(byte[] body) {
            this.body = body;
        }

        public int getHeaderEnd() {
            return headerEnd;
        }
    }

}