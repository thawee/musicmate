package apincer.android.mmate.dlna.transport;

import static apincer.android.mmate.dlna.MediaServerConfiguration.CONTENT_SERVER_PORT;

import android.content.Context;
import android.util.Log;

import androidx.core.content.ContextCompat;

import org.eclipse.jetty.http.HttpHeader;
import org.eclipse.jetty.http.HttpMethod;
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
import org.jupnp.transport.Router;
import org.jupnp.transport.spi.InitializationException;

import java.net.InetAddress;

import apincer.android.mmate.MusixMateApp;
import apincer.android.mmate.R;
import apincer.android.mmate.player.PlayerInfo;
import apincer.android.mmate.repository.MusicTag;
import apincer.android.mmate.utils.StringUtils;

public class JettyContentServerImpl extends StreamServerImpl.StreamServer {
    private static final String TAG = "JettyContentServer";

    public JettyContentServerImpl(Context context, Router router, StreamServerConfigurationImpl configuration) {
        super(context, router, configuration);
    }


    private Server server;

    public void initServer(InetAddress bindAddress) throws InitializationException {
        // Initialize the server with the specified port.
        Thread thread = new Thread(() -> {
            try {
                Log.i(TAG, "  Start Content Server (Jetty): " + bindAddress.getHostAddress() + ":" + CONTENT_SERVER_PORT);

                server = new Server();

                // HTTP Configuration
                HttpConfiguration httpConfig = new HttpConfiguration();
                //httpConfig.setOutputBufferSize(32768);
                httpConfig.setSendDateHeader(true);
                httpConfig.setSendServerVersion(false);
                httpConfig.setSendXPoweredBy(true);

                // HTTP connector
                // The first server connector we create is the one for http, passing in
                // the http configuration we configured above so it can get things like
                // the output buffer size, etc. We also set the port (8080) and
                // configure an idle timeout.
                try (ServerConnector http = new ServerConnector(server,
                        new HttpConnectionFactory(httpConfig))) {
                    http.setPort(CONTENT_SERVER_PORT);
                    http.setIdleTimeout(30000);
                    http.setAcceptQueueSize(500); // Increase the accept queue size

                    server.setConnectors(new Connector[]{http});
                }

                // Resource handler for cover art
                ResourceHandler resourceHandler = new ContentHandler();
                resourceHandler.setBaseResource(ResourceFactory.of(resourceHandler).newResource("/"));
                resourceHandler.setDirAllowed(false);
                resourceHandler.setAcceptRanges(true);
                resourceHandler.setEtags(true);
                resourceHandler.setServer(server);

                server.setHandler(resourceHandler);
                server.start();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });

        thread.start();
    }

    public void stopServer() {
        // Stop the server.
        try {
            Log.i(TAG, "  Stop Content Server (Jetty)");
            server.stop();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private class ContentHandler extends ResourceHandler {
        private ContentHandler( ) {
        }

        @Override
        public boolean handle(Request request, Response response, Callback callback) throws Exception {
            response.getHeaders().put(HttpHeader.SERVER, getFullServerName());
            if (!HttpMethod.GET.is(request.getMethod()) && !HttpMethod.HEAD.is(request.getMethod()))
            {
                // try another handler
                return super.handle(request, response, callback);
            }

            HttpContent content = null;
            String uri = request.getHttpURI().getPath();
            if(uri.startsWith("/res/")) {
                try {
                    String agent = request.getHeaders().get(HttpHeader.USER_AGENT);
                    String contentId = uri.substring(5, uri.indexOf("/", 5));
                    MusicTag tag = MusixMateApp.getInstance().getOrmLite().findById(StringUtils.toLong(contentId));
                    if (tag != null) {
                        PlayerInfo player = PlayerInfo.buildStreamPlayer(agent, ContextCompat.getDrawable(getContext(), R.drawable.img_upnp));
                        MusixMateApp.getPlayerControl().publishPlayingSong(player, tag);
                        content = getResourceService().getContent(tag.getPath(), request);
                    }
                } catch (Exception ex) {
                    Log.e(TAG, "lookupContent: - " + uri, ex);
                }
            }

            if (content == null)
            {
                return super.handle(request, response, callback); // no content - try other handlers
            }

            getResourceService().doGet(request, response, callback, content);
            return true;
        }
    }
}
