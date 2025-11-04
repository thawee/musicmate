package apincer.android.jupnp.transport.jetty;

import static apincer.music.core.Constants.COVER_ARTS;
import static apincer.music.core.Constants.DEFAULT_COVERART;
import static apincer.music.core.server.DLNAHeaderHelper.getDLNAContentFeatures;
import static apincer.music.core.utils.StringUtils.isEmpty;

import android.content.Context;
import android.util.Log;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

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
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArraySet;

import apincer.music.core.database.MusicTag;
import apincer.music.core.repository.FileRepository;
import apincer.music.core.repository.TagRepository;
import apincer.music.core.server.BaseServer;
import apincer.music.core.server.spi.WebServer;
import apincer.music.core.utils.MimeTypeUtils;
import apincer.music.core.utils.TagUtils;

public class JettyWebServerImpl extends BaseServer implements WebServer {
    private static final String TAG = "JettyWebServer";

    // Optimized server configuration for audiophile streaming
    //private static final int OUTPUT_BUFFER_SIZE = 262144; // 262144 - 256KB for better high-res streaming
    private static final int OUTPUT_BUFFER_SIZE = 131072; // 128kb  ideal balance of performance and memory for your personal server
    //private static final int OUTPUT_BUFFER_SIZE = 65536; // 64KB is a very safe and common value
    //private static final int OUTPUT_BUFFER_SIZE = 32768; // 32KB is often sufficient for audio
    private static final int MAX_THREADS = 4; //30;
    private static final int MIN_THREADS = 2; //6;
    private static final int IDLE_TIMEOUT = 300000; // 5 minutes
    private static final int CONNECTION_TIMEOUT = 600000; // 10 minutes

    // Additional performance settings for high-resolution audio
    private static final int REQUEST_HEADER_SIZE = 8192; //  standard default for most web servers
    private static final int RESPONSE_HEADER_SIZE = 8192; // standard default for most web servers
    private static final int ACCEPT_QUEUE_SIZE = 16; // More than enough for a personal server.

    private Thread serverThread;
    private Server server;

    public JettyWebServerImpl(Context context, FileRepository fileRepos, TagRepository tagRepos) {
        super(context, fileRepos, tagRepos);
        addLibInfo("Jetty", "12.1.2");
    }

