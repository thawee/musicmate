package apincer.music.core.server;

import static apincer.music.core.server.DLNAHeaderHelper.getDLNAContentFeatures;
import static apincer.music.core.utils.StringUtils.isEmpty;

import android.content.Context;
import android.util.Log;

import com.google.gson.Gson;

import java.io.File;
import java.io.IOException;
import java.net.InetAddress;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArraySet;

import apincer.music.core.database.MusicTag;
import apincer.music.core.http.NioHttpServer;
import apincer.music.core.playback.NowPlaying;
import apincer.music.core.playback.Player;
import apincer.music.core.repository.FileRepository;
import apincer.music.core.repository.TagRepository;
import apincer.music.core.server.spi.ContentServer;
import apincer.music.core.utils.StringUtils;
import apincer.music.core.utils.TagUtils;

public class NioContentServerImpl extends BaseServer implements ContentServer {
    private static final String TAG = "NioContentServer";

    // Server configuration
    private static final int MAX_THREADS = 4;
    private static final int IDLE_TIMEOUT = 300000; // 5 minutes
    private static final int READ_BUFFER_SIZE = 8192;

    private NioHttpServer server;
    private final WebSocketContent wsContent;
    private WebSocketHandlerImpl wsHandler;

    private final TagRepository tagRepos;
    private final FileRepository fileRepos;

    public NioContentServerImpl(Context context, FileRepository fileRepos, TagRepository tagRepos) {
        super(context);
        this.fileRepos = fileRepos;
        this.tagRepos =tagRepos;
        wsContent = new WebSocketContent(context, tagRepos);
        addLibInfo("NioHttpServer", "2.0");
    }

    public void initServer(InetAddress bindAddress) {
        Thread serverThread = new Thread(() -> {
            try {
                server = new NioHttpServer(CONTENT_SERVER_PORT);
                server.setMaxThread(MAX_THREADS);
                server.setClientReadBufferSize(READ_BUFFER_SIZE);
                server.setKeepAliveTimeout(IDLE_TIMEOUT);

                // --- WebSocket Handler ---
                wsHandler = new WebSocketHandlerImpl();
                server.registerWebSocketHandler(CONTEXT_PATH_WEBSOCKET, wsHandler);

                // --- Main HTTP Handler ---
                server.registerFallbackHandler(this::handleHttpRequest);

                Log.i(TAG, "Starting Content Server (NIO) on " + bindAddress.getHostAddress() + ":" + CONTENT_SERVER_PORT);
                server.run(); // This blocks until the server is stopped.
                Log.i(TAG, "Content Server stopped.");

            } catch (Exception e) {
                Log.e(TAG, "Failed to start Content Server", e);
            }
        });

        serverThread.setName("nio-server-runner");
        serverThread.start();
    }

