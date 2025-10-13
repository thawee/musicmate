package apincer.android.mmate.core.server;

import android.content.Context;
import android.util.Log;

import java.io.File;
import java.io.IOException;
import java.net.InetAddress;

import apincer.android.mmate.core.database.MusicTag;
import apincer.android.mmate.core.repository.TagRepository;
import apincer.android.mmate.core.utils.StringUtils;

public class DefaultContentServerImpl extends WebServer {
    private static final String TAG = "DefaultContentServerImpl";

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

    private HTTPServer server;
    private final TagRepository repos;

    public DefaultContentServerImpl(Context context, IMediaServer mediaServer) {
        super(context, mediaServer);
        repos = mediaServer.getTagReRepository();
    }

    public void initServer(InetAddress bindAddress) throws Exception {
        // Initialize the server with the specified port.
        Thread serverThread = new Thread(() -> {
            try {
                //Log.i(TAG, "Starting Content Server (Jetty) on " + bindAddress.getHostAddress() + ":" + CONTENT_SERVER_PORT);

                server = new HTTPServer(getListenPort());
                server.setSocketTimeout(30000); // 30 seconds

                HTTPServer.VirtualHost host = server.getVirtualHost(null);  // default virtual host
                host.setAllowGeneratedIndex(false);
                host.addContext("/{*}", new ContentHandler(), "GET", "POST");
                server.start();

                Log.i(TAG, "Content Server started on " + bindAddress.getHostAddress() + ":" + CONTENT_SERVER_PORT +" successfully.");

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
            if (server != null) {
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

    private class ContentHandler implements HTTPServer.ContextHandler {
        @Override
        public int serve(HTTPServer.Request req, HTTPServer.Response resp) throws IOException {
            MusicTag tag = findMusicTagFromUri(req.getPath());
            if (tag == null) {
                Log.w(TAG, "Content not found for URI: " + req.getPath());
                return 404;
            }

            File audioFile = new File(tag.getPath());
            return HTTPServer.serveFile(audioFile, "/album/", req, resp);
        }
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
}
