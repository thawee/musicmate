package apincer.android.mmate.core.server;

import android.content.Context;
import android.util.Log;

import java.io.File;
import java.io.IOException;
import java.net.InetAddress;
import java.util.Objects;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import apincer.android.mmate.core.database.MusicTag;
import apincer.android.mmate.core.http.HTTPServer;
import apincer.android.mmate.core.playback.Player;
import apincer.android.mmate.core.repository.TagRepository;
import apincer.android.mmate.core.utils.MimeTypeUtils;
import apincer.android.mmate.core.utils.StringUtils;
import apincer.android.mmate.core.utils.TagUtils;

import static apincer.android.mmate.core.server.DLNAHeaderHelper.getDLNAContentFeatures;

/**
 * A media content server implementation using the lightweight JLHTTP server.
 */
public class DefaultContentServerImpl extends AbstractServer {
    private static final String TAG = "DefaultContentServerImpl";

    private static final int MAX_THREADS = 8;
    private static final int MIN_THREADS = 2;
    private static final long KEEP_ALIVE_TIME_SECONDS = 300; // 5 minutes

    private HTTPServer server;
    private final TagRepository repos;

    public DefaultContentServerImpl(Context context, IMediaServer mediaServer) {
        super(context, mediaServer);
        repos = mediaServer.getTagReRepository();
        addLibInfo("JLHTTP", "3.2");
    }

    @Override
    public void initServer(InetAddress bindAddress) throws Exception {
        Thread serverThread = new Thread(() -> {
            try {
                Log.i(TAG, "Starting Content Server (JLHTTP) on " + bindAddress.getHostAddress() + ":" + CONTENT_SERVER_PORT);

                server = new HTTPServer(CONTENT_SERVER_PORT);

                // Configure a thread pool executor similar to the Jetty version
                ThreadPoolExecutor executor = new ThreadPoolExecutor(
                        MIN_THREADS,
                        MAX_THREADS,
                        KEEP_ALIVE_TIME_SECONDS, TimeUnit.SECONDS,
                        new LinkedBlockingQueue<>()
                );
                server.setExecutor(executor);

                // Get the default virtual host to add our content handler
                HTTPServer.VirtualHost host = server.getVirtualHost(null);

                // The handler will be responsible for serving content.
                // We use a wildcard context to catch all requests for media files.
                host.addContext("/res/{*}", new ContentHandler(), "GET", "HEAD");

                // Start the server (this blocks the current thread until the server is stopped)
                server.start();

                Log.i(TAG, "Content Server started successfully on " + bindAddress.getHostAddress() + ":" + CONTENT_SERVER_PORT);

            } catch (IOException e) {
                Log.e(TAG, "Failed to start Content Server", e);
            }
        });

        serverThread.setName("jlhttp-server-runner");
        serverThread.start();
    }

    @Override
    public void stopServer() {
        if (server != null) {
            Log.i(TAG, "Stopping Content Server (JLHTTP)");
            server.stop();
            server = null;
        }
    }

    @Override
    protected String getComponentName() {
        return "ContentServer";
    }

    @Override
    public int getListenPort() {
        return CONTENT_SERVER_PORT;
    }

    /**
     * Handles incoming HTTP requests for media content.
     */
    private class ContentHandler implements HTTPServer.ContextHandler {

        @Override
        public int serve(HTTPServer.Request req, HTTPServer.Response resp) throws IOException {
            MusicTag tag = findMusicTagFromUri(req.getPath());
            if (tag == null) {
                Log.w(TAG, "Content not found for URI: " + req.getPath());
                // Let the server generate a standard 404 response
                resp.getHeaders().add("Server", getServerSignature());
                return 404;
            }

            File audioFile = new File(tag.getPath());
            if (!audioFile.exists() || !audioFile.canRead()) {
                Log.e(TAG, "Audio file not accessible: " + tag.getPath());
                resp.getHeaders().add("Server", getServerSignature());
                return 404;
            }

            // Notify the playback service that a track is being streamed
            notifyPlaybackService(req, tag);

            Log.i(TAG, "Starting stream: \"" + tag.getTitle() + "\" [" + formatAudioQuality(tag) + "] to " + req.getSocket().getInetAddress().getHostAddress());

            // Prepare custom headers before letting the server stream the file
            prepareResponseHeaders(resp.getHeaders(), tag);

            // Use the server's built-in file serving utility.
            // It handles range requests (seeking), conditional headers (ETag/If-Modified), etc.
            HTTPServer.serveFileContent(audioFile, req, resp);

            // Return 0 to indicate that we have handled the request and sent the response
            return 0;
        }

