package apincer.android.mmate.dlna.transport;

import static java.nio.charset.StandardCharsets.UTF_8;
import static java.nio.file.StandardCopyOption.REPLACE_EXISTING;
import static apincer.android.mmate.Constants.COVER_ARTS;
import static apincer.android.mmate.Constants.DEFAULT_COVERART_DLNA_RES;
import static apincer.android.mmate.Constants.DEFAULT_COVERART_FILE;
import static apincer.android.mmate.utils.StringUtils.isEmpty;

import android.content.Context;
import android.util.Log;

import org.eclipse.jetty.http.HttpHeader;
import org.eclipse.jetty.http.HttpMethod;
import org.eclipse.jetty.http.HttpStatus;
import org.eclipse.jetty.http.content.HttpContent;
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
import org.eclipse.jetty.server.handler.ResourceHandler;
import org.eclipse.jetty.util.Callback;
import org.eclipse.jetty.util.resource.ResourceFactory;
import org.eclipse.jetty.util.thread.QueuedThreadPool;
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
import java.io.InputStream;
import java.net.InetAddress;
import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.util.List;
import java.util.Map;

import apincer.android.mmate.MusixMateApp;
import apincer.android.mmate.repository.FileRepository;
import apincer.android.mmate.repository.MusicTag;
import apincer.android.mmate.utils.ApplicationUtils;
import apincer.android.utils.FileUtils;

public class JettyUPnpServerImpl extends StreamServerImpl.StreamServer {
    private static final String TAG = "JettyUPnpServer";

    private Server server;

    public JettyUPnpServerImpl(Context context, Router router, StreamServerConfigurationImpl configuration) {
        super(context, router, configuration);
    }

    @Override
    public void initServer(InetAddress bindAddress) throws InitializationException {
        // Initialize the server with the specified port.
        Thread thread = new Thread(() -> {
            try {
                Log.i(TAG, "  Start UPNP Server (Jetty): " + bindAddress.getHostAddress() + ":" + getListenPort());

                // Configure thread pool
                QueuedThreadPool threadPool = new QueuedThreadPool();
                threadPool.setMaxThreads(120); // Adjust based on your server's capacity
                threadPool.setMinThreads(10);
                threadPool.setIdleTimeout(60000);

                server = new Server(threadPool);

                // HTTP Configuration
                HttpConfiguration httpConfig = new HttpConfiguration();
               // httpConfig.setOutputBufferSize(32768);
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
                    http.setPort(getListenPort());
                    http.setIdleTimeout(30000);
                    http.setAcceptQueueSize(500); // Increase the accept queue size

                    server.setConnectors(new Connector[]{http});
                }

                ContextHandler upnpContext = new ContextHandler("/dms");
                upnpContext.setHandler(new UPnpHandler());

                // Resource handler for cover art
                ResourceHandler coverartHandler = new CoverartHandler();
                coverartHandler.setBaseResource(ResourceFactory.of(coverartHandler).newResource("/"));
                coverartHandler.setDirAllowed(false);
                coverartHandler.setServer(server);
                ContextHandler coverartContext = new ContextHandler("/coverart");
                coverartContext.setHandler(coverartHandler);

                ContextHandlerCollection contexts = new ContextHandlerCollection(
                        upnpContext, coverartContext
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
                    StreamServerImpl.forceFullContent = true;
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
                return true;
            } catch (Throwable t) {
                Log.e(TAG, "Exception occurred during UPnP stream processing: "+ t.getMessage());
               // resp.sendError(500, t.getMessage());
                //upnpStream.responseException(t);
                // Declare response encoding and types
                response.getHeaders().put(HttpHeader.CONTENT_TYPE, "text/html; charset=utf-8");
                // Declare response status code
                response.setStatus(HttpStatus.INTERNAL_SERVER_ERROR_500);
                // Write back response
                Content.Sink.write(response, true, "<h1>"+t.getMessage()+"</h1>\n", callback);
            }
            return false;
        }

        private void writeResponseMessage(StreamResponseMessage responseMessage, Response resp, Callback callback) {
            int statusCode = responseMessage.getOperation().getStatusCode();

            // Headers
            for (Map.Entry<String, List<String>> entry : responseMessage.getHeaders().entrySet()) {
                for (String value : entry.getValue()) {
                    if(HttpHeader.SERVER.name().equalsIgnoreCase(entry.getKey())) {
                        // add server
                        resp.getHeaders().add(HttpHeader.SERVER, getFullServerName());
                    }else {
                        resp.getHeaders().add(entry.getKey(), value);
                    }
                }
            }

            // cache control
            resp.getHeaders().add(HttpHeader.PRAGMA, "no-cache");
            resp.getHeaders().add(HttpHeader.CACHE_CONTROL, "must-revalidate,no-cache,no-store");
           // resp.getHeaders().add(HttpHeader.LAST_MODIFIED, "0");
           // resp.getHeaders().add(HttpHeader.EXPIRES, "0");

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
              //  Log.d(TAG, new String(responseBody));
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
            req.getHeaders().stream().iterator().forEachRemaining(httpField -> headers.add(httpField.getName(), httpField.getValue()));
            requestMessage.setHeaders(headers);

            // Body
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


    private class CoverartHandler extends ResourceHandler {
        File defaultCoverartDir;
        private CoverartHandler( ) {
            defaultCoverartDir = new File(getCoverartDir(COVER_ARTS),DEFAULT_COVERART_FILE);
        }

        @Override
        public boolean handle(Request request, Response response, Callback callback) throws Exception {
            if (!HttpMethod.GET.is(request.getMethod()) && !HttpMethod.HEAD.is(request.getMethod())) {
                // try another handler
                return super.handle(request, response, callback);
            }

            //extracted embed cover art
            HttpContent content = getResourceService().getContent(Request.getPathInContext(request), request);
            if (content == null) {
                // folder cover art
                String uri = request.getHttpURI().getPath();
                try {
                    String albumUniqueKey = uri.substring("/coverart/".length(), uri.indexOf(".png"));
                    MusicTag tag = MusixMateApp.getInstance().getOrmLite().findByAlbumUniqueKey(albumUniqueKey);
                    if (tag != null) {
                        File covertFile = FileRepository.getFolderCoverArt(tag.getPath());
                        if(covertFile !=null) {
                            content = getResourceService().getContent(covertFile.getAbsolutePath(), request);
                        }
                    }
                } catch (Exception ex) {
                    Log.e(TAG, "lookupContent: - " + uri, ex);
                }
            }

            if (content == null)
            {
                // default cover art
                // setup default cover art
                try {
                    if(!defaultCoverartDir.exists()) {
                        FileUtils.createParentDirs(defaultCoverartDir);
                        InputStream in = ApplicationUtils.getAssetsAsStream(getContext(), DEFAULT_COVERART_DLNA_RES);
                        Files.copy(in, defaultCoverartDir.toPath(), REPLACE_EXISTING);
                    }
                } catch (IOException e) {
                    Log.e(TAG, "Init default missing cover art", e);
                }

                content = getResourceService().getContent(defaultCoverartDir.getAbsolutePath(), request);
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
