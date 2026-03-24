package apincer.music.server.nio;

import static apincer.music.core.http.NioHttpServer.HTTP_BAD_REQUEST;
import static apincer.music.core.http.NioHttpServer.HTTP_INTERNAL_ERROR;
import static apincer.music.core.http.NioHttpServer.HTTP_NOT_FOUND;
import static apincer.music.core.http.NioHttpServer.WEBSOCKET_CLOSE_GOING_AWAY;
import static apincer.music.core.http.NioHttpServer.WEBSOCKET_CLOSE_SERVER_FULL;
import static apincer.music.server.jupnp.transport.DLNAHeaderHelper.getDLNAContentFeatures;
import static apincer.music.core.utils.StringUtils.isEmpty;

import android.content.Context;
import android.util.Log;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.File;
import java.io.IOException;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import apincer.music.core.database.MusicTag;
import apincer.music.core.http.NioHttpServer;
import apincer.music.core.http.RateLimitingHandler;
import apincer.music.core.repository.FileRepository;
import apincer.music.core.repository.TagRepository;
import apincer.music.core.server.BaseServer;
import apincer.music.core.server.spi.WebServer;
import apincer.music.core.utils.TagUtils;

public class NioWebServerImpl extends BaseServer implements WebServer {
    private static final String TAG = "NioWebServer";

    private static final String DEFAULT_PATH = "/index.html";
    private static final String MSG_SONG_NOT_FOUND = "Song not found";
    private static final String MSG_FILE_NOT_ACCESSIBLE = "File not accessible";

    // Browsers open ~6 connections. 4 is too low and causes hanging.
    private static final int MAX_THREADS = 8; //16;
    private static final int IDLE_TIMEOUT = 300_000; // 5 minutes
    private static final int READ_BUFFER_SIZE = 8192;

    private Thread serverThread;
    private final Object serverLock = new Object();
    private NioHttpServer server;
    private WebSocketHandlerImpl wsHandler;

    // Executor for background tasks (logging/notifications) to not block IO threads
    private final ExecutorService backgroundExecutor = Executors.newFixedThreadPool(3); //Executors.newSingleThreadExecutor();

    public NioWebServerImpl(Context context, FileRepository fileRepos, TagRepository tagRepos) {
        super(context, fileRepos, tagRepos);
        addLibInfo("SonicNIO",  "2.2");
    }