    public void initServer(InetAddress bindAddress) {
        // Initialize the server with the specified port.
        serverThread = new Thread(() -> {
            try {
                //Log.i(TAG, "Starting Content Server (Jetty) on " + bindAddress.getHostAddress() + ":" + CONTENT_SERVER_PORT);

                // Configure thread pool optimized for audio streaming
                QueuedThreadPool threadPool = new QueuedThreadPool();
                threadPool.setMaxThreads(MAX_THREADS);
                threadPool.setMinThreads(MIN_THREADS);
                threadPool.setIdleTimeout(IDLE_TIMEOUT);
                threadPool.setName("jetty-content-server");
                threadPool.setDetailedDump(false); // Enable detailed dumps for debugging
                // Add this after creating the thread pool
                threadPool.setThreadsPriority(Thread.NORM_PRIORITY + 1); // Slightly higher priority

                server = new Server(threadPool);
                // Add this line after creating the server
                server.setAttribute("org.eclipse.jetty.server.Request.maxFormContentSize", 10000);

                // HTTP Configuration optimized for media streaming
                HttpConfiguration httpConfig = new HttpConfiguration();
                httpConfig.setOutputBufferSize(OUTPUT_BUFFER_SIZE);
                httpConfig.setSendDateHeader(true);
                httpConfig.setSendServerVersion(false);
              //  httpConfig.setSendXPoweredBy(true);
                httpConfig.setRequestHeaderSize(REQUEST_HEADER_SIZE);
                httpConfig.setResponseHeaderSize(RESPONSE_HEADER_SIZE);
                httpConfig.setSecurePort(WEB_SERVER_PORT);
                httpConfig.setSecureScheme("http");

                // HTTP connector with optimized settings for stable streaming
                ServerConnector connector = new ServerConnector(server, new HttpConnectionFactory(httpConfig));
                connector.setHost("0.0.0.0"); // Bind only to IPv4
                connector.setPort(WEB_SERVER_PORT);
                connector.setIdleTimeout(CONNECTION_TIMEOUT);
                connector.setAcceptQueueSize(ACCEPT_QUEUE_SIZE);
                connector.setReuseAddress(true); // Better address reuse for quick restarts

                server.setConnectors(new Connector[]{connector});

                // 1. WebSocket Handler on /ws context
                WebSocketUpgradeHandler wsUpgradeHandler = WebSocketUpgradeHandler.from(server, container -> {
                    // Configure WebSocket policy
                    container.setMaxTextMessageSize(64 * 1024); // 64KB max message size
                    container.setIdleTimeout(Duration.ofMinutes(10)); // 10 minutes idle timeout
                    container.setMaxBinaryMessageSize(64 * 1024);

                    // Add WebSocket endpoint mapping
                    PathSpec pathSpec = PathSpec.from("^.*$"); //accept any string including empty space
                    container.addMapping(pathSpec, (req, resp, callback) -> {
                        // Create and return WebSocket endpoint instance
                        return new WebSocketHandler();
                    });
                });

                // Create context handler for WebSocket
                ContextHandler wsContext = new ContextHandler();
                wsContext.setContextPath(CONTEXT_PATH_WEBSOCKET);
                wsContext.setHandler(wsUpgradeHandler);

                //Required:
                AliasCheck aliasCheck = (pathInContext, resource) -> true;
                wsContext.setAliasChecks(Collections.singletonList(aliasCheck)); // bypass alias check
                wsContext.setAllowNullPathInContext(true);

                // Optional: Add context attributes
                wsContext.setAttribute("websocket.server.version", "12.1.2");
                wsContext.setAttribute("websocket.max.connections", 1000);

                // 2. Music file, AlbumArt and static files (HTML, CSS, JS) on root context
                ResourceHandler webHandler = new WebContentHandler();
                webHandler.setBaseResource(ResourceFactory.of(webHandler).newResource("/"));
                webHandler.setDirAllowed(false);
                webHandler.setAcceptRanges(true);  // Enable range requests for seeking
                webHandler.setEtags(true);         // Enable ETags for caching
                webHandler.setCacheControl("public, max-age=86400");
                webHandler.setServer(server);
                ContextHandler webContext = new ContextHandler(CONTEXT_PATH_ROOT);
                webContext.setAliasChecks(Collections.singletonList(aliasCheck)); // bypass alias check
                webContext.setHandler(webHandler);

                // 4. Combine all handlers using ContextHandlerCollection
                ContextHandlerCollection handlers = new ContextHandlerCollection(wsContext, webContext);
                server.setHandler(handlers);

                server.setStopAtShutdown(true);
                server.setStopTimeout(5000);
                server.start();

                Log.i(TAG, "WebServer started on " + bindAddress.getHostAddress() + ":" + WEB_SERVER_PORT +" successfully.");

                server.join(); // Keep the thread alive until the server is stopped.
            } catch (Exception e) {
                Log.e(TAG, "Failed to start WebServer", e);
               // throw new RuntimeException(e);
            }
        });

        serverThread.setName("jetty-server-runner");
        serverThread.start();
    }

    public void stopServer() {
        // Stop the server.
        Log.i(TAG, "Stopping WebServer (Jetty)");

        try {
            if (server != null && server.isRunning()) {
                server.stop();
            }
        } catch (Exception e) {
            Log.e(TAG, "Error stopping WebServer", e);
           // throw new RuntimeException(e);
        }
        // Add thread cleanup
        if (serverThread != null && serverThread.isAlive()) {
            try {
                serverThread.join(5000); // Wait up to 5 seconds
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                Log.w(TAG, "Interrupted while waiting for server thread", e);
            }
        }
    }

    @Override
    public String getComponentName() {
        return "ContentServer";
    }

    @Override
    public int getListenPort() {
        return WEB_SERVER_PORT;
    }

    private class WebContentHandler extends ResourceHandler {
        final File defaultCoverartDir;

        private WebContentHandler() {
            defaultCoverartDir = new File(getCoverartDir(COVER_ARTS),DEFAULT_COVERART);
        }

        @Override
        public boolean handle(Request request, Response response, Callback callback) throws Exception {
            response.getHeaders().put(HttpHeader.SERVER, getServerSignature(getComponentName()));

            if (!HttpMethod.GET.is(request.getMethod()) && !HttpMethod.HEAD.is(request.getMethod())) {
                return super.handle(request, response, callback);
            }

            String requestUri = request.getHttpURI().getPath();

            if (isEmpty(requestUri) || requestUri.equals("/")) {
                requestUri = "/index.html";
            }

            if (requestUri.startsWith(CONTEXT_PATH_WEBSOCKET)) {
                Log.w(TAG, "WebSocket Content: " + request.getHttpURI().getPath());
                return super.handle(request, response, callback);
            }

            if (requestUri.startsWith(CONTEXT_PATH_COVERART)) {
                // Log.d(TAG, "Processing album art request: " + uri);
                File filePath = getAlbumArt(requestUri);
                return sendResource(filePath, request, response, callback);
            } else if (requestUri.startsWith(CONTEXT_PATH_MUSIC)) {
                MusicTag song = getSong(requestUri);
                return sendSong(song, request, response, callback);
            }else {
                File filePath = getWebResource(requestUri);
                return sendResource(filePath, request, response, callback);
            }
        }

