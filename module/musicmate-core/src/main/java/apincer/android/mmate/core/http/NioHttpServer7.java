package apincer.android.mmate.core.http;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.net.InetSocketAddress;
import java.net.StandardSocketOptions;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.Base64;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * A robust, memory-efficient, multi-threaded NIO HTTP Server.
 * Uses a single I/O thread (the "Reactor") and a pool of worker threads.
 *
 * REFACTORED VERSION:
 * - Implemented graceful shutdown.
 * - Improved handling of partial writes for standard HttpResponse.
 * - Added a simple state machine to ConnectionAttachment for more efficient request parsing.
 * - Reused a single read buffer in the attachment to reduce GC pressure.
 * - Added TCP socket and buffer tuning options.
 * - Added support for HTTP Range Requests in FileResponse for media streaming.
 * - Added Dynamic Content-Type (MIME type) support.
 * - Added robust error handling for invalid Range Requests (416).
 * - Added HTTP Keep-Alive connection support with idle timeout.
 * - Added WebSocket support with handshake and frame-based communication.
 * - Offloaded WebSocket onMessage handling to worker pool to prevent blocking the I/O thread.
 * - Corrected NullPointerException in WebSocket frame parsing loop.
 * - Optimized WebSocketFrameParser to use a reusable ByteBuffer instead of ByteArrayOutputStream.
 * - Corrected WebSocketFrameParser logic to handle multiple frames in a single TCP packet.
 * - Corrected state management bug in WebSocketFrameParser causing BufferOverflowException.
 * - Added graceful handling for 'Connection reset by peer' IOExceptions during streaming.
 * - Corrected buffer slicing in WebSocketFrameParser's readInto method.
 * - **FIX:** Unified WebSocket frame header parsing logic to fix final state corruption bug causing repeated BufferOverflowExceptions.
 */
public class NioHttpServer7 implements Runnable {
    private final int port;
    private final Map<String, Handler> routes = new HashMap<>();
    private final Map<String, WebSocketHandler> webSocketRoutes = new HashMap<>();
    private Handler fallbackHandler = null;
    private volatile boolean isRunning = true;
    private Selector selector;
    private ExecutorService workerPool;
    private int maxThread = 0;

    // --- Tuning Parameters ---
    private int socketBacklog = 128;
    private int clientReadBufferSize = 8192;
    private boolean tcpNoDelay = true;
    private long keepAliveTimeout = 5000; // 5 seconds
    private long lastTimeoutCheck = 0;

    // A thread-safe queue for worker threads to hand off completed responses to the I/O thread.
    private final Queue<ResponseTask> responseQueue = new ConcurrentLinkedQueue<>();

    public NioHttpServer7(int port) {
        this.port = port;
    }

    public void setMaxThread(int maxThread) { this.maxThread = maxThread; }
    public void registerHandler(String path, Handler handler) { routes.put(path, handler); }
    public void registerWebSocketHandler(String path, WebSocketHandler handler) { webSocketRoutes.put(path, handler); }
    public void registerFallbackHandler(Handler handler) { this.fallbackHandler = handler; }
    public void setSocketBacklog(int socketBacklog) { this.socketBacklog = socketBacklog; }
    public void setClientReadBufferSize(int clientReadBufferSize) { this.clientReadBufferSize = clientReadBufferSize; }
    public void setTcpNoDelay(boolean tcpNoDelay) { this.tcpNoDelay = tcpNoDelay; }
    public void setKeepAliveTimeout(long milliseconds) { this.keepAliveTimeout = milliseconds; }

    public void stop() {
        isRunning = false;
        gracefulShutdown(workerPool);
        if (selector != null) selector.wakeup();
    }

