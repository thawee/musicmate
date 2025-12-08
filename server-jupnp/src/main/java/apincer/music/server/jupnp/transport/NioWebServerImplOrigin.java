package apincer.music.server.jupnp.transport;

import static apincer.music.core.http.NioHttpServer.HTTP_INTERNAL_ERROR;
import static apincer.music.core.http.NioHttpServer.HTTP_NOT_FOUND;
import static apincer.music.core.http.NioHttpServer.WEBSOCKET_CLOSE_SERVER_FULL;
import static apincer.music.core.utils.StringUtils.isEmpty;
import static apincer.music.server.jupnp.transport.DLNAHeaderHelper.getDLNAContentFeatures;

import android.content.Context;
import android.util.Log;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.File;
import java.io.IOException;
import java.net.InetAddress;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArraySet;

import apincer.music.core.database.MusicTag;
import apincer.music.core.http.NioHttpServer;
import apincer.music.core.repository.FileRepository;
import apincer.music.core.repository.TagRepository;
import apincer.music.core.server.BaseServer;
import apincer.music.core.server.spi.WebServer;
import apincer.music.core.utils.TagUtils;

public class NioWebServerImplOrigin extends BaseServer implements WebServer {
    private static final String TAG = "NioWebServer";

    private static final String DEFAULT_PATH = "/index.html";
    private static final String MSG_SONG_NOT_FOUND = "Song not found";
    private static final String MSG_FILE_NOT_ACCESSIBLE = "File not accessible";

    // Server configuration
    private static final int MAX_THREADS = 4;
    private static final int IDLE_TIMEOUT = 300000; // 5 minutes
    private static final int READ_BUFFER_SIZE = 8192;

    private Thread serverThread;
    private NioHttpServer server;
    private WebSocketHandlerImpl wsHandler;

    public NioWebServerImplOrigin(Context context, FileRepository fileRepos, TagRepository tagRepos) {
        super(context, fileRepos, tagRepos);
        addLibInfo("NioWebServer", "2.1");
    }