        private boolean sendSong(MusicTag song, Request request, Response response, Callback callback) throws Exception {
            if (song == null) {
                Log.w(TAG, "Content not found for URI: " + request.getHttpURI().getPath());
                return super.handle(request, response, callback);
            }
            File audioFile = new File(song.getPath());

            // Prepare the special headers FIRST
            prepareMusicStreamingHeaders(response, song);

            //notify new song playing
            notifyPlayback(Request.getRemoteAddr(request), request.getHeaders().get(HttpHeader.USER_AGENT), song);

            // Now, call the generic file sender
            return sendResource(audioFile, request, response, callback);
        }

        private boolean sendResource(File filePath, Request request, Response response, Callback callback) throws Exception {
            if (filePath == null) {
                //Log.w(TAG, "Content not found for URI: " + request.getHttpURI().getPath());
                return super.handle(request, response, callback);
            }

            if (!filePath.exists() || !filePath.canRead()) {
                Log.e(TAG, "file not accessible: " + filePath);
                response.setStatus(HttpStatus.NOT_FOUND_404);
                callback.succeeded();
                return true;
            }
            HttpContent content = getResourceService().getContent(filePath.getAbsolutePath(), request);
            if (content == null) {
                Log.w(TAG, "Jetty could not get content for path: " + filePath);
                return super.handle(request, response, callback);
            }

            prepareResponseHeaders(response, filePath);

            getResourceService().doGet(request, response, new CompletionCallback(callback), content);
            return true;
        }

        private void prepareResponseHeaders(Response response, File filePath) {
            String mimeType = MimeTypeUtils.getMimeTypeFromPath(filePath.getAbsolutePath());
            if (mimeType != null) {
                response.getHeaders().put(HttpHeader.CONTENT_TYPE, mimeType);
            }
        }

        private void prepareMusicStreamingHeaders(Response response, MusicTag song) {
            addDlnaHeaders(response, song);
            addAudiophileHeaders(response, song);
        }

        /**
         * Add DLNA-specific headers for optimal client compatibility
         */
        private void addDlnaHeaders(Response response, MusicTag tag) {
            // Common DLNA headers
            response.getHeaders().put("transferMode.dlna.org", "Streaming");
            response.getHeaders().put("contentFeatures.dlna.org", getDLNAContentFeatures(tag));

            // Some renderers need this to know they can seek
            response.getHeaders().put(HttpHeader.ACCEPT_RANGES, "bytes");
        }

        /**
         * Add audiophile-specific headers with detailed audio quality information
         */
        private void addAudiophileHeaders(Response response, MusicTag tag) {
            // Add custom headers with detailed audio information for audiophile clients
            if (tag.getAudioSampleRate() > 0) response.getHeaders().put("X-Audio-Sample-Rate", tag.getAudioSampleRate() + " Hz");
            if (tag.getAudioBitsDepth() > 0) response.getHeaders().put("X-Audio-Bit-Depth", tag.getAudioBitsDepth() + " bit");
            if (tag.getAudioBitRate() > 0) response.getHeaders().put("X-Audio-Bitrate", tag.getAudioBitRate() + " kbps");
            if (TagUtils.getChannels(tag) > 0) response.getHeaders().put("X-Audio-Channels", String.valueOf(TagUtils.getChannels(tag)));
            response.getHeaders().put("X-Audio-Format", Objects.toString(tag.getFileType(), ""));
        }
    }

    @WebSocket
    public class WebSocketHandler extends WebSocketContent{
        // Store all active sessions
        private static final CopyOnWriteArraySet<Session> sessions = new CopyOnWriteArraySet<>();
        private static final int MAX_SESSIONS = 100;
        private static final Gson GSON = new GsonBuilder()
                .disableHtmlEscaping() // Reduces string processing
                .serializeNulls() // Optional: skip nulls to reduce JSON size
                .create();
       // private final WebSocketContent wsContent = buildWebSocketContent();

