package apincer.android.mmate.dlna.transport;

import static java.nio.charset.StandardCharsets.UTF_8;
import static apincer.android.mmate.dlna.transport.StreamServerImpl.TYPE_IMAGE_JPEG;
import static apincer.android.mmate.dlna.transport.StreamServerImpl.TYPE_IMAGE_PNG;
import static apincer.android.mmate.utils.StringUtils.isEmpty;

import android.content.Context;
import android.util.Log;

import org.eclipse.jetty.http.HttpField;
import org.eclipse.jetty.http.HttpHeader;
import org.eclipse.jetty.http.HttpStatus;
import org.eclipse.jetty.io.Content;
import org.eclipse.jetty.server.Connector;
import org.eclipse.jetty.server.Handler;
import org.eclipse.jetty.server.HttpConfiguration;
import org.eclipse.jetty.server.HttpConnectionFactory;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.Response;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.server.handler.ContextHandler;
import org.eclipse.jetty.server.handler.ContextHandlerCollection;
import org.eclipse.jetty.util.Callback;
import org.jupnp.model.message.StreamRequestMessage;
import org.jupnp.model.message.StreamResponseMessage;
import org.jupnp.model.message.UpnpHeaders;
import org.jupnp.model.message.UpnpMessage;
import org.jupnp.model.message.UpnpRequest;
import org.jupnp.transport.Router;
import org.jupnp.transport.spi.InitializationException;
import org.jupnp.transport.spi.UpnpStream;

import java.io.File;
import java.io.IOException;
import java.net.InetAddress;
import java.net.URI;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.Map;

import apincer.android.mmate.MusixMateApp;
import apincer.android.mmate.dlna.MediaServerSession;
import apincer.android.mmate.provider.CoverArtProvider;
import apincer.android.mmate.repository.MusicTag;
import apincer.android.utils.FileUtils;
import de.esoco.lib.expression.Action;

public class JettyUPnpServer extends StreamServerImpl.UPnpServer {
    private static final String TAG = "JettyUPnpServer";

    private Server server;
    private static final String httpServerName="Jetty/12.1.0.alpha0";

    public JettyUPnpServer(Context context, Router router, StreamServerConfigurationImpl configuration) {
        super(context, router, configuration);
    }

