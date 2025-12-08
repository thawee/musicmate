package apincer.music.server.jupnp.transport;

import static apincer.music.core.http.NioHttpServer.HTTP_INTERNAL_ERROR;
import static apincer.music.core.http.NioHttpServer.HTTP_NOT_FOUND;
import static apincer.music.core.http.NioHttpServer.WEBSOCKET_CLOSE_SERVER_FULL;
import static apincer.music.server.jupnp.transport.DLNAHeaderHelper.getDLNAContentFeatures;
import static apincer.music.core.utils.StringUtils.isEmpty;

import android.content.Context;
import android.util.Log;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.File;
import java.io.IOException;
import java.net.InetAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import apincer.music.core.database.MusicTag;
import apincer.music.core.http.NioHttpServer;
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

    // UPDATED: Increased thread count.
    // Browsers open ~6 connections. 4 is too low and causes hanging.
    private static final int MAX_THREADS = 16;
    private static final int IDLE_TIMEOUT = 300_000; // 5 minutes
    private static final int READ_BUFFER_SIZE = 8192;

    private Thread serverThread;
    private NioHttpServer server;
    private WebSocketHandlerImpl wsHandler;

    // Executor for background tasks (logging/notifications) to not block IO threads
    private final ExecutorService backgroundExecutor = Executors.newSingleThreadExecutor();

    public NioWebServerImpl(Context context, FileRepository fileRepos, TagRepository tagRepos) {
        super(context, fileRepos, tagRepos);
        addLibInfo("NioWebServer", "2.1");
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
                server.registerWebSocketHandler(CONTEXT_PATH_WEBSOCKET, wsHandler);

                server.registerFallbackHandler(this::handleHttpRequest);

                Log.i(TAG, TAG + " - WebServer (NIO) running on " + bindAddress.getHostAddress() + ":" + WEB_SERVER_PORT);
                server.run();

            } catch (Exception e) {
                Log.e(TAG, TAG + " - Failed to start WebServer", e);
            }
        });

        serverThread.setName("nio-webserver-runner");
        serverThread.start();
    }

    private NioHttpServer.HttpResponse handleHttpRequest(NioHttpServer.HttpRequest request) {
        try {
            String rawUri = request.getPath();
            if (isEmpty(rawUri) || rawUri.equals("/")) {
                rawUri = DEFAULT_PATH;
            }

            // FIX: Decode URI (e.g. "%20" -> " ")
            String requestUri = URLDecoder.decode(rawUri, StandardCharsets.UTF_8.name());

            if (requestUri.startsWith(CONTEXT_PATH_COVERART)) {
                File filePath = getAlbumArt(requestUri);
                return createAlbumArtResponse(filePath, request);
            } else if (requestUri.startsWith(CONTEXT_PATH_MUSIC)) {
                MusicTag song = getSong(requestUri);
                return createSongResponse(song, request);
            } else {
                File filePath = getWebResource(requestUri);
                return createResourceResponse(filePath, request);
            }
        } catch (Exception e) {
            Log.e(TAG, TAG + " - Error handling HTTP request: " + request.getPath(), e);
            return new NioHttpServer.HttpResponse().setStatus(HTTP_INTERNAL_ERROR, "Internal Server Error");
        }
    }

    private NioHttpServer.HttpResponse createAlbumArtResponse(File filePath, NioHttpServer.HttpRequest request) throws IOException {
        if (filePath == null || !filePath.exists() || !filePath.canRead()) {
            // Try default art immediately if specific failed
            filePath = getDefaultAlbumArt();
        }

        // Final check on default art
        if (filePath == null || !filePath.exists()) {
            return new NioHttpServer.HttpResponse().setStatus(HTTP_NOT_FOUND, "Not Found").setBody("Art not found".getBytes());
        }

        return server.createFileResponse(filePath, request);
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
            prepareMusicStreamingHeaders(response, song);

            // FIX: Run Playback Notification in background thread.
            // DO NOT block the IO thread with DB writes or other logic.
            backgroundExecutor.submit(() -> {
                try {
                    notifyPlayback(request.getRemoteHost(), request.getHeader("user-agent", "Streaming Renderer"), song);
                    Log.i(TAG, TAG + " - Streaming \"" + song.getTitle() + "\" to " + request.getRemoteHost());
                } catch (Exception e) {
                    Log.e(TAG, "Error in notifyPlayback", e);
                }
            });

            return response;
        } catch (IOException e) {
            Log.e(TAG, TAG + " - Error creating file response", e);
            return createErrorResponse(HTTP_INTERNAL_ERROR, "Error streaming file");
        }
    }

    private NioHttpServer.HttpResponse createResourceResponse(File filePath, NioHttpServer.HttpRequest request) throws IOException {
        if (filePath == null || !filePath.exists() || !filePath.canRead()) {
            return new NioHttpServer.HttpResponse().setStatus(HTTP_NOT_FOUND, "Not Found").setBody("Resource not found".getBytes());
        }
        return server.createFileResponse(filePath, request);
    }

    private NioHttpServer.HttpResponse createErrorResponse(int code, String message) {
        return new NioHttpServer.HttpResponse()
                .setStatus(code, message)
                .setBody(message.getBytes())
                .addHeader("Content-Type", "text/plain; charset=utf-8");
    }

    private void prepareMusicStreamingHeaders(NioHttpServer.HttpResponse response, MusicTag song) {
        addDlnaHeaders(response, song);
        addAudiophileHeaders(response, song);
    }

    private void addDlnaHeaders(NioHttpServer.HttpResponse response, MusicTag tag) {
        response.addHeader("transferMode.dlna.org", "Streaming");
        response.addHeader("contentFeatures.dlna.org", getDLNAContentFeatures(tag));
    }

    private void addAudiophileHeaders(NioHttpServer.HttpResponse response, MusicTag tag) {
        if (tag.getAudioSampleRate() > 0) response.addHeader("X-Audio-Sample-Rate", tag.getAudioSampleRate() + " Hz");
        if (tag.getAudioBitsDepth() > 0) response.addHeader("X-Audio-Bit-Depth", tag.getAudioBitsDepth() + " bit");
        if (tag.getAudioBitRate() > 0) response.addHeader("X-Audio-Bitrate", tag.getAudioBitRate() + " kbps");
        if (TagUtils.getChannels(tag) > 0) response.addHeader("X-Audio-Channels", String.valueOf(TagUtils.getChannels(tag)));
        response.addHeader("X-Audio-Format", Objects.toString(tag.getFileType(), ""));
    }

    public void stopServer() {
        Log.i(TAG, TAG + " - Stopping WebServer (NIO)");

        // 1. Close WebSockets
        if (wsHandler != null) {
            wsHandler.shutdown();
        }

        // 2. Stop Server
        if (server != null) {
            server.stop();
        }

        // 3. Cleanup Threads
        if (serverThread != null && serverThread.isAlive()) {
            try {
                serverThread.join(2000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                Log.w(TAG, TAG + " - Interrupted while waiting for server thread", e);
            }
        }

        // 4. Clean background executor
        // (Note: usually you'd shut it down, but if you restart the server often,
        //  you might want to keep it or re-initialize it in initServer)

        serverThread = null;
        server = null;
        wsHandler = null;
    }

    @Override
    public String getComponentName() {
        return "WebServer";
    }

    @Override
    public int getListenPort() {
        return WEB_SERVER_PORT;
    }

    private class WebSocketHandlerImpl extends WebSocketContent implements NioHttpServer.WebSocketHandler {
        private final CopyOnWriteArraySet<NioHttpServer.WebSocketConnection> sessions = new CopyOnWriteArraySet<>();
        private static final int MAX_SESSIONS = 100;
        private static final Gson GSON = new GsonBuilder()
                .disableHtmlEscaping()
                .serializeNulls()
                .create();

        public WebSocketHandlerImpl() {
            super();
        }

        @Override
        protected void broadcastMessage(String jsonResponse) {
            if (sessions == null || sessions.isEmpty()) return;

            // Simple iteration is cleaner and often faster for small sets (<100) than parallelStream
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
        public void onOpen(NioHttpServer.WebSocketConnection connection) {
            Log.d(TAG, TAG + " - New WebSocket connection.");
            if (sessions.size() >= MAX_SESSIONS) {
                Log.w(TAG, TAG + " - Max WebSocket connections reached");
                connection.close(WEBSOCKET_CLOSE_SERVER_FULL, "Server full");
                return;
            }
            sessions.add(connection);

            // Send welcome messages in background to avoid blocking acceptance
            backgroundExecutor.submit(() -> {
                try {
                    List<Map<String, Object>> messages = getWelcomeMessages();
                    for (Map<String, Object> message : messages) {
                        sendMessage(connection, message);
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Error sending welcome messages", e);
                }
            });
        }

        private void sendMessage(NioHttpServer.WebSocketConnection connection, Map<String, Object> response) {
            if (response != null && !connection.isClosed()) {
                String jsonResponse = GSON.toJson(response);
                connection.send(jsonResponse);
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
                Map<String, Object> messageMap = GSON.fromJson(message, Map.class);
                if (messageMap == null) return;

                String command = String.valueOf(messageMap.getOrDefault("command", ""));
                if (command.isEmpty()) return;

                // Handle command logic
                Map<String, Object> response = handleCommand(command, messageMap);
                sendMessage(connection, response);

            } catch (com.google.gson.JsonSyntaxException e) {
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
                    session.close(1001, "Server shutting down");
                }
                sessions.clear();
            } catch (Exception exception) {
                Log.e(TAG, "Error shutting down WS", exception);
            }
        }
    }
}