        public WebSocketHandler() {
            super();
           /* registerPlaybackCallback(new PlaybackCallback() {
                @Override
                public void onMediaTrackChanged(MediaTrack metadata) {
                  //  broadcastNowPlaying(metadata);
                }

                @Override
                public void onPlaybackStateChanged(PlaybackState state) {
                    broadcastPlaybackState(state);
                }

                @Override
                public void onPlaybackTargetChanged(PlaybackTarget playbackTarget) {
                   // broadcastPlaybackTarget(playbackTarget);
                }
            }); */
            //subscribePlaybackState(playbackState -> broadcastPlaybackState(playbackState));
        }

        @Override
        protected void broadcastMessage(String jsonResponse) {
            // logger.debug("Broadcasting message to {} sessions: {}", sessions.size(), message);

            sessions.parallelStream()
                    .filter(Session::isOpen)
                    .forEach(session -> {
                        try {
                            session.sendText(jsonResponse, null);
                        } catch (Exception e) {
                            // Remove dead session
                            sessions.remove(session);
                        }
                    });
        }

        @OnWebSocketOpen
        public void onConnect(Session session) {
            Log.d(TAG, "New WebSocket connection: "+session.getRemoteSocketAddress());
            if (sessions.size() >= MAX_SESSIONS) {
                Log.w(TAG, "Max WebSocket connections reached, rejecting new connection");
                session.close(1008, "Server full", null);
                return;
            }
            sessions.add(session);
            List<Map<String, Object>> messages = getWelcomeMessages();
            for (Map<String, Object> message : messages) {
                sendMessage(session, message);
            }
        }

        private void sendMessage(Session session, Map<String, Object> response) {
            if (response != null) {
                String jsonResponse = GSON.toJson(response);
                session.sendText(jsonResponse, null);
                Log.d(TAG, TAG+" - Response message: " + jsonResponse);
            }
        }

        @OnWebSocketMessage
        public void onMessage(Session session, String message) {
            Log.d(TAG, "Received message from "+session.getRemoteSocketAddress()+", message="+ message);
            if (message == null || message.length() < 2) { // Minimum: "{}"
                return;
            }

            // More efficient whitespace check
            char firstChar = message.charAt(0);
            if (firstChar != '{') {
                if (!Character.isWhitespace(firstChar)) {
                    return;
                }
                // Only trim if needed
                message = message.trim();
                if (message.isEmpty() || message.charAt(0) != '{') {
                    return;
                }
            }

            try {
                @SuppressWarnings("unchecked")
                Map<String, Object> messageMap = GSON.fromJson(message, Map.class);

                // Add null check
                if (messageMap == null) {
                    Log.w(TAG, "Null message map, ignoring");
                    return;
                }

                String command = String.valueOf(messageMap.getOrDefault("command", ""));

                // Validate command before processing
                if (command.isEmpty()) {
                    Log.w(TAG, "Empty command, ignoring");
                    return;
                }

                Map<String, Object> response = handleCommand(command, messageMap);
                if(response != null) {
                    session.sendText(GSON.toJson(response), null);
                    Log.d(TAG, "Response message: " + GSON.toJson(response));
                }
            } catch (com.google.gson.JsonSyntaxException e) {
                // Catching the specific exception for bad JSON.
                Log.e(TAG, "Error parsing WebSocket JSON message: " + message, e);
                // We don't call onError because this is a client-side data error, not a connection error.
            } catch (Exception e) {
                Log.e(TAG, "Error processing WebSocket message", e);
                onError(session, e);
            }
        }

        @OnWebSocketClose
        public void onClose(Session session, int statusCode, String reason) {
            Log.d(TAG, "WebSocket connection closed: "+session.getRemoteSocketAddress());

            sessions.remove(session);
        }

        @OnWebSocketError
        public void onError(Session session, Throwable error) {
            // Add logging here
            Log.e(TAG, "WebSocket error on session " + session.getRemoteSocketAddress(), error);
        }

        // Shutdown cleanup executor when server stops
        public void shutdown() {
            sessions.forEach(Session::close);
            sessions.clear();
        }
    }

    private static class CompletionCallback implements Callback {
        private final Callback delegate;
        public CompletionCallback(Callback delegate) { this.delegate = delegate; }
        @Override public void succeeded() { delegate.succeeded(); }
        @Override public void failed(Throwable x) {
            // Don't log common client-side errors like "Connection reset by peer" as failures.
            if (x instanceof java.io.IOException && "Broken pipe".equals(x.getMessage())) {
                Log.d(TAG, "Stream ended: client closed connection.");
            }// else {
            //    Log.w(TAG, "Stream failed with error", x);
            //}
            delegate.failed(x);
        }
    }
}