    private NioHttpServer.HttpResponse handleHttpRequest(NioHttpServer.HttpRequest request) {
        try {
            String requestUri = request.getPath();
            if (isEmpty(requestUri) || requestUri.equals("/")) {
                requestUri = "/index.html";
            }

            if (requestUri.startsWith(CONTEXT_PATH_COVERART)) {
                File filePath = getAlbumArt(requestUri);
                return createResourceResponse(filePath, request);
            } else if (requestUri.startsWith(CONTEXT_PATH_MUSIC)) {
                MusicTag song = getSong(requestUri);
                return createSongResponse(song, request);
            } else {
                File filePath = getWebResource(requestUri);
                return createResourceResponse(filePath, request);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error handling HTTP request", e);
            return new NioHttpServer.HttpResponse().setStatus(500, "Internal Server Error");
        }
    }

    private NioHttpServer.HttpResponse createSongResponse(MusicTag song, NioHttpServer.HttpRequest request) throws IOException {
        if (song == null) {
            return new NioHttpServer.HttpResponse().setStatus(404, "Not Found").setBody("Song not found".getBytes());
        }
        File audioFile = new File(song.getPath());
        if (!audioFile.exists() || !audioFile.canRead()) {
            return new NioHttpServer.HttpResponse().setStatus(404, "Not Found").setBody("File not accessible".getBytes());
        }

        // Notify playback service
        notifyPlaybackService(request.getHeader("host", ""), request.getHeader("user-agent", ""), song);

        // Create the response and add special headers
        NioHttpServer.FileResponse response = new NioHttpServer.FileResponse(audioFile, request);
        prepareMusicStreamingHeaders(response, song);

        Log.i(TAG, "Starting stream: \"" + song.getTitle() + "\" [" + formatAudioQuality(song) + "] to " + request.getRemoteHost());
        return response;
    }

    private NioHttpServer.HttpResponse createResourceResponse(File filePath, NioHttpServer.HttpRequest request) throws IOException {
        if (filePath == null || !filePath.exists() || !filePath.canRead()) {
            return new NioHttpServer.HttpResponse().setStatus(404, "Not Found").setBody("Resource not found".getBytes());
        }
        return new NioHttpServer.FileResponse(filePath, request);
    }

    private File getWebResource(String requestUri) {
        File webUiDir = new File(getContext().getFilesDir(), "webui");
        if (requestUri.contains("?")) {
            requestUri = requestUri.substring(0, requestUri.indexOf("?"));
        }
        return new File(webUiDir, requestUri);
    }

    private File getAlbumArt(String requestUri) {
        String albumUniqueKey = requestUri.substring(CONTEXT_PATH_COVERART.length(), requestUri.indexOf(".png"));
        return fileRepos.getCoverArt(albumUniqueKey);
    }

    private MusicTag getSong(String uri) {
        if (uri == null || !uri.startsWith(CONTEXT_PATH_MUSIC)) {
            return null;
        }
        try {
            String pathPart = uri.substring(CONTEXT_PATH_MUSIC.length());
            String[] parts = pathPart.split("/");
            if (parts.length > 0 && !parts[0].isEmpty()) {
                long contentId = StringUtils.toLong(parts[0]);
                return tagRepos.findById(contentId);
            }
        } catch (Exception ex) {
            Log.e(TAG, "Failed to parse content ID from URI: " + uri, ex);
        }
        return null;
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
        Log.i(TAG, "Stopping Content Server (NIO)");
        if (server != null) {
            server.stop();
        }
    }

    @Override
    public String getComponentName() {
        return "ContentServer";
    }

    @Override
    public int getListenPort() {
        return CONTENT_SERVER_PORT;
    }

    private String formatAudioQuality(MusicTag tag) {
        StringBuilder quality = new StringBuilder();
        if (tag.getAudioSampleRate() > 0 && tag.getAudioBitsDepth() > 0) {
            if (tag.getAudioSampleRate() >= 88200 && tag.getAudioBitsDepth() >= 24) quality.append("Hi-Res ");
            else if (tag.getAudioSampleRate() >= 44100 && tag.getAudioBitsDepth() >= 16) quality.append("CD-Quality ");
        }
        quality.append(tag.getFileType());
        if (tag.getAudioSampleRate() > 0) quality.append(" ").append(tag.getAudioSampleRate() / 1000.0).append("kHz");
        if (tag.getAudioBitsDepth() > 0) quality.append("/").append(tag.getAudioBitsDepth()).append("-bit");
        int channels = TagUtils.getChannels(tag);
        if (channels > 0) {
            if (channels == 1) quality.append(" Mono");
            else if (channels == 2) quality.append(" Stereo");
            else quality.append(" Multichannel (").append(channels).append(")");
        }
        return quality.toString();
    }

    private void notifyPlaybackService(String clientIp, String userAgent, MusicTag tag) {
        if(getPlaybackService() != null) {
            RendererDevice device = getPlaybackService().getRendererByIpAddress(clientIp);
            Player player = (device != null) ?
                    Player.Factory.create(getContext(), device) :
                    Player.Factory.create(getContext(), clientIp, userAgent);
            getPlaybackService().onNewTrackPlaying(player, tag, 0);
        }
    }

    // This method needs to be called from the PlaybackService to push updates
    public void broadcastNowPlaying(NowPlaying nowPlaying) {
        if (wsHandler != null) {
            wsHandler.broadcastNowPlaying(nowPlaying);
        }
    }

    private class WebSocketHandlerImpl implements NioHttpServer.WebSocketHandler {
        private final CopyOnWriteArraySet<NioHttpServer.WebSocketConnection> sessions = new CopyOnWriteArraySet<>();
        private final Gson gson = new Gson();

        @Override
        public void onOpen(NioHttpServer.WebSocketConnection connection) {
            Log.d(TAG, "New WebSocket connection.");
            sessions.add(connection);
        }

        @Override
        public void onMessage(NioHttpServer.WebSocketConnection connection, String message) {
            Log.d(TAG, "Received message: " + message);
            try {
                // Defensively check if the message is a valid JSON object before parsing.
                if (message == null || !message.trim().startsWith("{")) {
                    Log.w(TAG, "Received WebSocket message is not a valid JSON object, ignoring: " + message);
                    return;
                }
                Map<String, Object> messageMap = gson.fromJson(message, Map.class);
                String command = messageMap.getOrDefault("command", "").toString();

                Map<String, Object> response = wsContent.handleCommand(command, messageMap);
                if (response != null) {
                    String jsonResponse = gson.toJson(response);
                    connection.send(jsonResponse);
                    Log.d(TAG, "Response message: " + jsonResponse);
                }
            } catch (com.google.gson.JsonSyntaxException e) {
                // Catching the specific exception for bad JSON.
                Log.e(TAG, "Error parsing WebSocket JSON message: " + message, e);
                // We don't call onError because this is a client-side data error, not a connection error.
            } catch (Exception e) {
                Log.e(TAG, "Error processing WebSocket message", e);
                onError(connection, e);
            }
        }

        @Override
        public void onMessage(NioHttpServer.WebSocketConnection connection, byte[] message) {
            Log.d(TAG, "Received binary message of length: " + message.length);
            // Not implemented in original code
        }

        @Override
        public void onClose(NioHttpServer.WebSocketConnection connection, int code, String reason) {
            Log.d(TAG, "WebSocket connection closed. Code: " + code + ", Reason: " + reason);
            sessions.remove(connection);
        }

        @Override
        public void onError(NioHttpServer.WebSocketConnection connection, Exception ex) {
            Log.e(TAG, "WebSocket error", ex);
        }

        private void broadcast(String message) {
            sessions.parallelStream().forEach(session -> {
                try {
                    session.send(message);
                } catch (Exception e) {
                    Log.w(TAG, "Failed to broadcast to a session, removing it.", e);
                    sessions.remove(session);
                }
            });
        }

        public void broadcastNowPlaying(NowPlaying nowPlaying) {
            if (nowPlaying != null && nowPlaying.getSong() != null) {
                Map<String, Object> response = wsContent.getNowPlaying(nowPlaying);
                if (response != null) {
                    String jsonResponse = gson.toJson(response);
                    broadcast(jsonResponse);
                }
            }
        }
    }
}