    @Override
    public void initServer(InetAddress bindAddress) throws InitializationException {
        // Initialize the server with the specified port.
        Thread thread = new Thread(() -> {
            try {
                Log.i(TAG, "  Start UPNP Server (Jetty): " + bindAddress.getHostAddress() + ":" + getListenPort());

                server = new Server();

                // HTTP Configuration
                HttpConfiguration httpConfig = new HttpConfiguration();
                httpConfig.setOutputBufferSize(32768);
                httpConfig.setSendDateHeader(true);
                httpConfig.setSendServerVersion(false);
                httpConfig.setSendXPoweredBy(false);

                // HTTP connector
                // The first server connector we create is the one for http, passing in
                // the http configuration we configured above so it can get things like
                // the output buffer size, etc. We also set the port (8080) and
                // configure an idle timeout.
                try (ServerConnector http = new ServerConnector(server,
                        new HttpConnectionFactory(httpConfig))) {
                    http.setPort(getListenPort());
                    http.setIdleTimeout(30000);

                    server.setConnectors(new Connector[]{http});
                }

                ContextHandler upnpContext = new ContextHandler("/dms");
                upnpContext.setHandler(new UPnpHandler());

                ContextHandler albumContext = new ContextHandler("/coverart");
                albumContext.setHandler(new CoverArtHandler());

                ContextHandlerCollection contexts = new ContextHandlerCollection(
                        upnpContext, albumContext
                );

                server.setHandler(contexts);
                server.start();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });

        thread.start();
    }

    @Override
    public void stopServer() {
        // Stop the server.
        try {
            Log.i(TAG, "  Stop UPNP Server (Jetty)");
            server.stop();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private class UPnpHandler extends Handler.Abstract {
        private final UpnpStream upnpStream;

        private UPnpHandler( ) {
            this.upnpStream = new UpnpStream(getProtocolFactory()) {
                @Override
                public void run() {

                }
            };
        }

        @Override
        public boolean handle(Request request, Response response, Callback callback) {
            try {
                String userAgent = request.getHeaders().get(HttpHeader.USER_AGENT);
                if("CyberGarage-HTTP/1.0".equals(userAgent)) { // ||
                    // "Panasonic iOS VR-CP UPnP/2.0".equals(userAgent)) {//     requestMessage.getHeaders().getFirstHeader("User-agent"))) {
                    // Log.v(TAG, "Interim FIX for MConnect on IPadOS 18 beta, return all songs for MConnect(fix show only 20 songs)");
                    MediaServerSession.forceFullContent = true;
                }

                StreamRequestMessage requestMessage = readRequestMessage(request);
                // Log.v(TAG, "Processing new request message: " + requestMessage);

                StreamResponseMessage responseMessage = upnpStream.process(requestMessage);

                if (responseMessage != null) {
                    // Log.v(TAG, "Preparing HTTP response message: " + responseMessage);
                    writeResponseMessage(responseMessage, response, callback);
                } else {
                    // Declare response encoding and types
                    response.getHeaders().put(HttpHeader.CONTENT_TYPE, "text/html; charset=utf-8");
                    // Declare response status code
                    response.setStatus(HttpStatus.NOT_FOUND_404);
                    // Write back response
                    Content.Sink.write(response, true, "<h1>File Not Found</h1>\n", callback);
                }
            } catch (Throwable t) {
                Log.e(TAG, "Exception occurred during UPnP stream processing: ", t);
               // resp.sendError(500, t.getMessage());
                //upnpStream.responseException(t);
                // Declare response encoding and types
                response.getHeaders().put(HttpHeader.CONTENT_TYPE, "text/html; charset=utf-8");
                // Declare response status code
                response.setStatus(HttpStatus.INTERNAL_SERVER_ERROR_500);
                // Write back response
                Content.Sink.write(response, true, "<h1>"+t.getMessage()+"</h1>\n", callback);
            }
            return true;
        }

        private void writeResponseMessage(StreamResponseMessage responseMessage, Response resp, Callback callback) {
            int statusCode = responseMessage.getOperation().getStatusCode();

            // Headers
            for (Map.Entry<String, List<String>> entry : responseMessage.getHeaders().entrySet()) {
                for (String value : entry.getValue()) {
                    if(HttpHeader.SERVER.name().equalsIgnoreCase(entry.getKey())) {
                        // add server
                        // String sName = String.format("%s %s %s",getServerName(),value,SERVER_SUFFIX);
                        resp.getHeaders().add(HttpHeader.SERVER, getFullServerName(httpServerName));
                    }else {
                        resp.getHeaders().add(entry.getKey(), value);
                    }
                }
            }

            // cache control
            resp.getHeaders().add(HttpHeader.PRAGMA, "no-cache");
            resp.getHeaders().add(HttpHeader.CACHE_CONTROL, "no-store, no-cache, must-revalidate");
            resp.getHeaders().add(HttpHeader.LAST_MODIFIED, "0");
            resp.getHeaders().add(HttpHeader.EXPIRES, "0");

            // The Date header is recommended in UDA, jetty already add date header
            //Calendar time = new GregorianCalendar();
            //resp.getHeaders().add(HttpHeader.DATE, dateFormatter.format(time.getTime()));

            // Body
            byte[] responseBody = responseMessage.hasBody() ? responseMessage.getBodyBytes() : null;
            int contentLength = responseBody != null ? responseBody.length : -1;

            if (contentLength > 0) {

                // Declare response status code
                resp.setStatus(statusCode);
                // Write back response
                resp.write(true, ByteBuffer.wrap(responseBody), callback);
                //Content.Sink sink = Content.Sink.from(resp.);
                //Content.Sink.write(true, ByteBuffer.wrap(responseBody), callback);
            }
        }

        private StreamRequestMessage readRequestMessage(Request req) throws IOException {
            String requestMethod = req.getMethod();
            String requestURI = req.getHttpURI().getPath();
            StreamRequestMessage requestMessage;
            try {
                requestMessage =
                        new StreamRequestMessage(
                                UpnpRequest.Method.getByHttpName(requestMethod),
                                URI.create(requestURI)
                        );

            } catch (IllegalArgumentException ex) {
                throw new RuntimeException("Invalid request URI: " + requestURI, ex);
            }

            if (requestMessage.getOperation().getMethod().equals(UpnpRequest.Method.UNKNOWN)) {
                throw new RuntimeException("Method not supported: " + requestMethod);
            }

            UpnpHeaders headers = new UpnpHeaders();
            req.getHeaders().stream().iterator().forEachRemaining((Action<HttpField>) rValue -> headers.add(rValue.getName(), rValue.getValue()));
            requestMessage.setHeaders(headers);

            // Body
            // Non-blocking read the request content as a String.
            // Use with caution as the request content may be large.
            String body = Content.Source.asString(req, UTF_8);

            byte[] bodyBytes = new byte[]{};
            if (body!=null && !isEmpty(body)) {
                bodyBytes = body.getBytes(UTF_8);
            }

            if (bodyBytes.length > 0 && requestMessage.isContentTypeMissingOrText()) {

                // Log.v(TAG, "Request contains textual entity body, converting then setting string on message");
                requestMessage.setBodyCharacters(bodyBytes);

            } else if (bodyBytes.length > 0) {
                requestMessage.setBody(UpnpMessage.BodyType.BYTES, bodyBytes);
            }
            return requestMessage;
        }
    }

    private class CoverArtHandler extends Handler.Abstract {
        @Override
        public boolean handle(Request request, Response response, Callback callback) {
            String albumId = request.getHttpURI().getPath().substring("/coverart/".length());
            try {

                String path = CoverArtProvider.COVER_ARTS + albumId;
                File pathFile = new File(getCoverartDir(), path);
                if (pathFile.exists()) {
                    ByteBuffer buffer = FileUtils.getBytes(pathFile);
                    response.setStatus(HttpStatus.OK_200);
                    response.getHeaders().put(HttpHeader.CONTENT_TYPE,TYPE_IMAGE_PNG);
                    response.getHeaders().put(HttpHeader.CONTENT_LENGTH, buffer.capacity());
                    response.write(true, buffer, callback);
                    return true;
                }
            } catch (Exception e) {
                Log.e(TAG, "lookupAlbumArt: - no cover art found for uri: " + request.getHttpURI().getPath(), e);
            }

            ByteBuffer buffer = ByteBuffer.wrap(getDefaultIcon(albumId));
            response.setStatus(HttpStatus.OK_200);
            response.getHeaders().put(HttpHeader.CONTENT_TYPE,TYPE_IMAGE_PNG);
            response.getHeaders().put(HttpHeader.CONTENT_LENGTH, buffer.capacity());
            response.getHeaders().put(HttpHeader.CONTENT_TYPE,TYPE_IMAGE_JPEG);
            response.write(true, buffer, callback);
            return true;
        }

        private byte[] getDefaultIcon(String albumId) {
            if(albumId.contains(".")) {
                albumId = albumId.substring(0, albumId.indexOf("."));
            }
            MusicTag tag = MusixMateApp.getInstance().getOrmLite().findByAlbumUniqueKey(albumId);
            return MusixMateApp.getInstance().getDefaultNoCoverart(tag);
        }
    }
}
