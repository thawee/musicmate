package apincer.android.mmate.dlna.transport;

import static apincer.android.mmate.dlna.MediaServerConfiguration.CONTENT_SERVER_PORT;

import android.content.Context;
import android.util.Log;

import androidx.core.content.ContextCompat;

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
import org.jupnp.transport.Router;
import org.jupnp.transport.spi.InitializationException;

import java.io.File;
import java.net.InetAddress;

import apincer.android.mmate.MusixMateApp;
import apincer.android.mmate.R;
import apincer.android.mmate.player.PlayerInfo;
import apincer.android.mmate.repository.MusicTag;
import apincer.android.mmate.utils.MimeTypeUtils;
import apincer.android.mmate.utils.StringUtils;

import java.util.HashMap;
import java.util.Map;

public class JettyContentServerImpl extends StreamServerImpl.StreamServer {
    private static final String TAG = "JettyContentServer";

    // Optimized server configuration
    private static final int OUTPUT_BUFFER_SIZE = 131072; // 128KB for better streaming performance
    private static final int MAX_THREADS = 30;           // Thread pool size for concurrent streams
    private static final int MIN_THREADS = 6;            // Minimum threads to keep ready
    private static final int IDLE_TIMEOUT = 60000;       // 60 seconds idle timeout
    private static final int CONNECTION_TIMEOUT = 300000; // 5 minute connection timeout

    // Track current streaming clients
    private final Map<String, PlayerInfo> activeStreamers = new HashMap<>();

    public JettyContentServerImpl(Context context, Router router, StreamServerConfigurationImpl configuration) {
        super(context, router, configuration);
    }

    private Server server;

    public void initServer(InetAddress bindAddress) throws InitializationException {
        // Initialize the server with the specified port.
        Thread thread = new Thread(() -> {
            try {
                Log.i(TAG, "  Start Content Server (Jetty): " + bindAddress.getHostAddress() + ":" + CONTENT_SERVER_PORT);

                // Configure thread pool optimized for audio streaming
                QueuedThreadPool threadPool = new QueuedThreadPool();
                threadPool.setMaxThreads(MAX_THREADS);
                threadPool.setMinThreads(MIN_THREADS);
                threadPool.setIdleTimeout(IDLE_TIMEOUT);
                threadPool.setName("content-server");

                server = new Server(threadPool);

                // HTTP Configuration optimized for media streaming
                HttpConfiguration httpConfig = new HttpConfiguration();
                httpConfig.setOutputBufferSize(OUTPUT_BUFFER_SIZE);
                httpConfig.setSendDateHeader(true);
                httpConfig.setSendServerVersion(false);
                httpConfig.setSendXPoweredBy(true);
                httpConfig.setRequestHeaderSize(8192);

                // HTTP connector with optimized settings for stable streaming
                try (ServerConnector connector = new ServerConnector(server,
                        new HttpConnectionFactory(httpConfig))) {
                    connector.setHost("0.0.0.0"); // Bind only to IPv4
                    connector.setPort(CONTENT_SERVER_PORT);
                    connector.setIdleTimeout(CONNECTION_TIMEOUT);
                    connector.setAcceptQueueSize(128);

                    server.setConnectors(new Connector[]{connector});
                }

                // Resource handler for content streaming
                ResourceHandler resourceHandler = new ContentHandler();
                resourceHandler.setBaseResource(ResourceFactory.of(resourceHandler).newResource("/"));
                resourceHandler.setDirAllowed(false);
                resourceHandler.setAcceptRanges(true);  // Enable range requests for seeking
                resourceHandler.setEtags(true);         // Enable ETags for caching
                resourceHandler.setServer(server);

                server.setHandler(resourceHandler);
                server.start();
                Log.i(TAG, "Content Server started successfully");
            } catch (Exception e) {
                Log.e(TAG, "Failed to start Content Server", e);
                throw new RuntimeException(e);
            }
        });

        thread.start();
    }

    public void stopServer() {
        // Stop the server.
        try {
            Log.i(TAG, "  Stop Content Server (Jetty)");
            if (server != null && server.isRunning()) {
                server.stop();
                activeStreamers.clear();
            }
        } catch (Exception e) {
            Log.e(TAG, "Error stopping Content Server", e);
            throw new RuntimeException(e);
        }
    }

    @Override
    protected String getServerVersion() {
        return "Jetty/12.0.1";
    }

    private class ContentHandler extends ResourceHandler {
        private ContentHandler( ) {
        }

