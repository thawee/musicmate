package apincer.android.jupnp.server.undertow;

import static apincer.music.core.utils.StringUtils.isEmpty;

import android.content.Context;
import android.util.Log;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.undertow.Handlers;
import io.undertow.Undertow;
import io.undertow.UndertowOptions;
import io.undertow.server.DefaultByteBufferPool;
import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.server.handlers.PathHandler;
import io.undertow.server.handlers.resource.PathResourceManager;
import io.undertow.server.handlers.resource.ResourceHandler;
import io.undertow.util.Headers;
import io.undertow.util.HttpString;
import io.undertow.websockets.WebSocketConnectionCallback;
import io.undertow.websockets.WebSocketProtocolHandshakeHandler;
import io.undertow.websockets.core.AbstractReceiveListener;
import io.undertow.websockets.core.BufferedTextMessage;
import io.undertow.websockets.core.WebSocketChannel;
import io.undertow.websockets.core.WebSockets;
import io.undertow.websockets.spi.WebSocketHttpExchange;

import org.jupnp.transport.spi.InitializationException;
import org.xnio.IoUtils;
import org.xnio.Options;

import java.io.File;
import java.net.InetAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Paths;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArraySet;

import apincer.music.core.database.MusicTag;
import apincer.music.core.repository.FileRepository;
import apincer.music.core.repository.TagRepository;
import apincer.music.core.server.BaseServer;
import apincer.music.core.server.spi.WebServer;
import apincer.music.core.utils.MimeTypeUtils;
import apincer.music.server.jupnp.transport.DLNAHeaderHelper;

public class WebServerImpl extends BaseServer implements WebServer {

    private static final String TAG = "WebServerImpl";

    private final Object serverLock = new Object();
    private Undertow server;
    private Thread serverInitThread;
    private final ResourceHandler resourceHandler;
    private final CopyOnWriteArraySet<WebSocketChannel> activeWebsocketSessions = new CopyOnWriteArraySet<>();

    public WebServerImpl(Context context, FileRepository fileRepos, TagRepository tagRepos) {
        super(context, fileRepos, tagRepos);
        addLibInfo("Undertow", "");

        // Undertow's resource handler to serve actual files.
        // Paths.get("/") acts as the root file system for Android
        // need to  set followLinks on resourceManager
        String[] safePaths = new String[]{"/storage","/data"};
        // Define the specific entry points for your music
        // Use a larger attribute cache for these paths to avoid repeated disk I/O
        // This prevents the CPU from waking up just to check "lastModified" on every chunk request.
        int transferMinSize = 1024 * 100; // 100kB is plenty for file metadata
        resourceHandler = new ResourceHandler(new PathResourceManager(Paths.get("/"), transferMinSize, true, true, safePaths))
                .setDirectoryListingEnabled(false);
    }