    private void gracefulShutdown(ExecutorService pool) {
        if (pool == null) return;
        pool.shutdown();
        try {
            if (!pool.awaitTermination(5, TimeUnit.SECONDS)) {
                pool.shutdownNow();
                if (!pool.awaitTermination(5, TimeUnit.SECONDS))
                    System.err.println("Worker pool did not terminate");
            }
        } catch (InterruptedException ie) {
            pool.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    @Override
    public void run() {
        if(maxThread <= 0) {
            maxThread = Runtime.getRuntime().availableProcessors();
        }
        int coreCount = Math.max(1, maxThread);
        workerPool = Executors.newFixedThreadPool(coreCount, r -> {
            Thread t = new Thread(r, "NIO-Worker");
            t.setDaemon(true);
            return t;
        });
        System.out.println("Worker pool started with " + coreCount + " threads.");

        try (ServerSocketChannel serverSocketChannel = ServerSocketChannel.open()) {
            selector = Selector.open();
            serverSocketChannel.setOption(StandardSocketOptions.SO_REUSEADDR, true);
            serverSocketChannel.bind(new InetSocketAddress(port), socketBacklog);
            serverSocketChannel.configureBlocking(false);
            serverSocketChannel.register(selector, SelectionKey.OP_ACCEPT);

            System.out.println("Multi-threaded NIO Server started on port: " + port);
            lastTimeoutCheck = System.currentTimeMillis();

            while (isRunning) {
                processResponseQueue();
                if (selector.select(1000) == 0 && isRunning) {
                    handleIdleConnections();
                    continue;
                }
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
                        String msg = e.getMessage();
                        if (msg != null && (msg.contains("Connection reset by peer") || msg.contains("Broken pipe"))) {
                            // This is a common client-side disconnect during streaming, log it quietly.
                            // System.out.println("Client disconnected: " + msg);
                        } else {
                            // A more serious I/O error occurred.
                            System.err.println("I/O error handling key: " + e.getMessage());
                        }
                        closeConnection(key);
                    } catch (Exception e) {
                        System.err.println("Error handling key: " + e.getClass().getSimpleName() + " - " + e.getMessage());
                        closeConnection(key);
                    }
                }
                handleIdleConnections();
            }
        } catch (IOException e) {
            System.err.println("Server error: " + e.getMessage());
            e.printStackTrace();
        } finally {
            if (selector != null) try { selector.close(); } catch (IOException e) { /* ignore */ }
            if (workerPool != null && !workerPool.isShutdown()) workerPool.shutdownNow();
            System.out.println("NIO Server stopped.");
        }
    }

    private void handleIdleConnections() {
        long now = System.currentTimeMillis();
        if (now - lastTimeoutCheck > keepAliveTimeout) {
            for (SelectionKey key : selector.keys()) {
                if (key.isValid() && key.attachment() instanceof ConnectionAttachment) {
                    ConnectionAttachment attachment = (ConnectionAttachment) key.attachment();
                    if (attachment.state != ConnectionAttachment.ParseState.WEBSOCKET_FRAME && now - attachment.lastActivityTime > keepAliveTimeout) {
                        System.out.println("Closing idle HTTP connection.");
                        closeConnection(key);
                    }
                }
            }
            lastTimeoutCheck = now;
        }
    }

    private void processResponseQueue() {
        ResponseTask task;
        while ((task = responseQueue.poll()) != null) {
            SelectionKey key = task.key;
            if (key.isValid() && key.attachment() instanceof ConnectionAttachment) {
                ConnectionAttachment attachment = (ConnectionAttachment) key.attachment();
                attachment.response = task.response;
                attachment.wsHandler = task.wsHandler; // Carry over the handler for handshake
                key.interestOps(SelectionKey.OP_WRITE);
            }
        }
    }

    private void handleAccept(SelectionKey key) throws IOException {
        ServerSocketChannel serverChannel = (ServerSocketChannel) key.channel();
        SocketChannel clientChannel = serverChannel.accept();
        clientChannel.configureBlocking(false);
        clientChannel.setOption(StandardSocketOptions.TCP_NODELAY, tcpNoDelay);
        clientChannel.register(selector, SelectionKey.OP_READ, new ConnectionAttachment(clientReadBufferSize));
    }

