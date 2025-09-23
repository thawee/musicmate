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
import org.eclipse.jetty.server.Connector;
import org.eclipse.jetty.server.Handler;
import org.eclipse.jetty.server.HttpConfiguration;
import org.eclipse.jetty.server.HttpConnectionFactory;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.Response;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.server.handler.ContextHandler;
import org.eclipse.jetty.server.handler.ContextHandlerCollection;
import org.eclipse.jetty.server.handler.PathMappingsHandler;
import org.eclipse.jetty.server.handler.ResourceHandler;
import org.eclipse.jetty.util.Callback;
import org.eclipse.jetty.util.resource.Resource;
import org.eclipse.jetty.util.resource.ResourceFactory;
import org.eclipse.jetty.util.thread.QueuedThreadPool;
import org.eclipse.jetty.websocket.api.Session;
import org.eclipse.jetty.websocket.server.ServerWebSocketContainer;
import org.eclipse.jetty.websocket.server.WebSocketUpgradeHandler;
import org.jupnp.transport.Router;
import org.jupnp.transport.spi.InitializationException;

import java.io.File;
import java.io.IOException;
import java.net.InetAddress;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Supplier;

import apincer.android.mmate.MusixMateApp;
import apincer.android.mmate.dlna.content.WebSocketContent;
import apincer.android.mmate.dlna.transport.StreamServerConfigurationImpl;
import apincer.android.mmate.dlna.transport.StreamServerImpl;
import apincer.android.mmate.playback.NowPlaying;
import apincer.android.mmate.playback.PlaybackService;
import apincer.android.mmate.repository.FileRepository;
import apincer.android.mmate.repository.database.MusicTag;
import apincer.android.mmate.utils.ApplicationUtils;
import apincer.android.mmate.utils.MimeTypeUtils;

public class JettyWebServerImpl extends StreamServerImpl.StreamServer {
    private static final String TAG = "JettyWebServer";
    private Server server;
    private final MusicWebSocketManager webSocketManager;

    public JettyWebServerImpl(Context context, Router router, StreamServerConfigurationImpl configuration) {
        super(context, router, configuration);
        this.webSocketManager = new MusicWebSocketManager(context);
    }

