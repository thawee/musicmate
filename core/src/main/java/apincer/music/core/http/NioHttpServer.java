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
import java.util.logging.Level;
import java.util.logging.Logger;

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
 * <p><b>Supports:</b> keep-alive and {@code Connection: close} (HTTP/1.0 closes unless it asks for
 * keep-alive), pipelined requests (answered in order), {@code Expect: 100-continue}, byte ranges (suffix, open-ended,
 * clamped, 416, {@code If-Range}), ETag/304, HEAD, WebSocket (RFC 6455, 1 MB message limit), limits
 * on connections, concurrent streams (least recently active stream evicted), request size, header
 * read time (30 s, so slowly trickled headers are cut off) and idle time. <b>Not supported:</b> TLS, HTTP/2, chunked request bodies.
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


    // logcat tag "NioHttpServer"; levels are set in LogHelper
    private static final Logger LOG = Logger.getLogger("NioHttpServer");
    private boolean buffersHighLogged;  // selector thread only
    private boolean streamsHighLogged;  // selector thread only

    private final AtomicInteger activeStreams = new AtomicInteger(0);
    // Totals since start, for getStats(); incremented on the selector or worker threads
    private final java.util.concurrent.atomic.AtomicLong requestCount = new java.util.concurrent.atomic.AtomicLong();
    private final java.util.concurrent.atomic.AtomicLong bytesSentCount = new java.util.concurrent.atomic.AtomicLong();
    private final java.util.concurrent.atomic.AtomicLong evictionCount = new java.util.concurrent.atomic.AtomicLong();
    private final java.util.concurrent.atomic.AtomicLong rejectedCount = new java.util.concurrent.atomic.AtomicLong();
    private final java.util.concurrent.atomic.AtomicLong timeoutCount = new java.util.concurrent.atomic.AtomicLong();
    private final java.util.concurrent.atomic.AtomicLong idleCloseCount = new java.util.concurrent.atomic.AtomicLong();
    // An eviction frees its slot on the selector thread shortly after; the new stream is admitted meanwhile
    private final StreamSlots streamSlots = new StreamSlots() {
        @Override
        public boolean acquire() {
            if (activeStreams.get() >= maxConcurrentStreams && !tryEvictOldestStream()) {
                rejectedCount.incrementAndGet();
                return false;
            }
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
    // A client trickling header bytes resets the idle timer; this deadline bounds the whole header read
    private long headerReadTimeout = 30_000;
    private static final long SWEEP_INTERVAL_MS = 1000;
    private static final byte[] CONTINUE_100 = "HTTP/1.1 100 Continue\r\n\r\n".getBytes(StandardCharsets.US_ASCII);
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

    /** Time allowed to receive a request's headers once its first byte arrives (default 30 s). */
    public void setHeaderReadTimeout(long milliseconds) {
        this.headerReadTimeout = milliseconds;
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
                                LOG.log(Level.WARNING, "Error handling connection; closing it", e);
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
                LOG.log(Level.WARNING, "Server loop error on port " + port + "; restarting in 1 s", e);
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
        LOG.info("Server on port " + port + " stopped");
    }

    private void handleIdleConnections() {
        long now = System.currentTimeMillis();
        // Every second, so timeouts are enforced close to their configured values
        if (now - lastTimeoutCheck > SWEEP_INTERVAL_MS) {
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

                    // Slowloris: headers (or a body) trickling in slower than their deadline
                    if (attachment.state == ConnectionAttachment.ParseState.READING_HEADERS && attachment.response == null
                            && attachment.requestStartTime > 0 && now - attachment.requestStartTime > headerReadTimeout) {
                        timeoutCount.incrementAndGet();
                        closeConnection(key);
                        continue;
                    }
                    if (attachment.state == ConnectionAttachment.ParseState.READING_BODY
                            && attachment.bodyReadStartTime > 0 && now - attachment.bodyReadStartTime > ConnectionAttachment.BODY_READ_TIMEOUT) {
                        timeoutCount.incrementAndGet();
                        closeConnection(key);
                        continue;
                    }

                    long idleTimeout = (attachment.state == ConnectionAttachment.ParseState.WEBSOCKET_FRAME)
                            ? keepAliveTimeout * 4  // WebSockets: 4× the HTTP idle timeout
                            : keepAliveTimeout;     // HTTP keep-alive idle timeout (default 120 s)
                    if (now - attachment.lastActivityTime > idleTimeout) {
                        LOG.fine(attachment.state == ConnectionAttachment.ParseState.WEBSOCKET_FRAME
                                ? "Closing idle WebSocket connection" : "Closing idle HTTP connection");
                        idleCloseCount.incrementAndGet();
                        closeConnection(key);
                    }
                }
            }

            // Warnings are logged when the condition starts, not on every one-second sweep
            boolean buffersHigh = totalRequestBufferSize > 100 * 1024 * 1024; // 100MB threshold
            if (buffersHigh && !buffersHighLogged) {
                LOG.warning("High memory use in request buffers: " + (totalRequestBufferSize / 1024 / 1024)
                        + " MB across " + selector.keys().size() + " connections");
            }
            buffersHighLogged = buffersHigh;

            boolean streamsHigh = activeFileStreams > maxConcurrentStreams * 0.8;
            if (streamsHigh && !streamsHighLogged) {
                LOG.info("High concurrent stream count: " + activeFileStreams + "/" + maxConcurrentStreams);
            }
            streamsHighLogged = streamsHigh;

            lastTimeoutCheck = now;
        }
    }

    private void processResponseQueue() {
        ResponseTask task;
        while ((task = responseQueue.poll()) != null) {
            SelectionKey key = task.key;
            if (key.isValid() && key.attachment() instanceof ConnectionAttachment attachment) {
                attachment.response = task.response;
                attachment.upgradeHandler = task.wsHandler; // Carry over the handler for handshake
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
            rejectedCount.incrementAndGet();
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
        if (attachment.requestStartTime == 0) {
            attachment.requestStartTime = attachment.lastActivityTime; // starts the header deadline
        }
        processBufferedHttp(key, attachment);
    }

    /**
     * Parses the HTTP bytes buffered on this connection: headers, then the body. Runs after a read,
     * and after a keep-alive response for bytes of a pipelined next request already buffered.
     */
    private void processBufferedHttp(SelectionKey key, ConnectionAttachment attachment) throws IOException {
        SocketChannel clientChannel = (SocketChannel) key.channel();
        if (attachment.state == ConnectionAttachment.ParseState.READING_HEADERS) {
            // Scan only the bytes added since the last read; copy the buffer once the headers are complete
            int headerEnd = attachment.requestData.indexOfHeaderEnd(attachment.headerScanFrom);
            if (headerEnd == -1) {
                attachment.headerScanFrom = Math.max(0, attachment.requestData.size() - 3);
                return;
            }
            // Headers are in: the deadline ends here, or it would cut off a response streaming for minutes
            attachment.requestStartTime = 0;
            byte[] requestBytes = attachment.requestData.toByteArray();
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
            attachment.request = request;
            attachment.requestLength = headerEnd + contentLength; // bytes after this belong to the next request
            if (requestBytes.length < attachment.requestLength) {
                attachment.state = ConnectionAttachment.ParseState.READING_BODY;
                attachment.bodyReadStartTime = System.currentTimeMillis();
                // RFC 9110 §10.1.1: tell a waiting client to send the body now
                if (requestBytes.length == headerEnd
                        && "100-continue".equalsIgnoreCase(request.getHeader("expect", ""))) {
                    clientChannel.write(ByteBuffer.wrap(CONTINUE_100));
                }
                return;
            }
        } else if (attachment.state != ConnectionAttachment.ParseState.READING_BODY
                || attachment.requestData.size() < attachment.requestLength) {
            return;
        }
        completeRequest(key, attachment);
    }

    /** All of the request is buffered: take its body (exactly Content-Length bytes) and dispatch it. */
    private void completeRequest(SelectionKey key, ConnectionAttachment attachment) {
        HttpRequest request = attachment.request;
        byte[] allBytes = attachment.requestData.toByteArray();
        request.setBody(Arrays.copyOfRange(allBytes, request.getHeaderEnd(), attachment.requestLength));
        attachment.bodyReadStartTime = 0;
        dispatch(key, attachment, request);
    }

    /**
     * Hands a complete request to a worker, which owns it from here; the attachment drops its
     * reference so nothing on the selector thread touches the request again.
     */
    private void dispatch(SelectionKey key, ConnectionAttachment attachment, HttpRequest request) {
        requestCount.incrementAndGet();
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
                    LOG.log(Level.WARNING, "Handler failed for " + request.getMethod() + " " + request.getPath(), e);
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

            // Close after this response if the client asked (Connection: close), or is HTTP/1.0
            // without keep-alive (HTTP/1.0 closes by default)
            String connectionHeader = request.getHeader("connection", "");
            if ("close".equalsIgnoreCase(connectionHeader)
                    || ("HTTP/1.0".equalsIgnoreCase(request.getVersion()) && !"keep-alive".equalsIgnoreCase(connectionHeader))) {
                response.addHeader("Connection", "close");
            }

            // Queue the response (either success, 404, or 500)
            responseQueue.add(new ResponseTask(key, response));
            selector.wakeup();

        } catch (Exception e) {
            LOG.log(Level.WARNING, "Error processing request", e);
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
        bytesSentCount.addAndGet(attachment.response.write(clientChannel));

        if (attachment.response.isFullySent()) {

            if (attachment.response.statusCode == HTTP_SWITCHING_PROTOCOLS && attachment.upgradeHandler != null) {
                // Case 1: the 101 is out; the connection is now a WebSocket session
                attachment.upgradeToWebSocket(key);
                // Reads start only now; a CLOSE queued while parsing pipelined frames needs writes too
                int ops = SelectionKey.OP_READ;
                if (!attachment.ws.connection.getOutgoingQueue().isEmpty()) ops |= SelectionKey.OP_WRITE;
                key.interestOps(ops);

            } else if ("close".equalsIgnoreCase(attachment.response.headers.get("Connection"))) {
                // Case 2: The response headers indicate the connection should be closed.

                closeConnection(key);
            } else {
                // Case 3: keep-alive. Reset for the next request, keeping any pipelined bytes already read
                byte[] pipelined = attachment.bytesAfterRequest();
                streamingKeys.remove(key);
                attachment.reset();
                key.interestOps(SelectionKey.OP_READ);
                if (pipelined.length > 0) {
                    attachment.requestData.write(pipelined, 0, pipelined.length);
                    attachment.requestStartTime = System.currentTimeMillis();
                    processBufferedHttp(key, attachment);
                }
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
        LOG.info("Stream limit reached; evicting the stream idle for "
                + (System.currentTimeMillis() - oldestActivityTime) + " ms");
        evictionCount.incrementAndGet();
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

                // onClose fires once per session: a peer CLOSE already reported its own code
                if (attachment.ws != null) {
                    attachment.ws.notifyClosed(WebSocket.CLOSE_ABNORMAL, "Connection closed abnormally");
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

        WebSocketSession ws = attachment.ws;
        try {
            ws.parse(attachment.readBuffer);
        } catch (RuntimeException protocolError) {
            // Malformed or oversized frame: stop reading and end with a CLOSE frame queued last
            // (1002, or the 1009 onFrameStart already queued). Writing that frame closes the
            // connection; replies queued before it (e.g. a PONG) still go out first.
            attachment.readBuffer.clear();
            ws.connection.close(WebSocket.CLOSE_PROTOCOL_ERROR, "Protocol error");
            if (!ws.connection.getOutgoingQueue().isEmpty()) {
                key.interestOps(SelectionKey.OP_WRITE);
            } else {
                closeConnection(key);
            }
            return;
        }

        attachment.readBuffer.compact();

        // A peer CLOSE is acted on here, after the parse chain has returned, not inside its callbacks
        if (ws.closeReceived) {
            closeConnection(key);
        }
    }

    private void handleWebSocketWrite(SelectionKey key) throws IOException {
        ConnectionAttachment attachment = (ConnectionAttachment) key.attachment();
        SocketChannel channel = (SocketChannel) key.channel();
        WebSocketSession ws = attachment.ws;
        Queue<WebSocket.Frame> queue = ws.connection.getOutgoingQueue();

        while (true) {
            // 1. Get a buffer to write.
            // If we have a partially written one, use it. Otherwise, poll the queue.
            if (ws.pendingWriteBuffer == null) {
                WebSocket.Frame frame = queue.poll();
                if (frame == null) {
                    // Queue is empty. Remove OP_WRITE and stop.
                    key.interestOps(key.interestOps() & ~SelectionKey.OP_WRITE);
                    return;
                }
                // Generate the buffer ONCE per frame.
                ws.pendingWriteBuffer = frame.toByteBuffer();
                ws.closeAfterWrite = frame.getOpcode() == WebSocket.OPCODE_CLOSE;
            }

            // 2. Continuous write attempt
            channel.write(ws.pendingWriteBuffer);

            // 3. If the buffer still has data, the TCP window is full.
            // Exit and wait for the next OP_WRITE signal.
            if (ws.pendingWriteBuffer.hasRemaining()) {
                return;
            }

            // 4. Frame finished. Clear the pending buffer to allow the next loop
            // to poll the next frame from the queue.
            ws.pendingWriteBuffer = null;

            // 5. Our CLOSE frame is out: end the connection here, on the selector thread
            if (ws.closeAfterWrite) {
                closeConnection(key);
                return;
            }
        }
    }

    /** Counters for diagnostics: current connections and streams, and totals since start. */
    public static final class Stats {
        public final int connections, streams;
        public final long requests, bytesSent, evictions, rejected, timeouts, idleCloses;

        Stats(int connections, int streams, long requests, long bytesSent, long evictions,
              long rejected, long timeouts, long idleCloses) {
            this.connections = connections;
            this.streams = streams;
            this.requests = requests;
            this.bytesSent = bytesSent;
            this.evictions = evictions;
            this.rejected = rejected;
            this.timeouts = timeouts;
            this.idleCloses = idleCloses;
        }
    }

    /** A snapshot of the counters; safe from any thread. */
    public Stats getStats() {
        return new Stats(activeConnections.get(), activeStreams.get(), requestCount.get(), bytesSentCount.get(),
                evictionCount.get(), rejectedCount.get(), timeoutCount.get(), idleCloseCount.get());
    }

    public HttpResponse createFileResponse(File file, HttpRequest request) throws IOException {
        return createFileResponse(file, request, -1);
    }

    /** As above, but from {@code startOffset} (a DLNA time seek's byte position) as 200; -1 for none. */
    public HttpResponse createFileResponse(File file, HttpRequest request, long startOffset) throws IOException {
        try {
            return new FileResponse(file, request, streamSlots, startOffset);
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

    /** Per-connection state, touched only by the selector thread (ADR-036). */
    private class ConnectionAttachment {
        enum ParseState {READING_HEADERS, READING_BODY, WEBSOCKET_FRAME}

        final ByteBuffer readBuffer;
        BoundedByteArrayOutputStream requestData;
        int headerScanFrom = 0; // where the next header-end scan starts
        private volatile ParseState state = ParseState.READING_HEADERS;
        HttpRequest request;
        HttpResponse response;
        volatile long lastActivityTime; // read by workers choosing an eviction victim
        private long bodyReadStartTime = 0;
        long requestStartTime = 0;   // when the current request's first byte arrived (header deadline)
        int requestLength = 0;       // header + Content-Length bytes of the current request
        public static final long BODY_READ_TIMEOUT = 120_000; // 120 seconds for slow networks

        // WebSocket: the handler travels with the 101 response; the session exists after the upgrade
        WebSocket.Handler upgradeHandler;
        WebSocketSession ws;
        // Saved at parse time: frames pipelined behind the Upgrade request start at this offset
        int wsUpgradeHeaderEnd = 0;

        ConnectionAttachment(int readBufferSize) {
            this.readBuffer = ByteBuffer.allocate(readBufferSize);
            this.requestData = createByteArrayOutputStream();
            this.lastActivityTime = System.currentTimeMillis();
        }

        private boolean isWebSocketState() {
            return state == ParseState.WEBSOCKET_FRAME;
        }

        /** Ready for the next request on a keep-alive connection; also used when the connection closes. */
        void reset() {
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
            if (ws != null) {
                ws.connection.forceClose();
                ws = null;
            }
            upgradeHandler = null;
            wsUpgradeHeaderEnd = 0;
            bodyReadStartTime = 0;
            requestStartTime = 0;
            requestLength = 0;
            state = ParseState.READING_HEADERS;
        }

        /** Bytes already read beyond the current request: the start of a pipelined next request. */
        byte[] bytesAfterRequest() {
            if (requestData == null || requestLength <= 0 || requestData.size() <= requestLength) return new byte[0];
            byte[] all = requestData.toByteArray();
            return Arrays.copyOfRange(all, requestLength, all.length);
        }

        /** The 101 has been written: switch to WebSocket framing. */
        void upgradeToWebSocket(SelectionKey key) {
            state = ParseState.WEBSOCKET_FRAME;
            ws = new WebSocketSession(upgradeHandler, new NioWebSocketConnection(NioHttpServer.this, key),
                    new SerialExecutor(() -> workerPool), maxWebSocketFrameSize, maxRequestSize);
            upgradeHandler = null;
            // onOpen first, so it runs ahead of any message, including frames pipelined below
            ws.open();
            if (requestData != null && wsUpgradeHeaderEnd > 0) {
                byte[] fullData = requestData.toByteArray();
                if (fullData.length > wsUpgradeHeaderEnd) {
                    ws.parse(ByteBuffer.wrap(fullData, wsUpgradeHeaderEnd, fullData.length - wsUpgradeHeaderEnd));
                }
            }
            request = null;
            response = null;
            requestData = null; // no longer needed
        }

        /** Releases buffers and an unfinished response when the connection closes. */
        void cleanup() {
            requestData = null;
            if (response != null) {
                try {
                    response.close();
                } catch (IOException ignore) {
                }
                response = null;
            }
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

        /** Writes what the socket accepts now; returns the bytes written. */
        public long write(SocketChannel channel) throws IOException {
            if (headerBuffer == null) buildHeaders();
            long written = 0;
            if (!headersSent) {
                written += channel.write(headerBuffer);
                if (!headerBuffer.hasRemaining()) headersSent = true;
            }
            if (headersSent && bodyBuffer != null) written += channel.write(bodyBuffer);
            return written;
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
        private String version;
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
            this.version = requestLine.length > 2 ? requestLine[2] : "HTTP/1.0";
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

        /** Protocol from the request line, e.g. "HTTP/1.1"; a request line without one is HTTP/1.0. */
        public String getVersion() {
            return version;
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