    private void handleRead(SelectionKey key) throws IOException {
        ConnectionAttachment attachment = (ConnectionAttachment) key.attachment();
        attachment.lastActivityTime = System.currentTimeMillis();

        if (attachment.state == ConnectionAttachment.ParseState.WEBSOCKET_FRAME) {
            handleWebSocketRead(key);
            return;
        }

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

        if (attachment.state == ConnectionAttachment.ParseState.READING_HEADERS) {
            byte[] requestBytes = attachment.requestData.toByteArray();
            int headerEnd = findHeaderEnd(requestBytes);
            if (headerEnd != -1) {
                HttpRequest request = new HttpRequest(requestBytes, headerEnd, ((InetSocketAddress) clientChannel.getRemoteAddress()).getAddress().getHostAddress());
                attachment.request = request;
                int contentLength = Integer.parseInt(request.getHeader("content-length", "0"));

                if (request.getBody().length >= contentLength) {
                    key.interestOps(0);
                    workerPool.submit(() -> processRequest(key, request));
                } else {
                    attachment.state = ConnectionAttachment.ParseState.READING_BODY;
                }
            }
        } else if (attachment.state == ConnectionAttachment.ParseState.READING_BODY) {
            int contentLength = Integer.parseInt(attachment.request.getHeader("content-length", "0"));
            if (attachment.requestData.size() - attachment.request.getHeaderEnd() >= contentLength) {
                byte[] fullRequestBytes = attachment.requestData.toByteArray();
                HttpRequest finalRequest = new HttpRequest(fullRequestBytes, attachment.request.getHeaderEnd(), ((InetSocketAddress) clientChannel.getRemoteAddress()).getAddress().getHostAddress());
                key.interestOps(0);
                workerPool.submit(() -> processRequest(key, finalRequest));
            }
        }
    }

    private void processRequest(SelectionKey key, HttpRequest request) {
        try {
            // --- WebSocket Handshake Logic ---
            WebSocketHandler wsHandler = webSocketRoutes.get(request.getPath());
            if (wsHandler != null && "websocket".equalsIgnoreCase(request.getHeader("upgrade", ""))) {
                HttpResponse response = WebSocketHandshake.createHandshakeResponse(request);
                responseQueue.add(new ResponseTask(key, response, wsHandler));
                selector.wakeup();
                return;
            }

            Handler handler = routes.get(request.getPath());
            HttpResponse response = (handler != null) ? handler.handle(request) :
                    (fallbackHandler != null) ? fallbackHandler.handle(request) :
                            new HttpResponse().setStatus(404, "Not Found").setBody("<h1>404 Not Found</h1>".getBytes());

            if ("close".equalsIgnoreCase(request.getHeader("connection", "keep-alive"))) {
                response.addHeader("Connection", "close");
            }
            responseQueue.add(new ResponseTask(key, response));
            selector.wakeup();
        } catch (Exception e) {
            e.printStackTrace();
            HttpResponse errorResponse = new HttpResponse().setStatus(500, "Internal Server Error").addHeader("Connection", "close");
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
            if (attachment.response.statusCode == 101 && attachment.wsHandler != null) {
                // HTTP part is done, now upgrade the connection to WebSocket
                attachment.upgradeToWebSocket(key);
                workerPool.submit(() -> {
                    try {
                        attachment.wsHandler.onOpen(attachment.wsConnection);
                    } catch (Exception e) {
                        attachment.wsHandler.onError(attachment.wsConnection, e);
                    }
                });
                key.interestOps(SelectionKey.OP_READ);
            } else if ("close".equalsIgnoreCase(attachment.response.headers.get("Connection"))) {
                closeConnection(key);
            } else {
                attachment.reset();
                key.interestOps(SelectionKey.OP_READ);
            }
        }
    }

    private void closeConnection(SelectionKey key) {
        try {
            if (key.attachment() instanceof ConnectionAttachment) {
                ConnectionAttachment attachment = (ConnectionAttachment) key.attachment();
                if (attachment.state == ConnectionAttachment.ParseState.WEBSOCKET_FRAME && attachment.wsHandler != null) {
                    workerPool.submit(() -> {
                        try {
                            attachment.wsHandler.onClose(attachment.wsConnection, 1006, "Connection closed abnormally");
                        } catch (Exception e) {
                            // Log error during close if necessary
                        }
                    });
                }
                if (attachment.response != null) {
                    attachment.response.close();
                }
            }
            if (key.channel() != null) key.channel().close();
        } catch (IOException e) { /* ignore */ }
        finally {
            key.cancel();
        }
    }

    private int findHeaderEnd(byte[] data) {
        for (int i = 0; i < data.length - 3; i++) {
            if (data[i] == '\r' && data[i+1] == '\n' && data[i+2] == '\r' && data[i+3] == '\n') {
                return i + 4;
            }
        }
        return -1;
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
        attachment.wsFrameParser.parse(attachment.readBuffer, attachment);

        attachment.readBuffer.compact();
    }

