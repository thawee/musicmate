package apincer.music.server.nio;

import static apincer.music.core.http.NioHttpServer.HTTP_INTERNAL_ERROR;
import static apincer.music.core.http.NioHttpServer.HTTP_NOT_FOUND;
import static apincer.music.core.http.WebSocket.CLOSE_GOING_AWAY;
import static apincer.music.core.http.WebSocket.CLOSE_SERVER_FULL;
import static apincer.music.server.jupnp.transport.DLNAHeaderHelper.getDLNAContentFeatures;

import apincer.music.server.jupnp.transport.DLNAHeaderHelper;

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
    private RateLimitingHandler rateLimiter;
    private final apincer.music.core.http.ServerDiagnostics diagnostics = new apincer.music.core.http.ServerDiagnostics();

    @Override
    public String getDiagnostics() {
        NioHttpServer current = server;
        RateLimitingHandler limiter = rateLimiter;
        if (current == null) return "";
        long now = System.currentTimeMillis();
        String line = diagnostics.line(current.getStats(), limiter != null ? limiter.getRateLimitedCount() : 0, now);
        // Second line: clients (TVs, apps) seen on either port in the last 10 minutes
        String clients = apincer.music.core.server.ClientRegistry.SHARED.recentSummary(now, 10 * 60_000L);
        return clients.isEmpty() ? line : line + "\nClients: " + clients;
    }
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
            // Build the server here, under the lock, so a stopServer() that arrives before the
            // thread runs still finds it; it used to be assigned inside the thread.
            NioHttpServer newServer = new NioHttpServer(WEB_SERVER_PORT);
            newServer.setMaxThread(MAX_THREADS);
            newServer.setClientReadBufferSize(READ_BUFFER_SIZE);
            newServer.setKeepAliveTimeout(IDLE_TIMEOUT);
            wsHandler = new WebSocketHandlerImpl();
            newServer.registerWebSocketHandler(wsHandler);
            // Cover art is exempt: a WebUI grid loads many covers at once from one browser
            RateLimitingHandler rateLimiter = new RateLimitingHandler(50,
                    path -> path.startsWith(CONTEXT_PATH_COVERART), this::handleRequest);
            newServer.registerHttpHandler(rateLimiter);
            this.rateLimiter = rateLimiter;
            server = newServer;
            serverThread = new Thread(() -> {
                try {
                    newServer.run();
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
        apincer.music.core.server.ClientRegistry.observe(remoteHost, request.getHeaders(), rawUri);
        try {
            ContentHolder contentHolder = resolveRequest(rawUri, remoteHost, userAgent);
            if (contentHolder == null || !contentHolder.exists()) {
                return createErrorResponse(HTTP_NOT_FOUND, "Not found");
            }
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

        // Content-Type comes from FileResponse, which reads the image's bytes. The extension is often
        // wrong (JPEG covers named .png, embedded PNG art saved as Cover.jpg), and strict DLNA
        // renderers reject a mismatched type.
        NioHttpServer.HttpResponse response = server.createFileResponse(filePath, request);
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

        // A TV that cannot play this format gets it converted to WAV (lossless PCM)
        apincer.music.core.server.ClientFormatProfile profile = apincer.music.core.server.ClientFormatProfile.of(
                request.getHeader("user-agent", null), request.getHeader("x-av-client-info", null));
        if (profile.convertsToPcm(song) && TagUtils.isFLACFile(song)) {
            NioHttpServer.HttpResponse converted = createFlacAsWavResponse(audioFile, song, request);
            if (converted != null) return converted;
        }

        // DLNA time-based seek (LG webOS 2022+ seeks only this way; Sony prefers it)
        String timeSeekRange = request.getHeader("timeseekrange.dlna.org", null);
        long startOffset = -1;
        double startSeconds = 0;
        if (timeSeekRange != null && !"HEAD".equalsIgnoreCase(request.getMethod())) {
            double[] npt = apincer.music.server.jupnp.transport.TimeSeek.parseNpt(timeSeekRange);
            if (npt == null) return createErrorResponse(400, "Bad TimeSeekRange");
            if (npt[0] > 0) {
                String format = DLNAHeaderHelper.timeSeekFormat(song);
                if (format == null || song.getAudioDuration() <= 0) {
                    return createErrorResponse(406, "Time seek not supported for this file"); // DLNA 7.4.40.4
                }
                apincer.music.server.jupnp.transport.TimeSeek.Position seekTo =
                        apincer.music.server.jupnp.transport.TimeSeek.locate(audioFile, format, npt[0], song.getAudioDuration());
                if (seekTo == null) return createErrorResponse(416, "Time out of range");
                startOffset = seekTo.byteOffset;
                startSeconds = seekTo.startSeconds;
            }
        }

        NioHttpServer.HttpResponse response = server.createFileResponse(audioFile, request, startOffset);
        if (timeSeekRange != null && song.getAudioDuration() > 0) {
            // The time the stream really starts at (a FLAC seek point may be before the request)
            String total = apincer.music.server.jupnp.transport.TimeSeek.npt(song.getAudioDuration());
            response.addHeader("TimeSeekRange.dlna.org", "npt="
                    + apincer.music.server.jupnp.transport.TimeSeek.npt(startSeconds) + "-" + total + "/" + total);
        }
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
        DLNAHeaderHelper.getAudioHeaders(song).forEach(response::addHeader);
        // Samsung TVs ask for the track length this way
        if (request.getHeader("getmediainfo.sec", null) != null) {
            String mediaInfo = DLNAHeaderHelper.getSamsungMediaInfo(song);
            if (mediaInfo != null) response.addHeader("MediaInfo.sec", mediaInfo);
        }
        return response;
    }

    /**
     * The FLAC as a WAV stream (FlacToWav: native rate and depth, bit-perfect PCM), decoded while
     * it is sent; supports byte Range (206) and DLNA time seek (200 + TimeSeekRange). Null if the
     * FLAC cannot be read, so the caller serves the original file.
     */
    private NioHttpServer.HttpResponse createFlacAsWavResponse(File flac, Track song, NioHttpServer.HttpRequest request) {
        apincer.music.core.codec.FlacToWav wav;
        try {
            wav = apincer.music.core.codec.FlacToWav.open(flac);
        } catch (IOException e) {
            Log.w(TAG, "Cannot convert " + flac + "; serving the original", e);
            return null;
        }
        long length = wav.wavLength();
        long from = 0;
        long to = length - 1;
        int status = 200;
        String statusText = "OK";
        String contentRange = null;
        String timeSeekRange = null;

        String timeSeek = request.getHeader("timeseekrange.dlna.org", null);
        String range = request.getHeader("range", null);
        double duration = wav.numSamples / (double) wav.sampleRate;
        if (timeSeek != null) {
            double[] npt = apincer.music.server.jupnp.transport.TimeSeek.parseNpt(timeSeek);
            if (npt == null) return createErrorResponse(400, "Bad TimeSeekRange");
            if (npt[0] >= duration) return createErrorResponse(416, "Time out of range");
            from = wav.byteAtSeconds(npt[0]);
            String total = apincer.music.server.jupnp.transport.TimeSeek.npt(duration);
            timeSeekRange = "npt=" + apincer.music.server.jupnp.transport.TimeSeek.npt(npt[0]) + "-" + total + "/" + total;
        } else if (range != null && range.startsWith("bytes=") && !range.contains(",")) {
            String spec = range.substring(6).trim();
            try {
                if (spec.startsWith("-")) {
                    from = Math.max(0, length - Long.parseLong(spec.substring(1)));
                } else {
                    String[] parts = spec.split("-", 2);
                    from = Long.parseLong(parts[0]);
                    if (parts.length > 1 && !parts[1].isEmpty()) to = Math.min(length - 1, Long.parseLong(parts[1]));
                }
                if (from >= length || from > to) {
                    return new NioHttpServer.HttpResponse().setStatus(416, "Range Not Satisfiable")
                            .addHeader("Content-Range", "bytes */" + length);
                }
                status = 206;
                statusText = "Partial Content";
                contentRange = "bytes " + from + "-" + to + "/" + length;
            } catch (NumberFormatException ignored) {
                from = 0; // RFC 7233: an invalid Range is ignored
                to = length - 1;
            }
        }

        NioHttpServer.HttpResponse response;
        if ("HEAD".equalsIgnoreCase(request.getMethod())) {
            response = new NioHttpServer.HttpResponse().addHeader("Content-Length", String.valueOf(length));
        } else {
            final long start = from;
            final long end = to;
            final apincer.music.core.codec.FlacToWav source = wav;
            response = server.createStreamingResponse(status, statusText, end - start + 1,
                    sink -> source.write(flac, start, end, sink::write));
        }
        response.addHeader("Content-Type", "audio/wav");
        response.addHeader("Accept-Ranges", "bytes");
        if (contentRange != null) response.addHeader("Content-Range", contentRange);
        if (timeSeekRange != null) response.addHeader("TimeSeekRange.dlna.org", timeSeekRange);
        response.addHeader("transferMode.dlna.org", "Streaming");
        // CI=1: converted content; OP=11: time and byte seeks both work
        response.addHeader("contentFeatures.dlna.org", DLNAHeaderHelper.getConvertedPcmContentFeatures());
        response.addHeader("Cache-Control", "no-cache");
        response.addHeader("Server", getServerSignature());
        response.addHeader("Date", getCachedDate());
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
        destroy();
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
