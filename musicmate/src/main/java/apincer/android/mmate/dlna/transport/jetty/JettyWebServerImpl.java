package apincer.android.mmate.dlna.transport.jetty;
import static android.content.Context.BIND_AUTO_CREATE;
import static apincer.android.mmate.Constants.COVER_ARTS;
import static apincer.android.mmate.Constants.DEFAULT_COVERART_FILE;
import static apincer.android.mmate.dlna.MediaServerConfiguration.WEB_SERVER_PORT;
import static apincer.android.mmate.utils.StringUtils.isEmpty;

import android.annotation.SuppressLint;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.IBinder;
import android.util.Log;

import com.google.gson.Gson;

import org.eclipse.jetty.http.HttpHeader;
import org.eclipse.jetty.http.HttpMethod;
import org.eclipse.jetty.http.HttpStatus;
import org.eclipse.jetty.http.content.HttpContent;
import org.eclipse.jetty.http.pathmap.PathSpec;
import org.eclipse.jetty.server.AliasCheck;
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
import org.jupnp.transport.Router;
import org.jupnp.transport.spi.InitializationException;

import java.io.File;
import java.io.IOException;
import java.net.InetAddress;
import java.time.Duration;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArraySet;

import apincer.android.mmate.dlna.content.WebSocketContent;
import apincer.android.mmate.dlna.transport.StreamServerConfigurationImpl;
import apincer.android.mmate.dlna.transport.StreamServerImpl;
import apincer.android.mmate.playback.NowPlaying;
import apincer.android.mmate.playback.PlaybackService;
import apincer.android.mmate.repository.FileRepository;
import apincer.android.mmate.utils.ApplicationUtils;
import apincer.android.mmate.utils.MimeTypeUtils;

public class JettyWebServerImpl extends StreamServerImpl.StreamServer {
    private static final String TAG = "JettyWebServer";

    private final Context context;
    private Server server;
    private final WebSocketContent wsContent = new WebSocketContent();
    private final WebSocketHandler wsHandler = new WebSocketHandler();
    private PlaybackService playbackService;
    private boolean isPlaybackServiceBound = false;