    private void handleWebSocketWrite(SelectionKey key) throws IOException {
        ConnectionAttachment attachment = (ConnectionAttachment) key.attachment();
        SocketChannel channel = (SocketChannel) key.channel();
        Queue<WebSocketFrame> queue = attachment.wsConnection.getOutgoingQueue();

        WebSocketFrame frame;
        while ((frame = queue.peek()) != null) {
            ByteBuffer buffer = frame.toByteBuffer();
            channel.write(buffer);
            if (buffer.hasRemaining()) {
                // Didn't write the whole frame, come back later.
                return;
            }
            queue.poll(); // Wrote the whole frame, remove it.
        }

        // We wrote everything, stop listening for write events.
        if (queue.isEmpty()) {
            key.interestOps(SelectionKey.OP_READ);
        }
    }

    // --- INNER CLASSES AND INTERFACES ---
    @FunctionalInterface
    public interface Handler { HttpResponse handle(HttpRequest request); }

    public interface WebSocketHandler {
        void onOpen(WebSocketConnection connection);
        void onMessage(WebSocketConnection connection, String message);
        void onMessage(WebSocketConnection connection, byte[] message);
        void onClose(WebSocketConnection connection, int code, String reason);
        void onError(WebSocketConnection connection, Exception ex);
    }

    private class ConnectionAttachment implements WebSocketFrameParser.FrameDataHandler { // MODIFIED: implements handler
        enum ParseState { READING_HEADERS, READING_BODY, WEBSOCKET_FRAME }
        final ByteBuffer readBuffer;
        ByteArrayOutputStream requestData = new ByteArrayOutputStream();
        ParseState state = ParseState.READING_HEADERS;
        HttpRequest request;
        HttpResponse response;
        long lastActivityTime;

        // WebSocket specific fields
        WebSocketHandler wsHandler;
        WebSocketConnection wsConnection;
        WebSocketFrameParser wsFrameParser;

        // NEW: Fields for message reassembly
        private ByteArrayOutputStream reassemblyBuffer;
        private int fragmentedOpcode = 0;
        private boolean currentFrameIsFin;
        private int currentFrameOpcode;

        public ConnectionAttachment(int readBufferSize) {
            this.readBuffer = ByteBuffer.allocate(readBufferSize);
            this.lastActivityTime = System.currentTimeMillis();
        }

        public void reset() {
            requestData.reset();
            state = ParseState.READING_HEADERS;
            request = null;
            response = null;
        }

        public void upgradeToWebSocket(SelectionKey key) {
            this.state = ParseState.WEBSOCKET_FRAME;
            this.wsFrameParser = new WebSocketFrameParser();
            this.wsConnection = new WebSocketConnection(key);
            this.reassemblyBuffer = new ByteArrayOutputStream(); // NEW
            this.request = null;
            this.response = null;

            if (this.requestData != null) {
                try { this.requestData.close(); } catch (IOException e) { }
            }
            this.requestData = null; // No longer needed
        }

        // --- Implementation of FrameDataHandler ---

        @Override
        public void onFrameStart(boolean isFin, int opcode, long payloadLength) {
            this.currentFrameIsFin = isFin;
            this.currentFrameOpcode = opcode;
            if (opcode > 0x7) { // This is a control frame
                // Control frames cannot be fragmented and must be handled immediately
                return;
            }
            if (fragmentedOpcode == 0) { // Start of a new message
                fragmentedOpcode = opcode;
            }
        }

        @Override
        public void onFramePayloadData(ByteBuffer payloadChunk) {
            // Write the unmasked payload chunk to the reassembly buffer
            byte[] chunkBytes = new byte[payloadChunk.remaining()];
            payloadChunk.get(chunkBytes);
            try {
                reassemblyBuffer.write(chunkBytes);
            } catch (IOException e) {
                // This is a memory stream, should not happen.
                throw new RuntimeException(e);
            }
        }

        @Override
        public void onFrameEnd() {
            if (currentFrameIsFin) {
                // This is the final frame of a message, process the reassembled payload
                byte[] fullPayload = reassemblyBuffer.toByteArray();
                int finalOpcode = (fragmentedOpcode != 0) ? fragmentedOpcode : currentFrameOpcode;

                // Create a logical frame representing the complete message
                WebSocketFrame completeFrame = new WebSocketFrame(true, finalOpcode, fullPayload);
                processCompleteFrame(completeFrame);

                // Reset for the next message
                reassemblyBuffer.reset();
                fragmentedOpcode = 0;
            }
            // If !isFin, we just keep accumulating data in reassemblyBuffer
        }

