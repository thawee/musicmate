package apincer.android.jupnp.transport.jetty;

import static apincer.music.core.server.ProfileManager.calculateBufferSize;
import static apincer.music.server.jupnp.transport.DLNAHeaderHelper.getDLNAContentFeatures;

import android.content.Context;
import android.util.Log;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.eclipse.jetty.http.HttpHeader;
import org.eclipse.jetty.http.HttpMethod;
import org.eclipse.jetty.http.HttpStatus;
import org.eclipse.jetty.http.content.HttpContent;
import org.eclipse.jetty.http.pathmap.PathSpec;
import org.eclipse.jetty.server.AliasCheck;
import org.eclipse.jetty.server.Connector;
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
// Jetty 12 Specific WebSocket Imports
import org.eclipse.jetty.websocket.api.Session;
import org.eclipse.jetty.websocket.api.annotations.OnWebSocketClose;
import org.eclipse.jetty.websocket.api.annotations.OnWebSocketError;
import org.eclipse.jetty.websocket.api.annotations.OnWebSocketMessage;
import org.eclipse.jetty.websocket.api.annotations.OnWebSocketOpen;
import org.eclipse.jetty.websocket.api.annotations.WebSocket;
import org.eclipse.jetty.websocket.server.WebSocketUpgradeHandler;

import java.io.File;
import java.net.InetAddress;
import java.time.Duration;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArraySet;

import apincer.music.core.model.Track;
import apincer.music.core.repository.FileRepository;
import apincer.music.core.repository.TagRepository;
import apincer.music.core.server.BaseServer;
import apincer.music.core.server.ContentHolder;
import apincer.music.core.server.ProfileManager;
import apincer.music.core.server.ClientProfile;
import apincer.music.core.server.spi.WebServer;
import apincer.music.core.utils.MimeTypeUtils;
import apincer.music.core.utils.StringUtils;

public class JettyWebServerImpl extends BaseServer implements WebServer {
    private static final String TAG = "JettyWebServer";

    // Optimized for Mobile/Embedded use
    private static final int MAX_THREADS = 24;
    private static final int MIN_THREADS = 4;
    private static final int IDLE_TIMEOUT = 300_000; // 5 mins

    // 128KB buffer is a sweet spot for high-res audio on Android memory limits
    private static final int OUTPUT_BUFFER_SIZE = 131_072;

    private Thread serverThread;
    private final Object serverLock = new Object();
    private Server server;

    // Manage sessions at the server level to prevent leaks on restart
    private final CopyOnWriteArraySet<Session> activeWebsocketSessions = new CopyOnWriteArraySet<>();

    private final ProfileManager profileManager;
    private final java.util.concurrent.ExecutorService notificationExecutor;

