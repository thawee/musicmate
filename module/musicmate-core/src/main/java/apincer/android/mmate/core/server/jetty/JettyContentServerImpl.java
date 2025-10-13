package apincer.android.mmate.core.server.jetty;

import static apincer.android.mmate.core.server.DLNAHeaderHelper.getDLNAContentFeatures;

import android.content.Context;
import android.util.Log;

import org.eclipse.jetty.http.CompressedContentFormat;
import org.eclipse.jetty.http.HttpHeader;
import org.eclipse.jetty.http.HttpMethod;
import org.eclipse.jetty.http.HttpStatus;
import org.eclipse.jetty.http.content.HttpContent;
import org.eclipse.jetty.server.Connector;
import org.eclipse.jetty.server.HttpConfiguration;
import org.eclipse.jetty.server.HttpConnectionFactory;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.Response;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.server.handler.ResourceHandler;
import org.eclipse.jetty.util.Callback;
import org.eclipse.jetty.util.resource.ResourceFactory;
import org.eclipse.jetty.util.thread.QueuedThreadPool;

import java.io.File;
import java.net.InetAddress;
import java.util.Objects;

import apincer.android.mmate.core.database.MusicTag;
import apincer.android.mmate.core.playback.Player;
import apincer.android.mmate.core.repository.TagRepository;
import apincer.android.mmate.core.server.IMediaServer;
import apincer.android.mmate.core.server.RendererDevice;
import apincer.android.mmate.core.server.WebServer;
import apincer.android.mmate.core.utils.MimeTypeUtils;
import apincer.android.mmate.core.utils.StringUtils;
import apincer.android.mmate.core.utils.TagUtils;

public class JettyContentServerImpl extends WebServer {
    private static final String TAG = "JettyContentServer";

    // Optimized server configuration for audiophile streaming
    private static final int OUTPUT_BUFFER_SIZE = 262144; // 262144 - 256KB for better high-res streaming
    private static final int MAX_THREADS = 8; //30;
    private static final int MIN_THREADS = 2; //6;
    private static final int IDLE_TIMEOUT = 300000; // 5 minutes
    private static final int CONNECTION_TIMEOUT = 600000; // 10 minutes

    // Additional performance settings for high-resolution audio
    private static final int REQUEST_HEADER_SIZE = 16384; // Larger header size for complex requests
    private static final int RESPONSE_HEADER_SIZE = 16384; // Larger header size for detailed responses
    private static final int ACCEPT_QUEUE_SIZE = 32; //256;     // Larger queue for multiple clients

    private Server server;
    private final TagRepository repos;

    public JettyContentServerImpl(Context context, IMediaServer mediaServer) {
        super(context, mediaServer);
        repos = mediaServer.getTagReRepository();
    }

