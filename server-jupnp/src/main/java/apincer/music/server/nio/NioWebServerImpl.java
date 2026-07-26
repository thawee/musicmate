package apincer.music.server.nio;

import static apincer.music.core.http.NioHttpServer.HTTP_INTERNAL_ERROR;
import static apincer.music.core.http.NioHttpServer.HTTP_NOT_FOUND;
import static apincer.music.core.http.WebSocket.CLOSE_GOING_AWAY;
import static apincer.music.core.http.WebSocket.CLOSE_SERVER_FULL;
import static apincer.music.server.jupnp.transport.DLNAHeaderHelper.getDLNAContentFeatures;

import android.content.Context;
import android.util.Log;



import java.io.File;
import java.io.IOException;
import java.net.InetAddress;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArraySet;

import apincer.music.core.http.NioHttpServer;
import apincer.music.core.http.RateLimitingHandler;
import apincer.music.core.http.WebSocket;
import apincer.music.core.model.Track;
import apincer.music.core.repository.FileRepository;
import apincer.music.core.repository.TagRepository;
import apincer.music.core.server.BaseServer;
import apincer.music.core.server.ContentHolder;
import apincer.music.core.server.spi.WebServer;
import apincer.music.core.utils.TagUtils;

public class NioWebServerImpl extends BaseServer implements WebServer {
    private static final String TAG = "NioWebServer";

    private static final String MSG_SONG_NOT_FOUND = "Song not found";
    private static final String MSG_FILE_NOT_ACCESSIBLE = "File not accessible";

    private static final int MAX_THREADS = 8;
    private static final int IDLE_TIMEOUT = 300_000;
    private static final int READ_BUFFER_SIZE = 8192;

    private Thread serverThread;
    private final Object serverLock = new Object();
    private NioHttpServer server;
    private WebSocketHandlerImpl wsHandler;
    //private final ProfileManager profileManager;

    public NioWebServerImpl(Context context, FileRepository fileRepos, TagRepository tagRepos) {
        super(context, fileRepos, tagRepos);
        addLibInfo("SonicNIO",  "2.2");
       // this.profileManager = new ProfileManager(context, calculateBufferSize(context));
    }

    @Override
    public void restartServer(InetAddress bindAddress) {
        synchronized (serverLock) {
            stopServer();
            try { Thread.sleep(200); } catch (InterruptedException ignored) {}
            initServer(bindAddress);
        }
    }

    public void initServer(InetAddress bindAddress) {
        synchronized (serverLock) {
            if (serverThread != null && serverThread.isAlive()) return;
            serverThread = new Thread(() -> {
                try {
                    server = new NioHttpServer(WEB_SERVER_PORT);

                    server.setMaxThread(MAX_THREADS);
                    server.setClientReadBufferSize(READ_BUFFER_SIZE);
                    server.setKeepAliveTimeout(IDLE_TIMEOUT);
                    wsHandler = new WebSocketHandlerImpl();
                    server.registerWebSocketHandler(wsHandler);
                    NioHttpServer.Handler rateLimiter = new RateLimitingHandler(50, this::handleRequest);
                    server.registerHttpHandler(rateLimiter);
                    server.run();
                } catch (Exception e) { Log.e(TAG, "Failed to start WebServer", e); }
            });
            serverThread.setName("nio-webserver-runner");
            serverThread.start();
        }
    }