    public JettyWebServerImpl(Context context, FileRepository fileRepos, TagRepository tagRepos) {
        super(context, fileRepos, tagRepos);
        addLibInfo("Jetty", "");

        // Calculate buffer size based on RAM once
        int bufferSize = calculateBufferSize(context);

        // Initialize the shared manager
        this.profileManager = new ProfileManager(context, bufferSize);
        this.notificationExecutor = java.util.concurrent.Executors.newSingleThreadExecutor();
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
    public void initServer(InetAddress bindAddress) {
        serverThread = new Thread(() -> {
            try {
                // 1. Thread Pool Setup
                QueuedThreadPool threadPool = new QueuedThreadPool();
                threadPool.setMaxThreads(MAX_THREADS);
                threadPool.setMinThreads(MIN_THREADS);
                threadPool.setIdleTimeout(IDLE_TIMEOUT);
                threadPool.setName("jetty-audio-server");

                server = new Server(threadPool);

                // 2. HTTP Configuration
                HttpConfiguration httpConfig = new HttpConfiguration();
                httpConfig.setOutputBufferSize(OUTPUT_BUFFER_SIZE);
                httpConfig.setSendDateHeader(true);
                httpConfig.setSendServerVersion(false);
                httpConfig.setSecurePort(WEB_SERVER_PORT);
                httpConfig.setSecureScheme("http");

                // 3. Connector Setup
                ServerConnector connector = new ServerConnector(server, new HttpConnectionFactory(httpConfig));

                // DSCP tuning: 0x18 = Low Delay (0x10) | High Throughput (0x08)
               /* connector.getHttpConnectionFactory().addCustomizer((connection, request, response) -> {
                    try {
                        java.net.Socket socket = ((org.eclipse.jetty.io.SocketChannelEndPoint)connection.getEndPoint()).getSocket();
                        socket.setTrafficClass(0x18);
                    } catch (Exception ignore) {}
                }); */

                connector.setHost("0.0.0.0");
                connector.setPort(WEB_SERVER_PORT);
                connector.setIdleTimeout(IDLE_TIMEOUT);
                connector.setReuseAddress(true);
                server.setConnectors(new Connector[]{connector});

                // 4. WebSocket Context (Jetty 12 Style)
                ContextHandler wsContext = new ContextHandler();
                wsContext.setContextPath(CONTEXT_PATH_WEBSOCKET);
                wsContext.setAllowNullPathInContext(true); // Allow ws://ip:port/ws (without trailing slash)

                // WebSocketUpgradeHandler.from(...) signature changed in Jetty 12
                WebSocketUpgradeHandler wsHandler = WebSocketUpgradeHandler.from(server, wsContext, (container) -> {
                    // Configure the ServerWebSocketContainer
                    container.setMaxTextMessageSize(64 * 1024);
                    container.setIdleTimeout(Duration.ofMinutes(10));

                    // Add mapping using the Jetty 12 specific API
                    PathSpec pathSpec = PathSpec.from("^.*$"); //accept any string including empty space
                    container.addMapping(pathSpec, (req, resp, callback) -> {
                        // Create and return WebSocket endpoint instance
                        return new JettyWebServerImpl.WebSocketHandler(activeWebsocketSessions);
                    });
                });

                //Required:
                AliasCheck aliasCheck = (pathInContext, resource) -> true;
                wsContext.setAliasChecks(Collections.singletonList(aliasCheck)); // bypass alias check
                wsContext.setAllowNullPathInContext(true);

                // Optional: Add context attributes
                wsContext.setAttribute("websocket.server.version", "12.1.2");
                wsContext.setAttribute("websocket.max.connections", 1000);

                wsContext.setHandler(wsHandler);

                // 5. Web Content & Streaming Handler
                ResourceHandler webHandler = new WebContentHandler();
                webHandler.setBaseResource(ResourceFactory.of(webHandler).newResource("/"));
                webHandler.setDirAllowed(false);
                webHandler.setAcceptRanges(true);
                webHandler.setServer(server);

                ContextHandler webContext = new ContextHandler(CONTEXT_PATH_ROOT);
                webContext.setAliasChecks(Collections.singletonList(aliasCheck)); // bypass alias check
                webContext.setHandler(webHandler);

                // 6. Combine Handlers
                ContextHandlerCollection handlers = new ContextHandlerCollection();
                handlers.addHandler(wsContext);  // Check WS first
                handlers.addHandler(webContext); // Check Files second
                server.setHandler(handlers);

                server.setStopAtShutdown(true);
                server.start();

                Log.i(TAG, "WebServer started on " + bindAddress.getHostAddress() + ":" + WEB_SERVER_PORT +" successfully.");
                server.join();

            } catch (Exception e) {
                Log.e(TAG, "Failed to start WebServer", e);
            }
        });

        serverThread.setName("jetty-server-runner");
        serverThread.start();
    }

    @Override
    public void stopServer() {
        synchronized (serverLock) {
            if (server != null) {
                Log.i(TAG, "Stopping WebServer");

                // Graceful WebSocket cleanup
                for (Session session : activeWebsocketSessions) {
                    try {
                        if (session.isOpen()) session.close(1001, "Server stopping", null);
                    } catch (Exception ignored) {
                    }
                }
                activeWebsocketSessions.clear();

                try {
                    if (server.isRunning()) {
                        server.stop();
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Error stopping server", e);
                }finally {
                    server = null;
                }

                if (serverThread != null && serverThread.isAlive()) {
                    try {
                        serverThread.join(3000); // Wait up to 3 seconds
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                }

                if (notificationExecutor != null) {
                    notificationExecutor.shutdown();
                }
            }
        }
    }

    @Override
    public int getListenPort() { return WEB_SERVER_PORT; }

    // =============================================================
    // Custom Resource Handler for Files and Audio
    // =============================================================
    private class WebContentHandler extends ResourceHandler {

        @Override
        public boolean handle(Request request, Response response, Callback callback) throws Exception {
            response.getHeaders().put(HttpHeader.SERVER, "MusicMate-Server");

            if (!HttpMethod.GET.is(request.getMethod()) && !HttpMethod.HEAD.is(request.getMethod())) {
                return false; // Not a GET request, let other handlers try
            }

            String rawPath = request.getHttpURI().getPath();
            String userAgent = request.getHeaders().get(HttpHeader.USER_AGENT);
            String remoteAddr = Request.getRemoteAddr(request);

            // Check if this request belongs to WebSocket context to avoid 404s
            if (rawPath.startsWith(CONTEXT_PATH_WEBSOCKET)) {
                return false;
            }

            ContentHolder contentHolder = resolveRequest(rawPath, remoteAddr, userAgent);

            if (contentHolder.isImage()) {
                File filePath = new File(contentHolder.getFilePath());
                return sendResource(filePath, request, response, callback);
            } else if (contentHolder.isMedia()) {
                Track song = contentHolder.getTrack();// getSong(requestUri);
                return sendSong(song, request, response, callback);
            } else {
                File filePath = new File(contentHolder.getFilePath());
                return sendResource(filePath, request, response, callback);
            }
        }

        private boolean sendSong(Track song, Request request, Response response, Callback callback) throws Exception {
            if (song == null) return false;

            File audioFile = new File(song.getPath());

            String userAgent = request.getHeaders().get(HttpHeader.USER_AGENT);
            // ONE LINE DETECTION
            ClientProfile profile = profileManager.detect(userAgent);

            prepareMusicStreamingHeaders(response, song, profile);

            return sendResource(audioFile, request, response, callback);
        }

        private boolean sendResource(File filePath, Request request, Response response, Callback callback) throws Exception {
            if (filePath == null) return false;

            if (!filePath.exists() || !filePath.canRead()) {
                response.setStatus(HttpStatus.NOT_FOUND_404);
                callback.succeeded();
                return true;
            }

            // Add dynamic ETag support
            String etag = generateETag(filePath);
            response.getHeaders().put(HttpHeader.ETAG, etag);

            // Jetty 12 ResourceService usage
            HttpContent content = getResourceService().getContent(filePath.getAbsolutePath(), request);
            if (content == null) return false;

            String mimeType = MimeTypeUtils.getMimeTypeFromPath(filePath.getAbsolutePath());
            if (mimeType != null) {
                response.getHeaders().put(HttpHeader.CONTENT_TYPE, mimeType);
            }

            getResourceService().doGet(request, response, callback, content);
            return true;
        }

        private void prepareMusicStreamingHeaders(Response response, Track tag, ClientProfile profile) {
            response.getHeaders().put("transferMode.dlna.org", "Streaming");
            response.getHeaders().put("contentFeatures.dlna.org", getDLNAContentFeatures(tag));
            response.getHeaders().put(HttpHeader.ACCEPT_RANGES, "bytes");

            // Audiophile Dynamic Headers
            if (tag.getAudioSampleRate() > 0) response.getHeaders().put("X-Audio-Sample-Rate", tag.getAudioSampleRate() + " Hz");
            if (tag.getAudioBitsDepth() > 0) response.getHeaders().put("X-Audio-Bit-Depth", tag.getAudioBitsDepth() + " bit");
            if (tag.getAudioBitRate() > 0) response.getHeaders().put("X-Audio-Bitrate", tag.getAudioBitRate()/1000 + " kbps");
            response.getHeaders().put("X-Audio-Format", String.valueOf(tag.getFileType()));
            response.getHeaders().put("X-Audio-Bit-Perfect", "true");
        }
    }

    // =============================================================
    // WebSocket Handler (POJO Annotation Style)
    // =============================================================
    @WebSocket
    public class WebSocketHandler extends WebSocketContent {
        private final CopyOnWriteArraySet<Session> sessions;
        private static final ObjectMapper MAPPER = new ObjectMapper().setDefaultPropertyInclusion(JsonInclude.Include.ALWAYS);
               // .setSerializationInclusion(JsonInclude.Include.ALWAYS);

        public WebSocketHandler(CopyOnWriteArraySet<Session> sessions) {
            this.sessions = sessions;
        }

        @Override
        protected void broadcastMessage(String jsonResponse) {
            if(sessions != null) {
                sessions.parallelStream().filter(Session::isOpen).forEach(session -> session.sendText(jsonResponse, null));
            }
        }

        @OnWebSocketOpen
        public void onConnect(Session session) {
            Log.d(TAG, "WS Connected: " + session.getRemoteSocketAddress());
            sessions.add(session);

            // Send welcome messages
            for (Map<String, Object> msg : getWelcomeMessages()) {
                try {
                    String jsonResponse = MAPPER.writeValueAsString(msg);
                    session.sendText(jsonResponse, org.eclipse.jetty.websocket.api.Callback.NOOP);
                } catch (JsonProcessingException e) {
                    Log.e(TAG, "Error serializing welcome message", e);
                }
            }
        }

        @OnWebSocketMessage
        public void onMessage(Session session, String message) {
            try {
                if (message == null || message.trim().isEmpty()) return;

                @SuppressWarnings("unchecked")
                Map<String, Object> map = MAPPER.readValue(message, Map.class);
                String command = String.valueOf(map.get("command"));

                if (!StringUtils.isEmpty(command)) {
                    Map<String, Object> response = handleCommand(command, map);
                    if (response != null) {
                        String jsonResponse = MAPPER.writeValueAsString(response);
                        session.sendText(jsonResponse, org.eclipse.jetty.websocket.api.Callback.NOOP);
                    }
                }
            } catch (Exception e) {
                Log.e(TAG, "WS Message Error", e);
            }
        }

        @OnWebSocketClose
        public void onClose(Session session, int statusCode, String reason) {
            sessions.remove(session);
        }

        @OnWebSocketError
        public void onError(Session session, Throwable error) {
            Log.e(TAG, "WS Error", error);
        }
    }
}