    @Override
    public void initServer(InetAddress bindAddress) throws InitializationException {
        Thread serverThread = new Thread(() -> {
            try {
                Log.i(TAG, "Starting Web Server (Jetty) on " + bindAddress.getHostAddress() + ":" + WEB_SERVER_PORT);

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
                server.setConnectors(new Connector[]{connector});

                // 1. WebSocket Handler on /ws context
                ContextHandler wsContext = new ContextHandler("/ws");
                WebSocketUpgradeHandler wsUpgradeHandler = WebSocketUpgradeHandler.from(server, wsContext, container -> {
                    container.setMaxTextMessageSize(128 * 1024);
                    container.addMapping("/", (rq, rs, cb) -> webSocketManager.createEndpoint());
                });
                wsContext.setHandler(wsUpgradeHandler);
                //ServerWebSocketContainer.ensure(server, wsContext);

                // 2. Album Art Handler on /coverart and static files (HTML, CSS, JS) on root context
                AliasCheck aliasCheck = (pathInContext, resource) -> true;
                ResourceHandler webHandler = new WebContentHandler();
                webHandler.setBaseResource(ResourceFactory.of(webHandler).newResource("/"));
                webHandler.setDirAllowed(false);
                webHandler.setCacheControl("public, max-age=86400");
                webHandler.setServer(server);
                ContextHandler webContext = new ContextHandler("/");
                webContext.setAliasChecks(Collections.singletonList(aliasCheck)); // bypass alias check
                webContext.setHandler(webHandler);

                /*
                ResourceFactory resourceFactory = ResourceFactory.of(server);
                Resource rootResourceDir = resourceFactory.newResource(Paths.get("/var/www/html"));
                ResourceHandler rootResourceHandler = new ResourceHandler();
                rootResourceHandler.setBaseResource(rootResourceDir);
                rootResourceHandler.setDirAllowed(false);
                rootResourceHandler.setWelcomeFiles("index.html"); */

                // 4. Combine all handlers using ContextHandlerCollection
              //  ContextHandlerCollection handlers = new ContextHandlerCollection(wsContext, webContext);
               // server.setHandler(handlers);

                PathMappingsHandler pathMappingsHandler = new PathMappingsHandler(true);
               // pathMappingsHandler.addMapping(PathSpec.from("/ws/*"), wsUpgradeHandler);
                pathMappingsHandler.addMapping(PathSpec.from("/"), webHandler);
                server.setHandler(pathMappingsHandler);

                server.start();
                Log.i(TAG, "Web Server (Jetty) started successfully.");
                server.join();

            } catch (Exception e) {
                Log.e(TAG, "Failed to start or run Web server (Jetty)", e);
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
        Log.i(TAG, "Stopping Web Server (Jetty)");
        try {
            if (webSocketManager != null) {
                webSocketManager.stop();
            }
            if (server != null && server.isRunning()) {
                server.stop();
            }
        } catch (Exception e) {
            Log.e(TAG, "Error stopping Web server (Jetty)", e);
        }
    }

    @Override
    protected String getServerVersion() {
        return "Jetty/12.1.1";
    }

    private class WebContentHandler extends ResourceHandler {
        //private final LruCache<String, > albumArtCache = new LruCache<>(100);
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
            File filePath = null;
            if (requestUri.startsWith("/coverart/")) {
                // Log.d(TAG, "Processing album art request: " + uri);
                filePath =  handleAlbumArt(request, requestUri);
            }else {
                filePath = getWebResource(request, requestUri);
            }

            if (filePath == null) {
                Log.w(TAG, "Content not found for URI: " + request.getHttpURI().getPath());
                return super.handle(request, response, callback);
            }

            if (!filePath.exists() || !filePath.canRead()) {
                Log.e(TAG, "Audio file not accessible: " + filePath);
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
           // MusicTag tag = MusixMateApp.getInstance().getOrmLite().findByAlbumUniqueKey(albumUniqueKey);
           // if (tag != null) {
                return FileRepository.getCoverArt(albumUniqueKey);
           // }
           // return null;
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

    private class WSContentHandler extends Handler.Abstract {
        ServerWebSocketContainer container;
        public WSContentHandler() {
           // this.container = container;
        }

        @Override
        public boolean handle(Request request, Response response, Callback callback) throws Exception {
            // Retrieve the ServerWebSocketContainer.
            ServerWebSocketContainer container = ServerWebSocketContainer.get(request.getContext());

            // Verify special conditions for which a request should be upgraded to WebSocket.
           // String pathInContext = Request.getPathInContext(request);
           // if (pathInContext.startsWith("/")) {
                try {
                    // This is a WebSocket upgrade request, perform a direct upgrade.
                    boolean upgraded = container.upgrade((rq, rs, cb) -> new MusicMateEndpoint(webSocketManager), request, response, callback);
                    if (upgraded)
                        return true;
                    // This was supposed to be a WebSocket upgrade request, but something went wrong.
                    Response.writeError(request, response, callback, HttpStatus.UPGRADE_REQUIRED_426);
                    return true;
                }
                catch (Exception x)
                {
                    Response.writeError(request, response, callback, HttpStatus.UPGRADE_REQUIRED_426, "failed to upgrade", x);
                    return true;
                }
           // }else {
                // Handle a normal HTTP request.
           //     response.setStatus(HttpStatus.OK_200);
           //     callback.succeeded();
           //     return true;
           // }
        }
    }
}

// =======================================================================================
// WebSocket Manager (Singleton, manages all endpoints)
// =======================================================================================
class MusicWebSocketManager {
    private static final String TAG = "MusicWebSocketManager";
    private final Context context;
    private final Gson gson = new Gson();
    private final List<MusicMateEndpoint> endpoints = new CopyOnWriteArrayList<>();
    private PlaybackService playbackService;
    private boolean isPlaybackServiceBound = false;
    private final WebSocketContent wsContent = new WebSocketContent();

    private final ServiceConnection serviceConnection = new ServiceConnection() {
        @SuppressLint("CheckResult")
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            PlaybackService.PlaybackServiceBinder binder = (PlaybackService.PlaybackServiceBinder) service;
            playbackService = binder.getService();
            isPlaybackServiceBound = true;
            wsContent.setPlaybackService(playbackService);
            if (playbackService != null && playbackService.getNowPlayingSubject() != null) {
                playbackService.getNowPlayingSubject().subscribe(nowPlaying -> broadcastNowPlaying(nowPlaying));
                playbackService.getPlayingQueueSubject().subscribe(nowPlaying -> broadcastPlayingQueue(nowPlaying));
            }
            Log.i(TAG, "PlaybackService bound successfully.");
        }
        @Override
        public void onServiceDisconnected(ComponentName name) {
            isPlaybackServiceBound = false;
            playbackService = null;
            Log.w(TAG, "PlaybackService disconnected unexpectedly.");
        }
    };

    private void broadcastPlayingQueue(List<MusicTag> playingQueue) {
        // Sends the current queue state to ALL connected clients
        Map<String, Object> response = wsContent.getPlayingQueue(playingQueue);
        if(response != null) {
            String jsonResponse = gson.toJson(response);
            endpoints.forEach(endpoint -> endpoint.sendMessage(jsonResponse));
        }
    }

    public MusicWebSocketManager(Context context) {
        this.context = context;
        Intent intent = new Intent(context, PlaybackService.class);
        context.bindService(intent, serviceConnection, BIND_AUTO_CREATE);
    }

    public Object createEndpoint() {
        return new MusicMateEndpoint(this);
    }

    public void register(MusicMateEndpoint endpoint) {
        endpoints.add(endpoint);
        Log.i(TAG, "Client connected: " + endpoint.getRemoteAddress());
    }

    public void unregister(MusicMateEndpoint endpoint) {
        endpoints.remove(endpoint);
        Log.i(TAG, "Client disconnected: " + endpoint.getRemoteAddress());
    }

    public void stop() {
        if (isPlaybackServiceBound) {
            context.unbindService(serviceConnection);
            isPlaybackServiceBound = false;
        }
    }

    public void processMessage(MusicMateEndpoint endpoint, String messageText) {
        Log.d(TAG, "Received message: " + messageText);
        try {
            Map<String, Object> message = gson.fromJson(messageText, Map.class);
            String command = message.getOrDefault("command", "").toString();

            // ... (All your case statements would go here) ...
            Map<String, Object> response = wsContent.handleCommand(command, message);

            if(response != null) {
                endpoint.sendMessage(gson.toJson(response));
            }
        } catch (Exception e) {
            Log.e(TAG, "Error processing WebSocket message", e);
        }
    }

    private void broadcastNowPlaying(NowPlaying nowPlaying) {
        if (nowPlaying.getSong() != null) {
            Map<String, Object> response = wsContent.getNowPlaying(nowPlaying);// getMap(nowPlaying.getSong());
            if(response != null) {
                String jsonResponse = gson.toJson(response);
                endpoints.forEach(endpoint -> endpoint.sendMessage(jsonResponse));
            }
        }
    }

}

// =======================================================================================
// WebSocket Endpoint (A new instance is created for each connection)
// =======================================================================================
class MusicMateEndpoint {
    private final MusicWebSocketManager manager;
    private Session session;

    public MusicMateEndpoint(MusicWebSocketManager manager) {
        this.manager = manager;
    }

    public void onOpen(Session session) {
        this.session = session;
        this.manager.register(this);
    }

    public void onClose(int statusCode, String reason) {
        this.manager.unregister(this);
    }

    public void onMessage(String message) {
        this.manager.processMessage(this, message);
    }

    public void sendMessage(String jsonMessage) {
        if (session != null && session.isOpen()) {
            session.sendText(jsonMessage, org.eclipse.jetty.websocket.api.Callback.NOOP);
        }
    }

    public String getRemoteAddress() {
        return (session != null) ? session.getRemoteSocketAddress().toString(): "Unknown"; // .getRemoteAddress().toString() : "Unknown";
    }
}

