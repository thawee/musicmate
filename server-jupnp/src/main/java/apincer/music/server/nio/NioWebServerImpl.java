package apincer.music.server.nio;

import static apincer.music.core.http.NioHttpServer.HTTP_INTERNAL_ERROR;
import static apincer.music.core.http.NioHttpServer.HTTP_NOT_FOUND;
import static apincer.music.core.http.NioHttpServer.WEBSOCKET_CLOSE_GOING_AWAY;
import static apincer.music.core.http.NioHttpServer.WEBSOCKET_CLOSE_SERVER_FULL;
import static apincer.music.server.jupnp.transport.DLNAHeaderHelper.getDLNAContentFeatures;

import android.content.Context;
import android.util.Log;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.File;
import java.io.IOException;
import java.net.InetAddress;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArraySet;

import apincer.music.core.http.NioHttpServer;
import apincer.music.core.http.RateLimitingHandler;
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
        addLibInfo("SonicNIO",  "");
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
        NioHttpServer.HttpResponse response = server.createFileResponse(filePath, request);
        response.addHeader("Content-Type", content.getContentType());
        response.addHeader("Cache-Control", "public, max-age=604800");
        return response;
    }

    private NioHttpServer.HttpResponse createSongResponse(ContentHolder content, NioHttpServer.HttpRequest request) throws IOException {
        Track song = content.getTrack();
        if (song == null) return createErrorResponse(HTTP_NOT_FOUND, MSG_SONG_NOT_FOUND);
        if (song.getPath() == null) return createErrorResponse(HTTP_NOT_FOUND, MSG_SONG_NOT_FOUND);
        File audioFile = new File(song.getPath());
        if (!audioFile.exists() || !audioFile.canRead()) return createErrorResponse(HTTP_NOT_FOUND, MSG_FILE_NOT_ACCESSIBLE);
        NioHttpServer.HttpResponse response = server.createFileResponse(audioFile, request);
        //response.addHeader("Cache-Control", "no-store, no-transform, max-age=0");
        response.addHeader("Cache-Control", "no-cache");
        //response.addHeader("Pragma", "no-cache");
        response.addHeader("transferMode.dlna.org", "Streaming");
        response.addHeader("contentFeatures.dlna.org", getDLNAContentFeatures(song));
        response.addHeader("Content-Type", content.getContentType());
        response.addHeader("Server", getServerSignature());
        response.addHeader("Date", getCachedDate());
      //  addAudiophileHeaders(response, song);
        return response;
    }

    private String getCachedDate() {
        long now = System.currentTimeMillis();
        return formatDate(now);
    }

    private NioHttpServer.HttpResponse createResourceResponse(ContentHolder content, NioHttpServer.HttpRequest request) throws IOException {
        if (content == null || content.getFilePath() == null) return createErrorResponse(HTTP_NOT_FOUND, "Resource not found");
        NioHttpServer.HttpResponse response = server.createFileResponse(new File(content.getFilePath()), request);
        response.addHeader("Content-Type", content.getContentType());
        response.addHeader("Cache-Control", "public, max-age=604800");
        return response;
    }

    private NioHttpServer.HttpResponse createErrorResponse(int code, String message) {
        return new NioHttpServer.HttpResponse().setStatus(code, message).setBody(message.getBytes()).addHeader("Content-Type", "text/plain; charset=utf-8").addHeader("Connection", "close");
    }

    private void addAudiophileHeaders(NioHttpServer.HttpResponse response, Track tag) {
        if (tag.getAudioSampleRate() > 0) response.addHeader("X-Audio-Sample-Rate", tag.getAudioSampleRate() + " Hz");
        if (tag.getAudioBitsDepth() > 0) response.addHeader("X-Audio-Bit-Depth", tag.getAudioBitsDepth() + " bit");
        if (tag.getAudioBitRate() > 0) response.addHeader("X-Audio-Bitrate", tag.getAudioBitRate()/1000 + " kbps");
        if (TagUtils.getChannels(tag) > 0) response.addHeader("X-Audio-Channels", String.valueOf(TagUtils.getChannels(tag)));
        response.addHeader("X-Audio-Format", Objects.toString(tag.getFileType(), ""));
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

    private class WebSocketHandlerImpl extends WebSocketContent implements NioHttpServer.WebSocketHandler {
        private final CopyOnWriteArraySet<NioHttpServer.WebSocketConnection> sessions = new CopyOnWriteArraySet<>();
        private static final int MAX_SESSIONS = 100;
        private static final ObjectMapper MAPPER = new ObjectMapper().setDefaultPropertyInclusion(JsonInclude.Include.ALWAYS);

        @Override
        protected void broadcastMessage(String jsonResponse) {
            if(sessions ==null) return;
            for (NioHttpServer.WebSocketConnection session : sessions) {
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
        public void onOpen(NioHttpServer.WebSocketConnection connection) {
            if (sessions.size() >= MAX_SESSIONS) { connection.close(WEBSOCKET_CLOSE_SERVER_FULL, "Server full"); return; }
            sessions.add(connection);
            for (Map<String, Object> message : getWelcomeMessages()) { sendMessage(connection, message); }
        }

        private void sendMessage(NioHttpServer.WebSocketConnection connection, Map<String, Object> response) {
            if (response != null) {
                try { connection.send(MAPPER.writeValueAsString(response)); } catch (JsonProcessingException e) { Log.e(TAG, "Error serializing response", e); }
            }
        }

        @Override
        public void onMessage(NioHttpServer.WebSocketConnection connection, String message) {
            try {
                Map<String, Object> messageMap = MAPPER.readValue(message, Map.class);
                String command = String.valueOf(messageMap.getOrDefault("command", ""));
                if (!command.isEmpty()) sendMessage(connection, handleCommand(command, messageMap));
            } catch (Exception e) { Log.e(TAG, "Error processing WS message", e); }
        }

        @Override
        public void onMessage(NioHttpServer.WebSocketConnection connection, byte[] message) {}

        @Override
        public void onClose(NioHttpServer.WebSocketConnection connection, int code, String reason) { sessions.remove(connection); }

        @Override
        public void onError(NioHttpServer.WebSocketConnection connection, Exception ex) { Log.e(TAG, "WS Error", ex); }

        public void shutdown() {
            for (NioHttpServer.WebSocketConnection session : sessions) { session.close(WEBSOCKET_CLOSE_GOING_AWAY, "Server shutting down"); }
            sessions.clear();
        }
    }
}