        // NEW: Method to process a complete logical frame
        private void processCompleteFrame(final WebSocketFrame frame) {
            switch (frame.getOpcode()) {
                case 0x1: // TEXT
                case 0x2: // BINARY
                    workerPool.submit(() -> {
                        try {
                            if (frame.getOpcode() == 0x1) {
                                wsHandler.onMessage(wsConnection, frame.getPayloadAsText());
                            } else {
                                wsHandler.onMessage(wsConnection, frame.getPayload());
                            }
                        } catch (Exception e) {
                            wsHandler.onError(wsConnection, e);
                        }
                    });
                    break;
                case 0x8: // CLOSE
                    wsConnection.send(new WebSocketFrame(true, 0x8, new byte[0])); // Echo close frame
                    closeConnection(wsConnection.key);
                    break;
                case 0x9: // PING
                    wsConnection.send(new WebSocketFrame(true, 0xA, frame.getPayload())); // Send PONG
                    break;
            }
        }
    }

    private static class ResponseTask {
        final SelectionKey key;
        final HttpResponse response;
        final WebSocketHandler wsHandler;

        ResponseTask(SelectionKey key, HttpResponse response) { this(key, response, null); }
        ResponseTask(SelectionKey key, HttpResponse response, WebSocketHandler wsHandler) {
            this.key = key; this.response = response; this.wsHandler = wsHandler;
        }
    }

    public static class HttpResponse {
        protected int statusCode = 200;
        protected String statusText = "OK";
        protected final Map<String, String> headers = new HashMap<>();
        protected ByteBuffer headerBuffer;
        protected ByteBuffer bodyBuffer;
        protected boolean headersSent = false;

        public HttpResponse() { headers.put("Connection", "keep-alive"); }
        public HttpResponse setStatus(int code, String text) { this.statusCode = code; this.statusText = text; return this; }
        public HttpResponse addHeader(String name, String value) { this.headers.put(name, value); return this; }
        public HttpResponse setBody(byte[] body) {
            byte[] bodyData = (body == null) ? new byte[0] : body;
            this.addHeader("Content-Length", String.valueOf(bodyData.length));
            this.bodyBuffer = ByteBuffer.wrap(bodyData);
            return this;
        }
        protected void buildHeaders() {
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
        public boolean isFullySent() { return headersSent && (bodyBuffer == null || !bodyBuffer.hasRemaining()); }
        public void close() throws IOException {}
    }

    public static class FileResponse extends HttpResponse {
        // ... (FileResponse implementation remains the same)
        private final FileChannel fileChannel;
        private final long fileSize;
        private long bytesSent = 0;
        private final long rangeStart;
        private final long rangeEnd;
        private final long rangeLength;

        public FileResponse(File file, HttpRequest request) throws IOException {
            super();
            this.addHeader("Content-Type", MimeTypeUtil.getMimeType(file.getName()));
            RandomAccessFile raf = new RandomAccessFile(file, "r");
            this.fileChannel = raf.getChannel();
            this.fileSize = fileChannel.size();
            addHeader("Accept-Ranges", "bytes");
            String rangeHeader = request.getHeader("range", null);

            long tempStart = 0, tempEnd = this.fileSize - 1;

            if (rangeHeader != null && rangeHeader.startsWith("bytes=")) {
                if (parseRangeHeader(rangeHeader)) {
                    if (this.parsedStart > this.fileSize - 1) {
                        setStatus(416, "Range Not Satisfiable");
                        addHeader("Content-Range", "bytes */" + this.fileSize);
                        tempStart = 0;
                        tempEnd = -1;
                    } else {
                        setStatus(206, "Partial Content");
                        tempStart = this.parsedStart;
                        tempEnd = Math.min(this.parsedEnd, this.fileSize - 1);
                    }
                }
            }

            this.rangeStart = tempStart;
            this.rangeEnd = tempEnd;
            this.rangeLength = (this.rangeEnd < this.rangeStart) ? 0 : (this.rangeEnd - this.rangeStart) + 1;

            addHeader("Content-Length", String.valueOf(this.rangeLength));
            if (this.statusCode == 206) {
                addHeader("Content-Range", String.format("bytes %d-%d/%d", this.rangeStart, this.rangeEnd, this.fileSize));
            }
        }