    private NioHttpServer.HttpResponse handleRequest(NioHttpServer.HttpRequest request) {

        String rawUri = request.getPath();
        String remoteHost = request.getRemoteHost();
        String userAgent = request.getHeader("User-Agent", "Unknown");
        try {
            ContentHolder contentHolder = resolveRequest(rawUri, remoteHost, userAgent);
            if (contentHolder.isImage()) {
                return createAlbumArtResponse(contentHolder, request);
            } else if (contentHolder.isMedia()) {
                return createSongResponse(contentHolder, request);
            } else {
                return createResourceResponse(contentHolder, request);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error handling HTTP request: " + rawUri, e);
            return createErrorResponse(HTTP_INTERNAL_ERROR, "Internal Server Error");
        }
    }

    private NioHttpServer.HttpResponse createAlbumArtResponse(ContentHolder content, NioHttpServer.HttpRequest request) throws IOException {
        File filePath;
        if (content == null || content.getFilePath()==null) {
            filePath = getDefaultAlbumArt();
        }else {
            filePath = new File(content.getFilePath());
        }
        if (filePath == null || !filePath.exists()) return createErrorResponse(HTTP_NOT_FOUND, "Art not found");

        String contentType = content != null ? content.getContentType() : null;

        NioHttpServer.HttpResponse response = server.createFileResponse(filePath, request);
        if (contentType != null) {
            response.addHeader("Content-Type", contentType);
        }
        response.addHeader("Cache-Control", "public, max-age=604800");
        return response;
    }

    private NioHttpServer.HttpResponse createSongResponse(ContentHolder content, NioHttpServer.HttpRequest request) throws IOException {
        Track song = content.getTrack();
        if (song == null) return createErrorResponse(HTTP_NOT_FOUND, MSG_SONG_NOT_FOUND);
        if (song.getPath() == null) return createErrorResponse(HTTP_NOT_FOUND, MSG_SONG_NOT_FOUND);
        File audioFile = new File(song.getPath());
        if (!audioFile.exists() || !audioFile.canRead()) return createErrorResponse(HTTP_NOT_FOUND, MSG_FILE_NOT_ACCESSIBLE);

        // Prepare headers first to avoid exceptions after response creation
        String dlnaFeatures = getDLNAContentFeatures(song);
        String contentType = content.getContentType();
        String serverSignature = getServerSignature();
        String cachedDate = getCachedDate();

        NioHttpServer.HttpResponse response = server.createFileResponse(audioFile, request);
        response.addHeader("Cache-Control", "no-cache");
        response.addHeader("transferMode.dlna.org", "Streaming");
        if (dlnaFeatures != null) {
            response.addHeader("contentFeatures.dlna.org", dlnaFeatures);
        }
        if (contentType != null) {
            response.addHeader("Content-Type", contentType);
        }
        if (serverSignature != null) {
            response.addHeader("Server", serverSignature);
        }
        if (cachedDate != null) {
            response.addHeader("Date", cachedDate);
        }
        return response;
    }

    private String getCachedDate() {
        long now = System.currentTimeMillis();
        return formatDate(now);
    }

    private NioHttpServer.HttpResponse createResourceResponse(ContentHolder content, NioHttpServer.HttpRequest request) throws IOException {
        if (content == null || content.getFilePath() == null) return createErrorResponse(HTTP_NOT_FOUND, "Resource not found");
        File file = new File(content.getFilePath());
        String contentType = content.getContentType();

        NioHttpServer.HttpResponse response = server.createFileResponse(file, request);
        if (contentType != null) {
            response.addHeader("Content-Type", contentType);
        }
        response.addHeader("Cache-Control", "public, max-age=604800");
        return response;
    }

    private NioHttpServer.HttpResponse createErrorResponse(int code, String message) {
        return new NioHttpServer.HttpResponse().setStatus(code, message).setBody(message.getBytes()).addHeader("Content-Type", "text/plain; charset=utf-8").addHeader("Connection", "close");
    }


    public void stopServer() {
        synchronized (serverLock) {
            if (server != null) {
                if (wsHandler != null) wsHandler.shutdown();
                server.stop();
                if (serverThread != null) {
                    try { serverThread.join(2000); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
                }
                server = null;
                serverThread = null;
                wsHandler = null;
            }
        }
    }

    @Override
    public int getListenPort() { return WEB_SERVER_PORT; }

    private class WebSocketHandlerImpl extends WebSocketContent implements WebSocket.Handler {
        private final CopyOnWriteArraySet<WebSocket.Connection> sessions = new CopyOnWriteArraySet<>();
        private static final int MAX_SESSIONS = 100;

        @Override
        protected void broadcastMessage(String jsonResponse) {
            if(sessions ==null) return;
            for (WebSocket.Connection session : sessions) {
                if (session.isClosed()) {
                    sessions.remove(session);
                    continue;
                }
                session.send(jsonResponse);
            }
        }

        @Override
        public String getNamespace() { return CONTEXT_PATH_WEBSOCKET; }

        @Override
        public void onOpen(WebSocket.Connection connection) {
            if (sessions.size() >= MAX_SESSIONS) { connection.close(WebSocket.CLOSE_SERVER_FULL, "Server full"); return; }
            sessions.add(connection);
            for (Map<String, Object> message : getWelcomeMessages()) { sendMessage(connection, message); }
        }

        private void sendMessage(WebSocket.Connection connection, Map<String, Object> response) {
            if (response != null) {
                try { connection.send(apincer.music.core.utils.JsonUtils.toJson(response)); } catch (Exception e) { Log.e(TAG, "Error serializing response", e); }
            }
        }

        @Override
        public void onMessage(WebSocket.Connection connection, String message) {
            try {
                Map<String, Object> messageMap = (Map<String, Object>) (Map<?, ?>) apincer.music.core.utils.JsonUtils.toMap(message);
                String command = String.valueOf(messageMap.getOrDefault("command", ""));
                Map<String, Object> response = handleCommand(command, messageMap);
                sendMessage(connection, response);
            } catch (Exception e) {
                Log.e(TAG, "Error handling WebSocket message", e);
            }
        } @Override
        public void onMessage(WebSocket.Connection connection, byte[] message) {}

        @Override
        public void onClose(WebSocket.Connection connection, int code, String reason) { sessions.remove(connection); }

        @Override
        public void onError(WebSocket.Connection connection, Exception ex) { Log.e(TAG, "WS Error", ex); }

        public void shutdown() {
            for (WebSocket.Connection session : sessions) { session.close(WebSocket.CLOSE_GOING_AWAY, "Server shutting down"); }
            sessions.clear();
        }
    }
}