    public void initServer(InetAddress bindAddress) throws Exception {
        // Initialize the server with the specified port.
        Thread serverThread = new Thread(() -> {
            try {
                //Log.i(TAG, "Starting Content Server (Jetty) on " + bindAddress.getHostAddress() + ":" + CONTENT_SERVER_PORT);

                // Configure thread pool optimized for audio streaming
                QueuedThreadPool threadPool = new QueuedThreadPool();
                threadPool.setMaxThreads(MAX_THREADS);
                threadPool.setMinThreads(MIN_THREADS);
                threadPool.setIdleTimeout(IDLE_TIMEOUT);
                threadPool.setName("jetty-content-server");
                threadPool.setDetailedDump(false); // Enable detailed dumps for debugging
                // Add this after creating the thread pool
                threadPool.setThreadsPriority(Thread.NORM_PRIORITY + 1); // Slightly higher priority

                server = new Server(threadPool);
                // Add this line after creating the server
                server.setAttribute("org.eclipse.jetty.server.Request.maxFormContentSize", 10000);

                // HTTP Configuration optimized for media streaming
                HttpConfiguration httpConfig = new HttpConfiguration();
                httpConfig.setOutputBufferSize(OUTPUT_BUFFER_SIZE);
                httpConfig.setSendDateHeader(true);
                httpConfig.setSendServerVersion(false);
              //  httpConfig.setSendXPoweredBy(true);
                httpConfig.setRequestHeaderSize(REQUEST_HEADER_SIZE);
                httpConfig.setResponseHeaderSize(RESPONSE_HEADER_SIZE);
                httpConfig.setSecurePort(CONTENT_SERVER_PORT);
                httpConfig.setSecureScheme("http");

                // HTTP connector with optimized settings for stable streaming
                ServerConnector connector = new ServerConnector(server, new HttpConnectionFactory(httpConfig));
                connector.setHost("0.0.0.0"); // Bind only to IPv4
                connector.setPort(CONTENT_SERVER_PORT);
                connector.setIdleTimeout(CONNECTION_TIMEOUT);
                connector.setAcceptQueueSize(ACCEPT_QUEUE_SIZE);
                connector.setReuseAddress(true); // Better address reuse for quick restarts
               // connector.setAcceptorPriorityDelta(0); // Use normal thread priority

                server.setConnectors(new Connector[]{connector});

                // Resource handler for content streaming
                ResourceHandler resourceHandler = new ContentHandler();
                resourceHandler.setBaseResource(ResourceFactory.of(resourceHandler).newResource("/"));
                resourceHandler.setDirAllowed(false);
                resourceHandler.setAcceptRanges(true);  // Enable range requests for seeking
                resourceHandler.setEtags(true);         // Enable ETags for caching
                // Add this to your ResourceHandler configuration
                resourceHandler.setCacheControl("public, max-age=86400");
                resourceHandler.setPrecompressedFormats(new CompressedContentFormat[0]); // Disable compression for audio
                resourceHandler.setServer(server);

                server.setHandler(resourceHandler);
                server.setStopAtShutdown(true);
                server.setStopTimeout(5000);
                server.start();

                Log.i(TAG, "Content Server started on " + bindAddress.getHostAddress() + ":" + CONTENT_SERVER_PORT +" successfully.");

                server.join(); // Keep the thread alive until the server is stopped.
            } catch (Exception e) {
                Log.e(TAG, "Failed to start Content Server", e);
               // throw new RuntimeException(e);
            }
        });

        serverThread.setName("jetty-server-runner");
        serverThread.start();
    }

    public void stopServer() {
        // Stop the server.
        Log.i(TAG, "Stopping Content Server (Jetty)");

        try {
            if (server != null && server.isRunning()) {
                server.stop();
            }
        } catch (Exception e) {
            Log.e(TAG, "Error stopping Content Server", e);
           // throw new RuntimeException(e);
        }
    }

    @Override
    protected String getServerVersion() {
        return "Jetty/12.1.1";
    }

    @Override
    public int getListenPort() {
        return CONTENT_SERVER_PORT;
    }

    private class ContentHandler extends ResourceHandler {
        private ContentHandler() {
        }

        @Override
        public boolean handle(Request request, Response response, Callback callback) throws Exception {
            response.getHeaders().put(HttpHeader.SERVER, getFullServerName("ContentServer"));

            if (!HttpMethod.GET.is(request.getMethod()) && !HttpMethod.HEAD.is(request.getMethod())) {
                return super.handle(request, response, callback);
            }

            MusicTag tag = findMusicTagFromUri(request.getHttpURI().getPath());
            if (tag == null) {
                Log.w(TAG, "Content not found for URI: " + request.getHttpURI().getPath());
                return super.handle(request, response, callback);
            }

            File audioFile = new File(tag.getPath());
            if (!audioFile.exists() || !audioFile.canRead()) {
                Log.e(TAG, "Audio file not accessible: " + tag.getPath());
                response.setStatus(HttpStatus.NOT_FOUND_404);
                callback.succeeded();
                return true;
            }

            notifyPlaybackService(Request.getRemoteAddr(request), request.getHeaders().get(HttpHeader.USER_AGENT), tag);

            HttpContent content = getResourceService().getContent(tag.getPath(), request);
            if (content == null) {
                Log.w(TAG, "Jetty could not get content for path: " + tag.getPath());
                return super.handle(request, response, callback);
            }

            prepareResponseHeaders(response, tag);

            Log.i(TAG, "Starting stream: \"" + tag.getTitle() + "\" [" + formatAudioQuality(tag) + "] to " + Request.getRemoteAddr(request));
           // getResourceService().doGet(request, response, new StreamCompletionCallback(callback), content);
            getResourceService().doGet(request, response, callback, content);

            return true;
        }