        private long parsedStart, parsedEnd;

        private boolean parseRangeHeader(String rangeHeader) {
            try {
                String rangeValue = rangeHeader.substring(6);
                parsedStart = -1; parsedEnd = -1;

                if (rangeValue.startsWith("-")) {
                    long lastBytes = Long.parseLong(rangeValue.substring(1));
                    parsedStart = Math.max(0, this.fileSize - lastBytes);
                    parsedEnd = this.fileSize - 1;
                } else {
                    String[] ranges = rangeValue.split("-");
                    parsedStart = Long.parseLong(ranges[0]);
                    parsedEnd = (ranges.length > 1 && !ranges[1].isEmpty()) ? Long.parseLong(ranges[1]) : this.fileSize - 1;
                }
                return parsedStart >= 0 && parsedStart <= parsedEnd;
            } catch (NumberFormatException | ArrayIndexOutOfBoundsException e) {
                return false;
            }
        }
        @Override
        public void write(SocketChannel channel) throws IOException {
            if (headerBuffer == null) buildHeaders();
            if (!headersSent) {
                channel.write(headerBuffer);
                if (!headerBuffer.hasRemaining()) headersSent = true;
            }
            if(headersSent && bytesSent < rangeLength) {
                long written = fileChannel.transferTo(this.rangeStart + bytesSent, this.rangeLength - bytesSent, channel);
                if (written > 0) bytesSent += written;
            }
        }
        @Override
        public boolean isFullySent() { return headersSent && bytesSent >= rangeLength; }
        @Override
        public void close() throws IOException { if (fileChannel != null && fileChannel.isOpen()) fileChannel.close(); }
    }

    public static class MimeTypeUtil {
        // ... (MimeTypeUtil implementation remains the same)
        private static final Map<String, String> MIME_MAP = new HashMap<>();
        static {
            MIME_MAP.put("mp3", "audio/mpeg"); MIME_MAP.put("flac", "audio/flac");
            MIME_MAP.put("aac", "audio/aac"); MIME_MAP.put("m4a", "audio/mp4");
            MIME_MAP.put("ogg", "audio/ogg"); MIME_MAP.put("oga", "audio/ogg");
            MIME_MAP.put("wav", "audio/wav"); MIME_MAP.put("opus", "audio/opus");
            MIME_MAP.put("jpg", "image/jpeg"); MIME_MAP.put("jpeg", "image/jpeg");
            MIME_MAP.put("png", "image/png"); MIME_MAP.put("txt", "text/plain");
            MIME_MAP.put("html", "text/html");
        }
        public static String getMimeType(String fileName) {
            int lastDot = fileName.lastIndexOf('.');
            if (lastDot != -1 && lastDot < fileName.length() - 1) {
                return MIME_MAP.getOrDefault(fileName.substring(lastDot + 1).toLowerCase(), "application/octet-stream");
            }
            return "application/octet-stream";
        }
    }

    public static class HttpRequest {
        // ... (HttpRequest implementation remains the same)
        private final String method;
        private final String path;
        private final Map<String, String> headers;
        private final byte[] body;
        private final int headerEnd;

        public HttpRequest(byte[] requestBytes, int headerEnd, String remoteHost) {
            this.headers = new HashMap<>(); this.headerEnd = headerEnd;
            String headerPart = new String(requestBytes, 0, headerEnd, StandardCharsets.US_ASCII);
            this.body = Arrays.copyOfRange(requestBytes, headerEnd, requestBytes.length);
            String[] headerLines = headerPart.split("\r\n");
            String[] requestLine = headerLines[0].split(" ");
            this.method = requestLine[0]; this.path = requestLine[1];
            for (int i = 1; i < headerLines.length; i++) {
                String line = headerLines[i]; if (line.isEmpty()) continue;
                int separator = line.indexOf(":");
                if (separator != -1) {
                    headers.put(line.substring(0, separator).trim().toLowerCase(), line.substring(separator + 1).trim());
                }
            }
            if (!headers.containsKey("host")) headers.put("host", remoteHost);
        }
        public String getMethod() { return method; }
        public String getPath() { return path; }
        public Map<String, String> getHeaders() { return Collections.unmodifiableMap(headers); }
        public String getHeader(String name, String defaultValue) { return headers.getOrDefault(name.toLowerCase(), defaultValue); }
        public byte[] getBody() { return body; }
        public int getHeaderEnd() { return headerEnd; }
    }