    @Override
    public void restartServer(InetAddress bindAddress) {
        synchronized (serverLock) {
            Log.d(TAG, "Restarting SonicNIO Server...");

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

    public void initServer(InetAddress bindAddress) {
        if (serverThread != null && serverThread.isAlive()) {
            Log.w(TAG, TAG + " - initServer() called, but server is already running.");
            return;
        }

        serverThread = new Thread(() -> {
            try {
                server = new NioHttpServer(WEB_SERVER_PORT);
                server.setMaxThread(MAX_THREADS);
                server.setClientReadBufferSize(READ_BUFFER_SIZE);
                server.setKeepAliveTimeout(IDLE_TIMEOUT);

                wsHandler = new WebSocketHandlerImpl();
                server.registerWebSocketHandler(wsHandler);

                // 1. Your existing core router (the bottom of the chain)
                NioHttpServer.Handler baseRouter = this::handleRequest;

                // 2. Wrap the router in the Rate Limiter (Max 50 requests/sec per IP)
                NioHttpServer.Handler rateLimiter = new RateLimitingHandler(50, baseRouter);

                // 3. Register the outermost layer as the fallback handler
                server.registerHttpHandler(rateLimiter);

                Log.i(TAG, TAG + " - SonicNIO WebServer running on " + bindAddress.getHostAddress() + ":" + WEB_SERVER_PORT);
                server.run();

            } catch (Exception e) {
                Log.e(TAG, TAG + " - Failed to start WebServer", e);
            }
        });

        serverThread.setName("nio-webserver-runner");
        serverThread.start();
    }

    private NioHttpServer.HttpResponse handleRequest(NioHttpServer.HttpRequest request) {
        String rawUri = request.getPath();
        try {
            // Path Traversal Protection & Normalization
            String requestUri = normalizePath(rawUri);
            if (requestUri == null) {
                Log.w(TAG, "Security alert: Blocked path traversal attempt: " + rawUri);
                return createErrorResponse(HTTP_BAD_REQUEST, "Invalid path requested");
            }

            // Restore leading slash for internal routing logic
            requestUri = "/" + requestUri;

            if (requestUri.equals("/")) {
                requestUri = DEFAULT_PATH;
            }

            if (requestUri.startsWith(CONTEXT_PATH_COVERART)) {
                File filePath = getAlbumArt(requestUri);
                return createAlbumArtResponse(filePath, request);
            } else if (requestUri.startsWith(CONTEXT_PATH_MUSIC)) {
                MusicTag song = getSong(requestUri);
                String remoteAddr = request.getRemoteHost();
                String userAgent = request.getHeader("User-Agent", "Unknown");
                notifyPlayback(remoteAddr, userAgent, song);
                return createSongResponse(song, request);
            } else {
                File filePath = getWebResource(requestUri);
                return createResourceResponse(filePath, request);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error handling HTTP request: " + rawUri, e);
            return createErrorResponse(HTTP_INTERNAL_ERROR, "Internal Server Error");
        }
    }

    private NioHttpServer.HttpResponse createAlbumArtResponse(File filePath, NioHttpServer.HttpRequest request) throws IOException {
        if (filePath == null || !filePath.exists() || !filePath.canRead()) {
            // Try default art immediately if specific failed
            filePath = getDefaultAlbumArt();
        }

        // Final check on default art
        if (filePath == null || !filePath.exists()) {
            return createErrorResponse(HTTP_NOT_FOUND, "Art not found");
        }

        try {
            NioHttpServer.HttpResponse response = server.createFileResponse(filePath, request);
            // ARTWORK & WEB UI: Cache aggressively for 7 days
            response.addHeader("Cache-Control", "public, max-age=604800");
            return response;
        } catch (IOException e) {
            Log.e(TAG, "IO error streaming album art", e);
            return createErrorResponse(HTTP_INTERNAL_ERROR, "Streaming error");
        }
    }

    private NioHttpServer.HttpResponse createSongResponse(MusicTag song, NioHttpServer.HttpRequest request) {
        if (song == null) {
            Log.w(TAG, MSG_SONG_NOT_FOUND + ": " + request.getPath());
            return createErrorResponse(HTTP_NOT_FOUND, MSG_SONG_NOT_FOUND);
        }
        File audioFile = new File(song.getPath());
        if (!audioFile.exists() || !audioFile.canRead()) {
            Log.w(TAG, MSG_FILE_NOT_ACCESSIBLE + ": " + audioFile.getAbsolutePath());
            return createErrorResponse(HTTP_NOT_FOUND, MSG_FILE_NOT_ACCESSIBLE);
        }

        try {
            NioHttpServer.HttpResponse response = server.createFileResponse(audioFile, request);
            addMusicStreamingHeaders(response, song);
            return response;
        } catch (IOException e) {
            Log.e(TAG, "Error creating song file response", e);
            return createErrorResponse(HTTP_INTERNAL_ERROR, "Error streaming audio file");
        }
    }

    private NioHttpServer.HttpResponse createResourceResponse(File filePath, NioHttpServer.HttpRequest request) throws IOException {
        if (filePath == null || !filePath.exists() || !filePath.canRead()) {
            return createErrorResponse(HTTP_NOT_FOUND, "Resource not found");
        }
        
        try {
            NioHttpServer.HttpResponse response = server.createFileResponse(filePath, request);
            // ARTWORK & WEB UI: Cache aggressively for 7 days
            response.addHeader("Cache-Control", "public, max-age=604800");
            return response;
        } catch (IOException e) {
            Log.e(TAG, "IO error streaming web resource", e);
            return createErrorResponse(HTTP_INTERNAL_ERROR, "Streaming error");
        }
    }

    private NioHttpServer.HttpResponse createErrorResponse(int code, String message) {
        return new NioHttpServer.HttpResponse()
                .setStatus(code, message)
                .setBody(message.getBytes())
                .addHeader("Content-Type", "text/plain; charset=utf-8")
                .addHeader("Connection", "close"); // Close connection on errors for safety
    }

    private void addMusicStreamingHeaders(NioHttpServer.HttpResponse response, MusicTag song) {
        // MUSIC: Force volatile streaming, protect player memory
        response.addHeader("Cache-Control", "no-store, no-transform, max-age=0");
        response.addHeader("Pragma", "no-cache"); // Legacy support for older DLNA gear
        response.addHeader("transferMode.dlna.org", "Streaming");
        response.addHeader("contentFeatures.dlna.org", getDLNAContentFeatures(song));
        addAudiophileHeaders(response, song);
    }

    private void addAudiophileHeaders(NioHttpServer.HttpResponse response, MusicTag tag) {
        if (tag.getAudioSampleRate() > 0) response.addHeader("X-Audio-Sample-Rate", tag.getAudioSampleRate() + " Hz");
        if (tag.getAudioBitsDepth() > 0) response.addHeader("X-Audio-Bit-Depth", tag.getAudioBitsDepth() + " bit");
        if (tag.getAudioBitRate() > 0) response.addHeader("X-Audio-Bitrate", tag.getAudioBitRate()/1000 + " kbps");
        if (TagUtils.getChannels(tag) > 0) response.addHeader("X-Audio-Channels", String.valueOf(TagUtils.getChannels(tag)));
        response.addHeader("X-Audio-Format", Objects.toString(tag.getFileType(), ""));
    }

    public void stopServer() {
        Log.i(TAG, TAG + " - Stopping SonicNIO WebServer");
        synchronized (serverLock) {
            if (server != null) {
                // 1. Close WebSockets
                if (wsHandler != null) {
                    wsHandler.shutdown();
                    wsHandler = null;
                }

                // 2. Stop Server
                try {
                    server.stop();
                    Log.i(TAG, "SonicNIO WebServer stopped successfully.");
                } catch (Exception e) {
                    Log.e(TAG, "Error during server shutdown", e);
                } finally {
                    server = null;
                }

                // 3. Cleanup Threads
                if (serverThread != null) {
                    try {
                        serverThread.join(2000);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        Log.w(TAG, TAG + " - Interrupted while waiting for server thread", e);
                    }finally {
                        serverThread = null;
                    }
                }

                // 4. CRITICAL: Shut down the background executor!
                if (backgroundExecutor != null && !backgroundExecutor.isShutdown()) {
                    backgroundExecutor.shutdownNow();
                }
            }
        }
    }

    @Override
    public int getListenPort() {
        return WEB_SERVER_PORT;
    }

    private class WebSocketHandlerImpl extends WebSocketContent implements NioHttpServer.WebSocketHandler {
        private final CopyOnWriteArraySet<NioHttpServer.WebSocketConnection> sessions = new CopyOnWriteArraySet<>();
        private static final int MAX_SESSIONS = 100;
        private static final ObjectMapper MAPPER = new ObjectMapper()
                .setDefaultPropertyInclusion(JsonInclude.Include.ALWAYS);

        public WebSocketHandlerImpl() {
            super();
        }

        @Override
        protected void broadcastMessage(String jsonResponse) {
            if (sessions == null || sessions.isEmpty()) return;

            for (NioHttpServer.WebSocketConnection session : sessions) {
                if (session.isClosed()) {
                    sessions.remove(session);
                    continue;
                }
                try {
                    session.send(jsonResponse);
                } catch (Exception e) {
                    Log.w(TAG, TAG + " - Failed to broadcast, removing session.", e);
                    session.forceClose();
                    sessions.remove(session);
                }
            }
        }

        @Override
        public String getNamespace() {
            return CONTEXT_PATH_WEBSOCKET;
        }

        @Override
        public void onOpen(NioHttpServer.WebSocketConnection connection) {
            Log.d(TAG, TAG + " - New WebSocket connection.");
            if (sessions.size() >= MAX_SESSIONS) {
                Log.w(TAG, TAG + " - Max WebSocket connections reached");
                connection.close(WEBSOCKET_CLOSE_SERVER_FULL, "Server full");
                return;
            }
            sessions.add(connection);

            List<Map<String, Object>> messages = getWelcomeMessages();
            for (Map<String, Object> message : messages) {
                sendMessage(connection, message);
            }
        }

        private void sendMessage(NioHttpServer.WebSocketConnection connection, Map<String, Object> response) {
            if (response != null && !connection.isClosed()) {
                try {
                   // byte[] rawJson = MAPPER.writeValueAsBytes(response);
                   //connection.send(new String(rawJson, StandardCharsets.UTF_8));
                    String rawJson = MAPPER.writeValueAsString(response);
                    connection.send(rawJson);
                } catch (JsonProcessingException e) {
                    Log.e(TAG, "Error serializing response", e);
                }
            }
        }

        @Override
        public void onMessage(NioHttpServer.WebSocketConnection connection, String message) {
            if (message == null || message.length() < 2) return;

            char firstChar = message.charAt(0);
            if (firstChar != '{') {
                if (!Character.isWhitespace(firstChar)) return;
                message = message.trim();
                if (message.isEmpty() || message.charAt(0) != '{') return;
            }

            try {
                @SuppressWarnings("unchecked")
                Map<String, Object> messageMap = MAPPER.readValue(message, Map.class);
                if (messageMap == null) return;

                String command = String.valueOf(messageMap.getOrDefault("command", ""));
                if (command.isEmpty()) return;

                Map<String, Object> response = handleCommand(command, messageMap);
                sendMessage(connection, response);

            } catch (IOException e) {
                Log.e(TAG, TAG + " - Bad JSON: " + message);
            } catch (Exception e) {
                Log.e(TAG, TAG + " - Error processing WS message", e);
            }
        }

        @Override
        public void onMessage(NioHttpServer.WebSocketConnection connection, byte[] message) {
            // Binary not supported
        }

        @Override
        public void onClose(NioHttpServer.WebSocketConnection connection, int code, String reason) {
            Log.d(TAG, TAG + " - WS Closed. Code: " + code);
            sessions.remove(connection);
        }

        @Override
        public void onError(NioHttpServer.WebSocketConnection connection, Exception ex) {
            Log.e(TAG, TAG + " - WS Error", ex);
        }

        public void shutdown() {
            try {
                for (NioHttpServer.WebSocketConnection session : sessions) {
                    session.close(WEBSOCKET_CLOSE_GOING_AWAY, "Server shutting down");
                }
                sessions.clear();
            } catch (Exception exception) {
                Log.e(TAG, "Error shutting down WS", exception);
            }
        }
    }
}