        @Override
        public boolean handle(Request request, Response response, Callback callback) throws Exception {
            // Set server identity
            response.getHeaders().put(HttpHeader.SERVER, getFullServerName());

            // Only handle GET and HEAD requests
            if (!HttpMethod.GET.is(request.getMethod()) && !HttpMethod.HEAD.is(request.getMethod()))  {
                return super.handle(request, response, callback);
            }

            String clientAddress = Request.getRemoteAddr(request);
            String uri = request.getHttpURI().getPath();
            String userAgent = request.getHeaders().get(HttpHeader.USER_AGENT);

            HttpContent content = null;

            if(uri.startsWith("/res/")) {
                try {
                    String contentId = uri.substring(5, uri.indexOf("/", 5));
                    MusicTag tag = MusixMateApp.getInstance().getOrmLite().findById(StringUtils.toLong(contentId));

                    if (tag != null) {
                        File audioFile = new File(tag.getPath());

                        if (!audioFile.exists() || !audioFile.canRead()) {
                            Log.e(TAG, "Audio file not accessible: " + tag.getPath());
                            response.setStatus(HttpStatus.NOT_FOUND_404);
                            return true;
                        }

                        // Create PlayerInfo for tracking
                        PlayerInfo player = PlayerInfo.buildStreamPlayer(
                                userAgent,
                                ContextCompat.getDrawable(getContext(), R.drawable.img_upnp_white)
                        );

                        // Store in active streamers
                        activeStreamers.put(clientAddress, player);

                        // Publish currently playing song
                        MusixMateApp.getPlayerControl().publishPlayingSong(player, tag);

                        // Prepare content
                        content = getResourceService().getContent(tag.getPath(), request);

                        // Set appropriate MIME type based on file extension
                        String mimeType = MimeTypeUtils.getMimeTypeFromPath(tag.getPath());
                        if (mimeType != null) {
                            response.getHeaders().put(HttpHeader.CONTENT_TYPE, mimeType);
                        }

                        // Add DLNA headers for better client compatibility
                        addDlnaHeaders(response, tag);
                    } else {
                        Log.w(TAG, "Content not found: " + contentId);
                        //response.setStatus(HttpStatus.NOT_FOUND_404);
                        //return true;
                        return super.handle(request, response, callback);
                    }
                } catch (Exception ex) {
                    Log.e(TAG, "lookupContent: - " + uri, ex);
                    return super.handle(request, response, callback);
                }
            }

            if (content == null) {
                Log.w(TAG, "Content not found");
                //response.setStatus(HttpStatus.NOT_FOUND_404);

                //return true;
                return super.handle(request, response, callback);
            }else {
                // Set resource-based headers
                response.getHeaders().put(HttpHeader.ACCEPT_RANGES, "bytes");
                getResourceService().doGet(request, response, callback, content);

                return true;
            }
        }

        /**
         * Add DLNA-specific headers for optimal client compatibility
         */
        private void addDlnaHeaders(Response response, MusicTag tag) {
            // Common DLNA headers
            response.getHeaders().put("transferMode.dlna.org", "Streaming");
            response.getHeaders().put(HttpHeader.CONNECTION, "keep-alive");

            String path = tag.getPath().toLowerCase();
            String contentFeatures;

            // Format-specific DLNA parameters based on audio type
            if (path.endsWith(".flac")) {
                contentFeatures = "DLNA.ORG_PN=FLAC;DLNA.ORG_OP=01;DLNA.ORG_CI=0;DLNA.ORG_FLAGS=01700000000000000000000000000000";
            } else if (path.endsWith(".mp3")) {
                contentFeatures = "DLNA.ORG_PN=MP3;DLNA.ORG_OP=01;DLNA.ORG_CI=0;DLNA.ORG_FLAGS=01700000000000000000000000000000";
            } else if (path.endsWith(".wav") || path.endsWith(".wave")) {
                contentFeatures = "DLNA.ORG_PN=WAV;DLNA.ORG_OP=01;DLNA.ORG_CI=0;DLNA.ORG_FLAGS=01700000000000000000000000000000";
            } else if (path.endsWith(".m4a") || path.endsWith(".aac")) {
                contentFeatures = "DLNA.ORG_PN=AAC_ISO;DLNA.ORG_OP=01;DLNA.ORG_CI=0;DLNA.ORG_FLAGS=01700000000000000000000000000000";
            } else if (path.endsWith(".aiff") || path.endsWith(".aif")) {
                contentFeatures = "DLNA.ORG_PN=AIFF;DLNA.ORG_OP=01;DLNA.ORG_CI=0;DLNA.ORG_FLAGS=01700000000000000000000000000000";
            } else {
                // Generic audio content features
                contentFeatures = "DLNA.ORG_OP=01;DLNA.ORG_CI=0;DLNA.ORG_FLAGS=01700000000000000000000000000000";
            }

            response.getHeaders().put("contentFeatures.dlna.org", contentFeatures);
        }
    }
}