    // --- NEW WebSocket Helper Classes ---

    public static class WebSocketConnection {
        private final SelectionKey key;
        private final Queue<WebSocketFrame> outgoingQueue = new ConcurrentLinkedQueue<>();

        WebSocketConnection(SelectionKey key) { this.key = key; }

        public void send(String message) {
            send(new WebSocketFrame(true, 0x1, message.getBytes(StandardCharsets.UTF_8)));
        }

        public void send(byte[] message) {
            send(new WebSocketFrame(true, 0x2, message));
        }

        public void send(WebSocketFrame frame) {
            outgoingQueue.add(frame);
            key.interestOps(key.interestOps() | SelectionKey.OP_WRITE);
            key.selector().wakeup();
        }

        Queue<WebSocketFrame> getOutgoingQueue() { return outgoingQueue; }
    }

    private static class WebSocketHandshake {
        private static final String WEBSOCKET_GUID = "258EAFA5-E914-47DA-95CA-C5AB0DC85B11";

        public static HttpResponse createHandshakeResponse(HttpRequest request) throws NoSuchAlgorithmException {
            String clientKey = request.getHeader("sec-websocket-key", null);
            if (clientKey == null) {
                return new HttpResponse().setStatus(400, "Bad Request").setBody("Missing Sec-WebSocket-Key header".getBytes());
            }
            String acceptKey = Base64.getEncoder().encodeToString(
                    MessageDigest.getInstance("SHA-1").digest((clientKey + WEBSOCKET_GUID).getBytes(StandardCharsets.UTF_8))
            );
            return new HttpResponse()
                    .setStatus(101, "Switching Protocols")
                    .addHeader("Upgrade", "websocket")
                    .addHeader("Connection", "Upgrade")
                    .addHeader("Sec-WebSocket-Accept", acceptKey);
        }
    }

    private static class WebSocketFrame {
        private final boolean isFin;
        private final int opcode;
        private final byte[] payload;

        WebSocketFrame(boolean isFin, int opcode, byte[] payload) {
            this.isFin = isFin; this.opcode = opcode; this.payload = payload;
        }

        public int getOpcode() { return opcode; }
        public byte[] getPayload() { return payload; }
        public String getPayloadAsText() { return new String(payload, StandardCharsets.UTF_8); }

        public ByteBuffer toByteBuffer() {
            int payloadLength = payload.length;
            int headerSize = 2;
            if (payloadLength > 65535) headerSize += 8;
            else if (payloadLength > 125) headerSize += 2;
            ByteBuffer buffer = ByteBuffer.allocate(headerSize + payloadLength);

            byte b0 = 0;
            if (isFin) b0 |= (byte) 0x80;
            b0 |= (byte) (opcode & 0x0F);
            buffer.put(b0);

            byte b1 = 0; // Mask bit is 0 for server-to-client
            if (payloadLength <= 125) {
                b1 |= (byte) payloadLength;
                buffer.put(b1);
            } else if (payloadLength <= 65535) {
                b1 |= 126;
                buffer.put(b1);
                buffer.putShort((short) payloadLength);
            } else {
                b1 |= 127;
                buffer.put(b1);
                buffer.putLong(payloadLength);
            }

            buffer.put(payload);
            buffer.flip();
            return buffer;
        }
    }

    private static class WebSocketFrameParser {
        // NEW: Callback interface for streaming frame data
        public interface FrameDataHandler {
            void onFrameStart(boolean isFin, int opcode, long payloadLength);
            void onFramePayloadData(ByteBuffer payloadChunk);
            void onFrameEnd();
        }

        private enum State { READING_HEADER, READING_PAYLOAD_LEN_16, READING_PAYLOAD_LEN_64, READING_MASK, READING_PAYLOAD }
        private State state = State.READING_HEADER;
        private final byte[] smallHeaderBuffer = new byte[2];
        private int bytesRead = 0;
        private long payloadLength;
        private long payloadBytesRemaining; // MODIFIED: Track remaining bytes instead of using a buffer position
        private byte[] maskKey;
        private byte[] lengthBytes;