    public void initServer(InetAddress bindAddress) {
        // If server thread is not null and is still running, exit.
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

                // --- WebSocket Handler ---
                wsHandler = new WebSocketHandlerImpl();
                server.registerWebSocketHandler(CONTEXT_PATH_WEBSOCKET, wsHandler);

                // --- Main HTTP Handler ---
                server.registerFallbackHandler(this::handleHttpRequest);
                Log.i(TAG, TAG+" - WebServer (NIO) running on " + bindAddress.getHostAddress() + ":" + WEB_SERVER_PORT);
                server.run(); // This blocks until the server is stopped.
               // Log.i(TAG, "Content Server stopped.");

            } catch (Exception e) {
                Log.e(TAG, TAG+" - Failed to start WebServer", e);
            }
        });

        serverThread.setName("nio-webserver-runner");
        serverThread.start();
    }

    private NioHttpServer.HttpResponse handleHttpRequest(NioHttpServer.HttpRequest request) {
        try {
            String requestUri = request.getPath();
            if (isEmpty(requestUri) || requestUri.equals("/")) {
                requestUri = DEFAULT_PATH;
            }

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
            Log.e(TAG, TAG+" - Error handling HTTP request", e);
            return new NioHttpServer.HttpResponse().setStatus(HTTP_INTERNAL_ERROR, "Internal Server Error");
        }
    }

    private NioHttpServer.HttpResponse createAlbumArtResponse(File filePath, NioHttpServer.HttpRequest request) throws IOException {
        if (filePath == null || !filePath.exists() || !filePath.canRead()) {
            return new NioHttpServer.HttpResponse().setStatus(HTTP_NOT_FOUND, "Not Found").setBody("Resource not found".getBytes());
        }
        try {
            return server.createFileResponse(filePath, request);
        } catch (Exception e) {
            filePath = getDefaultAlbumArt();
            return server.createFileResponse(filePath, request);
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
            // Create the response and add special headers
            NioHttpServer.HttpResponse response = server.createFileResponse(audioFile, request);
            prepareMusicStreamingHeaders(response, song);

            // Notify playback service
            notifyPlayback(request.getRemoteHost(), request.getHeader("user-agent", "Streaming Renderer"), song);

            Log.i(TAG, TAG+" - Streaming \"" + song.getTitle() + "\" [" + formatAudioQuality(song) + "] to " + request.getRemoteHost());
            return response;
        } catch (IOException e) {
            Log.e(TAG, TAG+" - Error creating file response", e);
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
        Log.i(TAG, TAG+" - Stopping WebServer (NIO)");
        if (wsHandler != null) {
            wsHandler.shutdown(); // ← Close all WebSocket connections
        }
        if (server != null) {
            server.stop();
        }
        // Add thread cleanup
        if (serverThread != null && serverThread.isAlive()) {
            try {
                serverThread.join(2000); // Wait up to 2 seconds
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                Log.w(TAG, TAG+" - Interrupted while waiting for server thread", e);
            }
        }

        // Set fields to null so the server can be restarted
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
                .disableHtmlEscaping() // Reduces string processing
                .serializeNulls() // Optional: skip nulls to reduce JSON size
                .create();
       // private final WebSocketContent webSocketContent = buildWebSocketContent();

        public WebSocketHandlerImpl() {
            super();
            /*
            registerPlaybackCallback(new PlaybackCallback() {
                @Override
                public void onMediaTrackChanged(MediaTrack metadata) {
                    broadcastNowPlaying(metadata);
                }

                @Override
                public void onPlaybackStateChanged(PlaybackState state) {
                    broadcastPlaybackState(state);
                }

                @Override
                public void onPlaybackTargetChanged(PlaybackTarget playbackTarget) {
                    broadcastPlaybackTarget(playbackTarget);
                }
            }); */
        }

        @Override
        protected void broadcastMessage(String jsonResponse) {
            if(sessions ==null || sessions.isEmpty()) return;

            sessions.removeIf(NioHttpServer.WebSocketConnection::isClosed); // Clean up closed connections

            //  Log.d(TAG, TAG+" - Broadcast message: " + message);
            sessions.parallelStream().forEach(session -> {
                try {
                    if (!session.isClosed()) {
                        session.send(jsonResponse);
                    }
                } catch (Exception e) {
                    Log.w(TAG, TAG+" - Failed to broadcast to a session, removing it.", e);
                    session.forceClose();
                    sessions.remove(session);
                }
            });
        }

        @Override
        public void onOpen(NioHttpServer.WebSocketConnection connection) {
            Log.d(TAG, TAG+" - New WebSocket connection.");
            if (sessions.size() >= MAX_SESSIONS) {
                Log.w(TAG, TAG+" - Max WebSocket connections reached, rejecting new connection");
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
            if (response != null) {
                String jsonResponse = GSON.toJson(response);
                connection.send(jsonResponse);
                Log.d(TAG, TAG+" - Send message: " + jsonResponse);
            }
        }

        @Override
        public void onMessage(NioHttpServer.WebSocketConnection connection, String message) {
            //Log.d(TAG, "Received message: " + message);

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
                    Log.w(TAG, TAG+" - Null message map, ignoring");
                    return;
                }

                String command = String.valueOf(messageMap.getOrDefault("command", ""));

                // Validate command before processing
                if (command.isEmpty()) {
                    Log.w(TAG, TAG+" - Empty command, ignoring");
                    return;
                }

                Map<String, Object> response = handleCommand(command, messageMap);
               sendMessage(connection, response);
            } catch (com.google.gson.JsonSyntaxException e) {
                // Catching the specific exception for bad JSON.
                Log.e(TAG, TAG+" - Error parsing WebSocket JSON message: " + message, e);
                // We don't call onError because this is a client-side data error, not a connection error.
            } catch (Exception e) {
                Log.e(TAG,TAG+" - Error processing WebSocket message", e);
                onError(connection, e);
            }
        }

        @Override
        public void onMessage(NioHttpServer.WebSocketConnection connection, byte[] message) {
            Log.d(TAG, TAG+" - Received binary message of length: " + message.length);
            // Not implemented in original code
        }

        @Override
        public void onClose(NioHttpServer.WebSocketConnection connection, int code, String reason) {
            Log.d(TAG, TAG+" - WebSocket connection closed. Code: " + code + ", Reason: " + reason);
            sessions.remove(connection);
        }

        @Override
        public void onError(NioHttpServer.WebSocketConnection connection, Exception ex) {
            Log.e(TAG, TAG+" - WebSocket error", ex);
        }

        /*
        public void broadcastPlaybackState(PlaybackState state) {
            Map<String, Object> response = getPlaybackState(state);
            if (response != null) {
                String jsonResponse = GSON.toJson(response);
                broadcast(jsonResponse);
            }
        }

        public void broadcastNowPlaying(MediaTrack track) {
            Map<String, Object> response = getNowPlaying(track);
            if (response != null) {
                String jsonResponse = GSON.toJson(response);
                broadcast(jsonResponse);

                Log.d(TAG, "broadcastNowPlaying: "+jsonResponse);
            }
        }

        public void broadcastPlaybackTarget(PlaybackTarget player) {
            Map<String, Object> response = getPlaybackTarget(player);
            if (response != null) {
                String jsonResponse = GSON.toJson(response);
                broadcast(jsonResponse);
                Log.d(TAG, "broadcastPlaybackTarget: "+jsonResponse);
            }
        } */

        // Shutdown cleanup executor when server stops
        public void shutdown() {
            try {
                sessions.forEach(session -> session.close(1001, "Server shutting down"));
                sessions.clear();
            }catch (Exception exception) {
                exception.printStackTrace();
            }
        }
    }
}

