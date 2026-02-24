package apincer.android.jupnp.transport.undertow;

import static apincer.music.core.utils.StringUtils.isEmpty;

import android.content.Context;
import android.util.Log;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.undertow.Handlers;
import io.undertow.Undertow;
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

    private Undertow server;
    private final ResourceHandler resourceHandler;
    private final CopyOnWriteArraySet<WebSocketChannel> activeWebsocketSessions = new CopyOnWriteArraySet<>();

    public WebServerImpl(Context context, FileRepository fileRepos, TagRepository tagRepos) {
        super(context, fileRepos, tagRepos);
        addLibInfo("Undertow", "");

        // Undertow's resource handler to serve actual files.
        // Paths.get("/") acts as the root file system for Android
        // need to  set followLinks on resourceManager
        String[] safePaths = new String[]{"/storage","/data"};
        resourceHandler = new ResourceHandler(new PathResourceManager(Paths.get("/"), 10485760, true, true, safePaths))
                .setDirectoryListingEnabled(false);
    }

    @Override
    public void initServer(InetAddress bindAddress) throws Exception {
        Thread thread = new Thread(new Runnable() {
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

                    server = Undertow.builder()
                            .addHttpListener(WEB_SERVER_PORT, bindAddress.getHostAddress())
                            .setIoThreads(2)
                            .setWorkerThreads(4)
                            .setByteBufferPool(new DefaultByteBufferPool(false, 1024 * 16))
                            .setHandler(pathHandler)
                            .build();

                    server.start();
                } catch (Exception ex) {
                    throw new InitializationException("Could not initialize " + getClass().getSimpleName() + ": " + ex, ex);
                }
            }
        });

        thread.start();
    }

    private void serveContent(HttpServerExchange exchange, ContentHolder contentHolder) throws Exception {
        if (contentHolder != null && contentHolder.getFilePath() != null && new File(contentHolder.getFilePath()).exists()) {
            exchange.getResponseHeaders().put(Headers.CONTENT_TYPE, contentHolder.getContentType());
            exchange.getResponseHeaders().put(Headers.ACCEPT_RANGES, "bytes");

            if (contentHolder.getDlnaContentFeatures() != null) {
                exchange.getResponseHeaders().put(new HttpString("transferMode.dlna.org"), "Streaming");
                exchange.getResponseHeaders().put(new HttpString("contentFeatures.dlna.org"), contentHolder.getDlnaContentFeatures());
            }

            exchange.setRelativePath(contentHolder.getFilePath()); // Because ResourceManager base is "/"
            resourceHandler.handleRequest(exchange);
        } else {
            exchange.setStatusCode(404);
            exchange.getResponseSender().send("<html><body><h1>File not found</h1></body></html>");
        }
    }

    @Override
    public void stopServer() {
        Log.v(TAG, "Shutting down Undertow Content Server");

        for (WebSocketChannel session : activeWebsocketSessions) {
            try {
                if (session.isOpen()) session.sendClose();
            } catch (Exception ignored) {}
        }
        activeWebsocketSessions.clear();

        if (server != null) {
            try {
                server.stop();
            } catch (Exception e) {
                Log.d(TAG, "got exception on content server stop ", e);
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