        // MODIFIED: 'parse' now takes a handler and doesn't return a frame
        public void parse(ByteBuffer buffer, FrameDataHandler handler) {
            while (buffer.hasRemaining()) {
                switch (state) {
                    case READING_HEADER:
                        if (readBytes(buffer, smallHeaderBuffer, 2)) {
                            processHeader(handler);
                        }
                        break;
                    case READING_PAYLOAD_LEN_16:
                        if (lengthBytes == null) lengthBytes = new byte[2];
                        if (readBytes(buffer, lengthBytes, 2)) {
                            payloadLength = ByteBuffer.wrap(lengthBytes).getShort() & 0xFFFF;
                            payloadBytesRemaining = payloadLength;
                            bytesRead = 0;
                            state = State.READING_MASK;
                            lengthBytes = null;
                            handler.onFrameStart((smallHeaderBuffer[0] & 0x80) != 0, smallHeaderBuffer[0] & 0x0F, payloadLength);
                        }
                        break;
                    case READING_PAYLOAD_LEN_64:
                        if (lengthBytes == null) lengthBytes = new byte[8];
                        if (readBytes(buffer, lengthBytes, 8)) {
                            payloadLength = ByteBuffer.wrap(lengthBytes).getLong();
                            payloadBytesRemaining = payloadLength;
                            bytesRead = 0;
                            state = State.READING_MASK;
                            lengthBytes = null;
                            handler.onFrameStart((smallHeaderBuffer[0] & 0x80) != 0, smallHeaderBuffer[0] & 0x0F, payloadLength);
                        }
                        break;
                    case READING_MASK:
                        if (maskKey == null) maskKey = new byte[4];
                        if (readBytes(buffer, maskKey, 4)) {
                            state = State.READING_PAYLOAD;
                            bytesRead = 0;
                            if (payloadLength == 0) { // Handle empty frames
                                handler.onFrameEnd();
                                reset();
                            }
                        }
                        break;
                    case READING_PAYLOAD:
                        long toRead = Math.min(buffer.remaining(), payloadBytesRemaining);
                        if (toRead == 0) break; // Nothing to read in this buffer iteration

                        int originalLimit = buffer.limit();
                        buffer.limit(buffer.position() + (int)toRead);

                        // Unmask and pass the chunk to the handler
                        unmaskAndProcessChunk(buffer, handler);

                        buffer.limit(originalLimit);
                        payloadBytesRemaining -= toRead;

                        if (payloadBytesRemaining == 0) {
                            handler.onFrameEnd();
                            reset();
                        }
                        break;
                }
            }
        }

        private void unmaskAndProcessChunk(ByteBuffer chunk, FrameDataHandler handler) {
            // Unmask the data in place or create a temporary buffer for the chunk
            for (int i = 0; i < chunk.remaining(); i++) {
                int payloadIndex = (int)((payloadLength - payloadBytesRemaining) + i);
                chunk.put(chunk.position() + i, (byte)(chunk.get(chunk.position() + i) ^ maskKey[payloadIndex % 4]));
            }
            handler.onFramePayloadData(chunk);
        }

        private void processHeader(FrameDataHandler handler) {
            payloadLength = smallHeaderBuffer[1] & 0x7F;
            if ((smallHeaderBuffer[1] & 0x80) == 0) throw new RuntimeException("Client frame must be masked");

            if (payloadLength <= 125) {
                payloadBytesRemaining = payloadLength;
                state = State.READING_MASK;
                // We have all header info, so we can call onFrameStart now
                handler.onFrameStart((smallHeaderBuffer[0] & 0x80) != 0, smallHeaderBuffer[0] & 0x0F, payloadLength);
            } else if (payloadLength == 126) {
                state = State.READING_PAYLOAD_LEN_16;
            } else {
                state = State.READING_PAYLOAD_LEN_64;
            }
            bytesRead = 0;
        }

        private void reset() {
            state = State.READING_HEADER;
            bytesRead = 0;
            maskKey = null;
            lengthBytes = null;
            payloadLength = 0;
            payloadBytesRemaining = 0;
        }

        // readBytes method remains the same
        private boolean readBytes(ByteBuffer buffer, byte[] dest, int length) {
            int needed = length - bytesRead;
            int canRead = Math.min(buffer.remaining(), needed);
            buffer.get(dest, bytesRead, canRead);
            bytesRead += canRead;
            return bytesRead == length;
        }
    }
}