    private final ServiceConnection serviceConnection = new ServiceConnection() {
        @SuppressLint("CheckResult")
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            // Get the binder from the service and set the mediaServerService instance
            PlaybackService.PlaybackServiceBinder binder = (PlaybackService.PlaybackServiceBinder) service;
            playbackService = binder.getService();
            isPlaybackServiceBound = true;
            if(playbackService != null) {
                //observe song and player, and playing progress
                playbackService.getNowPlayingSubject().subscribe(wsHandler::broadcastNowPlaying);
            }
            wsContent.setPlaybackService(playbackService);
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            playbackService = null;
            isPlaybackServiceBound = false;
            wsContent.setPlaybackService(null);
            // Log.w(TAG, "PlaybackService disconnected unexpectedly.");
        }
    };

    public JettyWebServerImpl(Context context, Router router, StreamServerConfigurationImpl configuration) {
        super(context, router, configuration);
        this.context = context;
    }

    @Override
    public void initServer(InetAddress bindAddress) throws InitializationException {
        // Bind to the MediaServerService as soon as this service is created
        Intent intent = new Intent(getContext(), PlaybackService.class);
        getContext().bindService(intent, serviceConnection, BIND_AUTO_CREATE);

        Thread serverThread = new Thread(() -> {
            try {
                //Log.i(TAG, "Starting Web Server (Jetty) on " + bindAddress.getHostAddress() + ":" + WEB_SERVER_PORT);

                prepareWebUIFiles();

                QueuedThreadPool threadPool = new QueuedThreadPool(12, 4, 30000);
                threadPool.setName("jetty-web-server");

                server = new Server(threadPool);

                HttpConfiguration httpConfig = new HttpConfiguration();
                httpConfig.setOutputBufferSize(65536);
                httpConfig.setSendServerVersion(false);
                httpConfig.setRequestHeaderSize(8192);

                ServerConnector connector = new ServerConnector(server, new HttpConnectionFactory(httpConfig));
                connector.setHost("0.0.0.0");
                connector.setPort(WEB_SERVER_PORT);
                connector.setIdleTimeout(60000);
                server.addConnector(connector);

                // 1. WebSocket Handler on /ws context
               // ContextHandler wsContext = new ContextHandler("/ws");
                WebSocketUpgradeHandler wsUpgradeHandler = WebSocketUpgradeHandler.from(server, container -> {
                    // Configure WebSocket policy
                    container.setMaxTextMessageSize(64 * 1024); // 64KB max message size
                    container.setIdleTimeout(Duration.ofMinutes(10)); // 10 minutes idle timeout
                    container.setMaxBinaryMessageSize(64 * 1024);

                    // Add WebSocket endpoint mapping
                    PathSpec pathSpec = PathSpec.from("^.*$");
                    container.addMapping(pathSpec, (req, resp, callback) -> {
                    // Create and return WebSocket endpoint instance
                        return wsHandler;
                    });
                });

                // Create context handler for WebSocket
                ContextHandler wsContext = new ContextHandler();
                wsContext.setContextPath(WEBSOCKET_PATH);
                wsContext.setHandler(wsUpgradeHandler);

                //Required:
                AliasCheck aliasCheck = (pathInContext, resource) -> true;
                wsContext.setAliasChecks(Collections.singletonList(aliasCheck)); // bypass alias check
                wsContext.setAllowNullPathInContext(true);

                // Optional: Add context attributes
                wsContext.setAttribute("websocket.server.version", "12.1.0");
                wsContext.setAttribute("websocket.max.connections", 1000);

                // 2. Album Art Handler on /coverart and static files (HTML, CSS, JS) on root context
                ResourceHandler webHandler = new WebContentHandler();
                webHandler.setBaseResource(ResourceFactory.of(webHandler).newResource("/"));
                webHandler.setDirAllowed(false);
                webHandler.setCacheControl("public, max-age=86400");
                webHandler.setServer(server);
                ContextHandler webContext = new ContextHandler(CONTEXT_PATH);
                webContext.setAliasChecks(Collections.singletonList(aliasCheck)); // bypass alias check
                webContext.setHandler(webHandler);

                // 4. Combine all handlers using ContextHandlerCollection
                ContextHandlerCollection handlers = new ContextHandlerCollection(wsContext, webContext);
                server.setHandler(handlers);

                server.start();

                Log.i(TAG, "WebUI server started on " +
                        bindAddress.getHostAddress() + ":" + WEB_SERVER_PORT+" successfully.");

                server.join(); // last step
            } catch (Exception ex) {
                Log.e(TAG, "Http WebUI server initialization failed: " + ex.getMessage(), ex);
            }
        });
        serverThread.setName("jetty-web-runner");
        serverThread.start();
    }

    private void prepareWebUIFiles() {
        try {
            ApplicationUtils.deleteFiles(getContext(), "webui");
            ApplicationUtils.copyFileOrDirToCache(getContext(), "webui");
        } catch (IOException e) {
            throw new RuntimeException("Failed to prepare Web UI files", e);
        }
    }

    @Override
    public void stopServer() {
       // Log.i(TAG, "Stopping WebUI Server (Jetty)");
        try {
            if (server != null && server.isRunning()) {
                server.stop();
            }
        } catch (Exception e) {
            Log.e(TAG, "Error stopping Web server (Jetty)", e);
        }
        if(isPlaybackServiceBound) {
            context.unbindService(serviceConnection);
        }

        Log.i(TAG, "Http WebUI server stopped successfully");
    }

    @Override
    protected String getServerVersion() {
        return "Jetty/12.1.1";
    }

    private class WebContentHandler extends ResourceHandler {
        final File defaultCoverartDir;

        private WebContentHandler() {
            defaultCoverartDir = new File(getCoverartDir(COVER_ARTS),DEFAULT_COVERART_FILE);
        }

        @Override
        public boolean handle(Request request, Response response, Callback callback) throws Exception {
            response.getHeaders().put(HttpHeader.SERVER, getFullServerName());

            if (!HttpMethod.GET.is(request.getMethod()) && !HttpMethod.HEAD.is(request.getMethod())) {
                return super.handle(request, response, callback);
            }

            String requestUri = request.getHttpURI().getPath();

            if (isEmpty(requestUri) || requestUri.equals("/")) {
                requestUri = "/index.html";
            }

            if (requestUri.startsWith(WEBSOCKET_PATH)) {
                Log.w(TAG, "WebSocket Content: " + request.getHttpURI().getPath());
                return super.handle(request, response, callback);
            }

            File filePath = null;
            if (requestUri.startsWith("/coverart/")) {
                // Log.d(TAG, "Processing album art request: " + uri);
                filePath =  handleAlbumArt(request, requestUri);
            }else {
                filePath = getWebResource(request, requestUri);
            }

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

        private File getWebResource(Request request, String requestUri) {
            File filePath = new File(getContext().getFilesDir(), "webui");
            if(requestUri.contains("?")) {
                requestUri = requestUri.substring(0, requestUri.indexOf("?"));
            }
            return new File(filePath, requestUri);
        }

        private File handleAlbumArt(Request request, String requestUri) {
            String albumUniqueKey = requestUri.substring("/coverart/".length(), requestUri.indexOf(".png"));
            return FileRepository.getCoverArt(albumUniqueKey);
        }

        private void prepareResponseHeaders(Response response, File filePath) {
            String mimeType = MimeTypeUtils.getMimeTypeFromPath(filePath.getAbsolutePath());
            if (mimeType != null) {
                response.getHeaders().put(HttpHeader.CONTENT_TYPE, mimeType);
            }
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

    @WebSocket
    public class WebSocketHandler {
        // Store all active sessions
        private static final CopyOnWriteArraySet<Session> sessions = new CopyOnWriteArraySet<>();
        private final Gson gson = new Gson();

        @OnWebSocketOpen
        public void onConnect(Session session) {
            Log.d(TAG, "New WebSocket connection: "+session.getRemoteSocketAddress());
            sessions.add(session);
        }

        @OnWebSocketMessage
        public void onMessage(Session session, String message) {
            Log.d(TAG, "Received message from "+session.getRemoteSocketAddress()+", message="+ message);
            Map<String, Object> messageMap = gson.fromJson(message, Map.class);
            String command = messageMap.getOrDefault("command", "").toString();

            Map<String, Object> response = wsContent.handleCommand(command, messageMap);
            if(response != null) {
                session.sendText(gson.toJson(response), null);
                Log.d(TAG, "Response message: " + gson.toJson(response));
            }
        }

        @OnWebSocketClose
        public void onClose(Session session, int statusCode, String reason) {
            Log.d(TAG, "WebSocket connection closed: "+session.getRemoteSocketAddress());

            sessions.remove(session);
        }

        @OnWebSocketError
        public void onError(Session session, Throwable error) {
        }

        /**
         * Broadcast message to all connected clients
         * @param message Message to broadcast
         */
        private void broadcast(String message) {
           // logger.debug("Broadcasting message to {} sessions: {}", sessions.size(), message);

            sessions.parallelStream()
                    .filter(Session::isOpen)
                    .forEach(session -> {
                        try {
                            session.sendText(message, null);
                        } catch (Exception e) {
                            // Remove dead session
                            sessions.remove(session);
                        }
                    });
        }

        public void broadcastNowPlaying(NowPlaying nowPlaying) {
            if (nowPlaying.getSong() != null) {
                Map<String, Object> response = wsContent.getNowPlaying(nowPlaying);// getMap(nowPlaying.getSong());
                if(response != null) {
                    String jsonResponse = gson.toJson(response);
                    broadcast(jsonResponse);
                   // sessions.forEach(endpoint -> endpoint.sendText(jsonResponse, null));
                }
            }
        }
    }
}