        private MusicTag findMusicTagFromUri(String uri) {
            if (uri == null || !uri.startsWith("/res/")) {
                return null;
            }
            try {
                // IMPROVEMENT: More robust URI parsing to prevent exceptions.
                String pathPart = uri.substring(5); // Everything after "/res/"
                String[] parts = pathPart.split("/");
                if (parts.length > 0 && !parts[0].isEmpty()) {
                    long contentId = StringUtils.toLong(parts[0]);
                    return repos.findById(contentId); //MusixMateApp.getInstance().getOrmLite().findById(contentId);
                }
            } catch (Exception ex) {
                Log.e(TAG, "Failed to parse content ID from URI: " + uri, ex);
            }
            return null;
        }

        private void notifyPlaybackService(String clientIp, String userAgent, MusicTag tag) {
            RendererDevice device = mediaServer.getRendererByIpAddress(clientIp);
            Player player = (device != null) ?
                    Player.Factory.create(getContext(), device) :
                    Player.Factory.create(getContext(), clientIp, userAgent);
            mediaServer.getPlaybackService().onNewTrackPlaying(player, tag, 0);
        }

        private void prepareResponseHeaders(Response response, MusicTag tag) {
            String mimeType = MimeTypeUtils.getMimeTypeFromPath(tag.getPath());
            if (mimeType != null) {
                response.getHeaders().put(HttpHeader.CONTENT_TYPE, mimeType);
            }
            addDlnaHeaders(response, tag);
            addAudiophileHeaders(response, tag);
        }

        /**
         * Add DLNA-specific headers for optimal client compatibility
         */
        private void addDlnaHeaders(Response response, MusicTag tag) {
            // Common DLNA headers
            response.getHeaders().put("transferMode.dlna.org", "Streaming");
            response.getHeaders().put("contentFeatures.dlna.org", getDLNAContentFeatures(tag));

            // Some renderers need this to know they can seek
            response.getHeaders().put(HttpHeader.ACCEPT_RANGES, "bytes");
        }

        /**
         * Add audiophile-specific headers with detailed audio quality information
         */
        private void addAudiophileHeaders(Response response, MusicTag tag) {
            // Add custom headers with detailed audio information for audiophile clients
            if (tag.getAudioSampleRate() > 0) response.getHeaders().put("X-Audio-Sample-Rate", tag.getAudioSampleRate() + " Hz");
            if (tag.getAudioBitsDepth() > 0) response.getHeaders().put("X-Audio-Bit-Depth", tag.getAudioBitsDepth() + " bit");
            if (tag.getAudioBitRate() > 0) response.getHeaders().put("X-Audio-Bitrate", tag.getAudioBitRate() + " kbps");
            if (TagUtils.getChannels(tag) > 0) response.getHeaders().put("X-Audio-Channels", String.valueOf(TagUtils.getChannels(tag)));
            response.getHeaders().put("X-Audio-Format", Objects.toString(tag.getFileType(), ""));
        }

        /**
         * Format audio quality description based on tag properties
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

    private static class StreamCompletionCallback implements Callback {
        private final Callback delegate;
        public StreamCompletionCallback(Callback delegate) { this.delegate = delegate; }
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
}