        private MusicTag findMusicTagFromUri(String uri) {
            if (uri == null || !uri.startsWith("/res/")) {
                return null;
            }
            try {
                String pathPart = uri.substring(5); // Everything after "/res/"
                String[] parts = pathPart.split("/");
                if (parts.length > 0 && !parts[0].isEmpty()) {
                    long contentId = StringUtils.toLong(parts[0]);
                    return repos.findById(contentId);
                }
            } catch (Exception ex) {
                Log.e(TAG, "Failed to parse content ID from URI: " + uri, ex);
            }
            return null;
        }

        private void notifyPlaybackService(HTTPServer.Request req, MusicTag tag) {
            String clientIp = req.getSocket().getInetAddress().getHostAddress();
            String userAgent = req.getHeaders().get("User-Agent");
            RendererDevice device = mediaServer.getRendererByIpAddress(clientIp);
            Player player = (device != null) ?
                    Player.Factory.create(getContext(), device) :
                    Player.Factory.create(getContext(), clientIp, userAgent);
            mediaServer.getPlaybackService().onNewTrackPlaying(player, tag, 0);
        }

        private void prepareResponseHeaders(HTTPServer.Headers headers, MusicTag tag) {
            // Set the custom server name
            headers.add("Server", getServerSignature());

            // Set the content type if not already set by serveFileContent
            String mimeType = MimeTypeUtils.getMimeTypeFromPath(tag.getPath());
            if (mimeType != null) {
                headers.add("Content-Type", mimeType);
            }

            addDlnaHeaders(headers, tag);
            addAudiophileHeaders(headers, tag);
        }

        /**
         * Add DLNA-specific headers for optimal client compatibility.
         */
        private void addDlnaHeaders(HTTPServer.Headers headers, MusicTag tag) {
            headers.add("transferMode.dlna.org", "Streaming");
            headers.add("contentFeatures.dlna.org", getDLNAContentFeatures(tag));
            // serveFileContent already adds Accept-Ranges: bytes
        }

        /**
         * Add audiophile-specific headers with detailed audio quality information.
         */
        private void addAudiophileHeaders(HTTPServer.Headers headers, MusicTag tag) {
            if (tag.getAudioSampleRate() > 0) headers.add("X-Audio-Sample-Rate", tag.getAudioSampleRate() + " Hz");
            if (tag.getAudioBitsDepth() > 0) headers.add("X-Audio-Bit-Depth", tag.getAudioBitsDepth() + " bit");
            if (tag.getAudioBitRate() > 0) headers.add("X-Audio-Bitrate", tag.getAudioBitRate() + " kbps");
            if (TagUtils.getChannels(tag) > 0) headers.add("X-Audio-Channels", String.valueOf(TagUtils.getChannels(tag)));
            headers.add("X-Audio-Format", Objects.toString(tag.getFileType(), ""));
        }

        /**
         * Format audio quality description based on tag properties.
         */
        private String formatAudioQuality(MusicTag tag) {
            StringBuilder quality = new StringBuilder();

            if (tag.getAudioSampleRate() > 0 && tag.getAudioBitsDepth() > 0) {
                if (tag.getAudioSampleRate() >= 88200 && tag.getAudioBitsDepth() >= 24) {
                    quality.append("Hi-Res ");
                } else if (tag.getAudioSampleRate() >= 44100 && tag.getAudioBitsDepth() >= 16) {
                    quality.append("CD-Quality ");
                }
            }

            quality.append(tag.getFileType());

            if (tag.getAudioSampleRate() > 0) {
                quality.append(" ").append(tag.getAudioSampleRate() / 1000.0).append("kHz");
            }

            if (tag.getAudioBitsDepth() > 0) {
                quality.append("/").append(tag.getAudioBitsDepth()).append("-bit");
            }

            int channels = TagUtils.getChannels(tag);
            if (channels > 0) {
                if (channels == 1) {
                    quality.append(" Mono");
                } else if (channels == 2) {
                    quality.append(" Stereo");
                } else {
                    quality.append(" Multichannel (").append(channels).append(")");
                }
            }

            return quality.toString();
        }
    }
}