    @Override
    public void restartServer(InetAddress bindAddress) {
        synchronized (serverLock) {
            Log.d(TAG, "Restarting Undertow Server...");

            // 1. Full Stop
            stopServer();

            // 2. Small grace period for OS to release the socket
            try {
                Thread.sleep(200);
            } catch (InterruptedException ignored) {}

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
        serverInitThread = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    Log.v(TAG, "Running Undertow Content Server: " + bindAddress.getHostAddress() + ":" + WEB_SERVER_PORT);

                    final WebSocketHandler wsHandler = new WebSocketHandler(activeWebsocketSessions);

                    HttpHandler mainHandler = new HttpHandler() {
                        @Override
                        public void handleRequest(HttpServerExchange exchange) throws Exception {
                            if (exchange.isInIoThread()) {
                                exchange.dispatch(this);
                                return;
                            }

                            String requestMethod = exchange.getRequestMethod().toString();
                            if (!requestMethod.equals("GET") && !requestMethod.equals("HEAD")) {
                                exchange.setStatusCode(405);
                                exchange.getResponseSender().send("Method not supported");
                                return;
                            }

                            String rawPath = exchange.getRequestPath();
                            String requestUri = rawPath;
                            try {
                                requestUri = URLDecoder.decode(rawPath, StandardCharsets.UTF_8);
                            } catch (Exception ignore) {}

                            if (isEmpty(requestUri) || requestUri.equals("/")) {
                                requestUri = "/index.html";
                            }

                            String userAgent = exchange.getRequestHeaders().getFirst(Headers.USER_AGENT);
                            String remoteAddr = exchange.getSourceAddress() != null ? exchange.getSourceAddress().getAddress().getHostAddress() : "";

                            if (requestUri.startsWith(CONTEXT_PATH_COVERART)) {
                                ContentHolder contentHolder = lookupAlbumArt(requestUri);
                                serveContent(exchange, contentHolder);
                            } else if (requestUri.startsWith(CONTEXT_PATH_MUSIC)) {
                                ContentHolder contentHolder = lookupContent(requestUri, userAgent, remoteAddr);
                                serveContent(exchange, contentHolder);
                            } else {
                                File filePath = getWebResource(requestUri);
                                if (filePath != null && filePath.exists() && filePath.canRead()) {
                                    String mimeType = MimeTypeUtils.getMimeTypeFromPath(filePath.getAbsolutePath());
                                    ContentHolder contentHolder = new ContentHolder(mimeType != null ? mimeType : "application/octet-stream", requestUri, filePath.getAbsolutePath(), null);
                                    serveContent(exchange, contentHolder);
                                } else {
                                    exchange.setStatusCode(404);
                                    exchange.getResponseSender().send("<html><body><h1>File not found</h1></body></html>");
                                }
                            }
                        }
                    };

                    PathHandler pathHandler = Handlers.path(mainHandler)
                            .addPrefixPath(CONTEXT_PATH_WEBSOCKET, new WebSocketProtocolHandshakeHandler(new WebSocketConnectionCallback() {
                                @Override
                                public void onConnect(WebSocketHttpExchange exchange, WebSocketChannel channel) {
                                    wsHandler.onConnect(channel);
                                    channel.getReceiveSetter().set(new AbstractReceiveListener() {
                                        @Override
                                        protected void onFullTextMessage(WebSocketChannel channel, BufferedTextMessage message) {
                                            wsHandler.onMessage(channel, message.getData());
                                        }

                                        @Override
                                        protected void onClose(WebSocketChannel webSocketChannel, io.undertow.websockets.core.StreamSourceFrameChannel sourceChannel) throws java.io.IOException {
                                            wsHandler.onClose(webSocketChannel);
                                            super.onClose(webSocketChannel, sourceChannel);
                                        }
                                    });
                                    channel.resumeReceives();
                                }
                            }));

                    // 128KB or 256KB is the "sweet spot" for DSD and 192kHz PCM
                    int bufferSize = 1024 * 256;

                    server = Undertow.builder()
                            .addHttpListener(WEB_SERVER_PORT, bindAddress.getHostAddress())
                            //.setIoThreads(2)
                            // Stick to 1 IO thread; Android's kernel handles the NIO multiplexing well enough
                            .setIoThreads(1)
                            .setWorkerThreads(4)
                            //.setByteBufferPool(new DefaultByteBufferPool(false, 1024 * 16))
                            // Use Direct Buffers for Zero-Copy (less CPU overhead)
                            .setByteBufferPool(new DefaultByteBufferPool(true, bufferSize))
                            // Critical: Prevent the server from keeping connections open indefinitely
                            .setServerOption(UndertowOptions.IDLE_TIMEOUT, 30000) // 30 seconds
                            .setServerOption(UndertowOptions.NO_REQUEST_TIMEOUT, 10000) // 10 seconds
                            // Correct way to set TCP_NODELAY in Undertow
                            .setSocketOption(Options.TCP_NODELAY, true)
                            // Hints to the Android Linux kernel to prioritize these packets for Low Delay
                            // 0x10 is Low Delay (Good for control)
                            // 0x08 is High Throughput (Better for massive Hi-Res/DSD streams)
                            // Use 0x10|0x08 for a balance (0x18)
                            //.setSocketOption(Options.IP_TRAFFIC_CLASS, 0x10)
                            .setSocketOption(Options.IP_TRAFFIC_CLASS, 0x18)
                            // Increase the system-level send buffer to handle bursts
                            .setSocketOption(Options.SEND_BUFFER, 1024 * 1024)
                            .setHandler(pathHandler)
                            .build();

                    server.start();
                } catch (Exception ex) {
                    throw new InitializationException("Could not initialize " + getClass().getSimpleName() + ": " + ex, ex);
                }
            }
        });
        serverInitThread.setName("undertow-webserver-runner");
        serverInitThread.start();
    }

    private void serveContent(HttpServerExchange exchange, ContentHolder contentHolder) throws Exception {
        File file = new File(contentHolder.getFilePath());
        if (file.exists()) {
            if(contentHolder.isMedia()) {
                // 1. Bit-Perfect & Live Streaming Headers
                // 'no-transform' is the most critical for audiophiles: it forbids network compression.
                exchange.getResponseHeaders().put(Headers.CACHE_CONTROL, "no-store, no-transform, max-age=0");
                exchange.getResponseHeaders().put(Headers.CONTENT_TYPE, contentHolder.getContentType());
                exchange.getResponseHeaders().put(Headers.CONTENT_LENGTH, file.length());
                exchange.getResponseHeaders().put(Headers.ACCEPT_RANGES, "bytes");

                // 2. High-End Renderer Hints
                exchange.getResponseHeaders().put(HttpString.tryFromString("X-Audio-Bit-Perfect"), "true");

                // Fix: Use the file extension or type from your MusicTag if available
                String codec = contentHolder.getContentType().replace("audio/", "").toUpperCase();
                exchange.getResponseHeaders().put(HttpString.tryFromString("X-Audio-Codec"), codec);

                // 3. DLNA Specifics
                if (contentHolder.getDlnaContentFeatures() != null) {
                    exchange.getResponseHeaders().put(new HttpString("transferMode.dlna.org"), "Streaming");
                    exchange.getResponseHeaders().put(new HttpString("contentFeatures.dlna.org"), contentHolder.getDlnaContentFeatures());
                }
            }else if (contentHolder.isImage()) {
                // Optimize for Library Scrolling: 1 hour cache is good,
                // but for a camper setup with slow Wi-Fi, 24h (86400) is even better.
                exchange.getResponseHeaders().put(Headers.CACHE_CONTROL, "public, max-age=86400, must-revalidate");
                exchange.getResponseHeaders().put(Headers.CONTENT_TYPE, contentHolder.getContentType());
                exchange.getResponseHeaders().put(Headers.CONTENT_LENGTH, file.length());
            }else {
                // 4. Snappy Web UI: 7-day cache for CSS/JS/Icons
                exchange.getResponseHeaders().put(Headers.CACHE_CONTROL, "public, max-age=604800");
                exchange.getResponseHeaders().put(Headers.CONTENT_TYPE, contentHolder.getContentType());
                exchange.getResponseHeaders().put(Headers.CONTENT_LENGTH, file.length());
            }

            // Because ResourceManager base is "/"
            exchange.setRelativePath(contentHolder.getFilePath());

            // ResourceHandler will now use NIO transferTo()
            // which is the most battery-efficient way to stream large FLAC files
            resourceHandler.handleRequest(exchange);
        } else {
            exchange.setStatusCode(404);
            exchange.getResponseSender().send("File not found");
        }
    }

    @Override
    public void stopServer() {
        Log.v(TAG, "Shutting down Undertow Web Server");

        synchronized (serverLock) {
            if (server != null) {
                try {
                    // Close WebSocket sessions first if you have the list
                    for (WebSocketChannel session : activeWebsocketSessions) {
                        IoUtils.safeClose(session);
                    }
                    activeWebsocketSessions.clear();

                    server.stop();
                    Log.i(TAG, "Undertow Server stopped successfully.");
                } catch (Exception e) {
                    Log.e(TAG, "Error during server shutdown", e);
                } finally {
                    server = null;
                }
            }

            if (serverInitThread != null) {
                try {
                    serverInitThread.join(2000); // Wait for thread to die
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    Log.i(TAG, TAG + " - Interrupted while waiting for server thread to stop.");
                }
            }
        }
    }

    @Override
    public int getListenPort() {
        return WEB_SERVER_PORT;
    }

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

    private ContentHolder lookupAlbumArt(String requestUri) {
        File filePath = getAlbumArt(requestUri);
        return new ContentHolder("image/png", requestUri, filePath.getAbsolutePath(), null);
    }

    static class ContentHolder {
        private final String contentType;
        private final String resId;
        private final String filePath;
        private final String dlnaContentFeatures;

        public ContentHolder(String contentType, String resId, String filePath, String dlnaContentFeatures) {
            this.resId = resId;
            this.filePath = filePath;
            this.contentType = contentType;
            this.dlnaContentFeatures = dlnaContentFeatures;
        }

        public String getFilePath() {
            return filePath;
        }

        public String getContentType() {
            return contentType;
        }

        public String getDlnaContentFeatures() {
            return dlnaContentFeatures;
        }

        public boolean isMedia() {
            if(isEmpty(contentType)) return false;
            return contentType.startsWith("audio/") || contentType.startsWith("video/");
        }

        public boolean isImage() {
            if(isEmpty(contentType)) return false;
            return contentType.startsWith("image/");
        }
    }

    public class WebSocketHandler extends WebSocketContent {
        private final CopyOnWriteArraySet<WebSocketChannel> sessions;
        private static final ObjectMapper MAPPER = new ObjectMapper().setDefaultPropertyInclusion(JsonInclude.Include.ALWAYS);

        public WebSocketHandler(CopyOnWriteArraySet<WebSocketChannel> sessions) {
            this.sessions = sessions;
        }

        @Override
        protected void broadcastMessage(String jsonResponse) {
            if(sessions != null) {
                sessions.parallelStream().filter(WebSocketChannel::isOpen).forEach(session -> WebSockets.sendText(jsonResponse, session, null));
            }
        }

        public void onConnect(WebSocketChannel session) {
            Log.d(TAG, "WS Connected: " + session.getSourceAddress());
            sessions.add(session);

            // Send welcome messages
            for (Map<String, Object> msg : getWelcomeMessages()) {
                try {
                    String jsonResponse = MAPPER.writeValueAsString(msg);
                    WebSockets.sendText(jsonResponse, session, null);
                } catch (JsonProcessingException e) {
                    Log.e(TAG, "Error serializing welcome message", e);
                }
            }
        }

        public void onMessage(WebSocketChannel session, String message) {
            try {
                if (message == null || message.trim().isEmpty()) return;

                @SuppressWarnings("unchecked")
                Map<String, Object> map = MAPPER.readValue(message, Map.class);
                String command = String.valueOf(map.get("command"));

                if (!isEmpty(command)) {
                    Map<String, Object> response = handleCommand(command, map);
                    if (response != null) {
                        String jsonResponse = MAPPER.writeValueAsString(response);
                        WebSockets.sendText(jsonResponse, session, null);
                    }
                }
            } catch (Exception e) {
                Log.e(TAG, "WS Message Error", e);
            }
        }

        public void onClose(WebSocketChannel session) {
            sessions.remove(session);
        }
    }
}