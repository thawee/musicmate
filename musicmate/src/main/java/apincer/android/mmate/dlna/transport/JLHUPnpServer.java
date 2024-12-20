package apincer.android.mmate.dlna.transport;

import android.content.Context;
import android.util.Log;

import net.freeutils.httpserver.HTTPServer;

import org.apache.commons.io.IOUtils;
import org.jupnp.model.message.StreamRequestMessage;
import org.jupnp.model.message.StreamResponseMessage;
import org.jupnp.model.message.UpnpHeaders;
import org.jupnp.model.message.UpnpMessage;
import org.jupnp.model.message.UpnpRequest;
import org.jupnp.protocol.ProtocolFactory;
import org.jupnp.transport.Router;
import org.jupnp.transport.spi.InitializationException;
import org.jupnp.transport.spi.UpnpStream;

import java.io.File;
import java.io.IOException;
import java.net.InetAddress;
import java.net.URI;
import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.List;
import java.util.Map;

import apincer.android.mmate.dlna.MediaServerSession;
import apincer.android.mmate.provider.CoverArtProvider;

public class JLHUPnpServer extends StreamServerImpl.UPnpServer {
    private static final String TAG = "JLHUPnpServer";
    private final HTTPServer server;

    public JLHUPnpServer(Context context, Router router, StreamServerConfigurationImpl configuration)  {
        super(context, router, configuration);
        this.server = new HTTPServer(configuration.getListenPort());
    }

    synchronized public void initServer(InetAddress bindAddress) throws InitializationException {
        Thread thread = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    Log.i(TAG, "Start UPNP Server (JLH): " + bindAddress.getHostAddress() + ":" + getListenPort());
                    server.setSocketTimeout(30000); // 30 seconds

                    HTTPServer.VirtualHost host = server.getVirtualHost(null);  // default virtual host
                    host.setAllowGeneratedIndex(false);
                    host.addContext("/{*}", new UpnpStreamHandler(getProtocolFactory()), "GET", "POST");
                    server.start();
                } catch (Exception ex) {
                    throw new InitializationException("Could not initialize " + getClass().getSimpleName() + ": " + ex, ex);
                }
            }
        });

        thread.start();
    }

    synchronized public void stopServer() {
        if(server != null) {
            Log.i(TAG, "Stop UPNP Server (JLH)");
            server.stop();
        }
    }

    private class UpnpStreamHandler extends UpnpStream implements HTTPServer.ContextHandler {
         protected UpnpStreamHandler(ProtocolFactory protocolFactory) {
            super(protocolFactory);
        }

        @Override
        public int serve(HTTPServer.Request req, HTTPServer.Response resp) throws IOException {
            if (req.getPath().startsWith("/coverart/")) {
                try {
                    String albumId = req.getPath().substring("/coverart/".length());
                    String path = CoverArtProvider.COVER_ARTS + albumId;
                    File pathFile = new File(getCoverartDir(), path);
                    if (pathFile.exists()) {
                        HTTPServer.serveFile(pathFile.getParentFile(), "/coverart/", req, resp);
                    }
                    return 0;
                } catch (Exception e) {
                    Log.e(TAG, "AlbumArt: - not found " + req.getPath(), e);
                }
            }else {
                return serveUPNP(req, resp);
            }
            return 0;
        }

        private int serveUPNP(HTTPServer.Request req, HTTPServer.Response resp) throws IOException {
            try {
                String userAgent = getUserAgent(req);
                if("CyberGarage-HTTP/1.0".equals(userAgent)) { // ||
                    // "Panasonic iOS VR-CP UPnP/2.0".equals(userAgent)) {//     requestMessage.getHeaders().getFirstHeader("User-agent"))) {
                   // Log.v(TAG, "Interim FIX for MConnect on IPadOS 18 beta, return all songs for MConnect(fix show only 20 songs)");
                    MediaServerSession.forceFullContent = true;
                }

                StreamRequestMessage requestMessage = readRequestMessage(req);
                // Log.v(TAG, "Processing new request message: " + requestMessage);

                StreamResponseMessage responseMessage = process(requestMessage);

                if (responseMessage != null) {
                    // Log.v(TAG, "Preparing HTTP response message: " + responseMessage);
                    writeResponseMessage(responseMessage, resp);
                } else {
                    // If it's null, it's 404
                  //  Log.v(TAG, "Sending HTTP response status: 404");
                    resp.send(404, "not found");
                }

            } catch (Throwable t) {
                Log.e(TAG, "Exception occurred during UPnP stream processing: ", t);
                resp.sendError(500, t.getMessage());
                responseException(t);
            }
            return 0;
        }

        private void writeResponseMessage(StreamResponseMessage responseMessage, HTTPServer.Response resp) throws IOException {
            int statusCode = responseMessage.getOperation().getStatusCode();

            // Headers
            for (Map.Entry<String, List<String>> entry : responseMessage.getHeaders().entrySet()) {
                for (String value : entry.getValue()) {
                    if("Server".equalsIgnoreCase(entry.getKey())) {
                        // add server
                       // String sName = String.format("%s %s %s",getServerName(),value,SERVER_SUFFIX);
                        resp.getHeaders().add("Server", getFullServerName(value));
                    }else {
                        resp.getHeaders().add(entry.getKey(), value);
                    }
                }
            }
            // The Date header is recommended in UDA
            Calendar time = new GregorianCalendar();
            resp.getHeaders().add("Date", dateFormatter.format(time.getTime()));

            // Body
            byte[] responseBodyBytes = responseMessage.hasBody() ? responseMessage.getBodyBytes() : null;
            int contentLength = responseBodyBytes != null ? responseBodyBytes.length : -1;

            if (contentLength > 0) {
               // resp.getHeaders().add("Content-Type", "application/xml");
                resp.sendHeaders(statusCode);
                resp.getOutputStream().write(responseBodyBytes);
                resp.getOutputStream().close();
            }
        }

        private StreamRequestMessage readRequestMessage(HTTPServer.Request req) throws IOException {
            String requestMethod = req.getMethod();
            String requestURI = req.getURI().getPath();
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
            HTTPServer.Headers requestHeaders = req.getHeaders();
            for (HTTPServer.Header header : requestHeaders) {
                headers.add(header.getName(), header.getValue());
            }
            requestMessage.setHeaders(headers);

            // Body
            byte[] bodyBytes = IOUtils.toByteArray(req.getBody());
            if (bodyBytes == null) {
                bodyBytes = new byte[]{};
            }
          //  Log.v(TAG, "Reading request body bytes: " + bodyBytes.length);

            if (bodyBytes.length > 0 && requestMessage.isContentTypeMissingOrText()) {

                // Log.v(TAG, "Request contains textual entity body, converting then setting string on message");
                requestMessage.setBodyCharacters(bodyBytes);

            } else if (bodyBytes.length > 0) {

                // Log.v(TAG, "Request contains binary entity body, setting bytes on message");
                requestMessage.setBody(UpnpMessage.BodyType.BYTES, bodyBytes);

          //  } else {
          //      Log.v(TAG, "Request did not contain entity body");
            }
            //  Log.v(TAG, "Request entity body: "+requestMessage.getBodyString());
            return requestMessage;
        }

        private String getUserAgent(HTTPServer.Request req) {
            return req.getHeaders().get("User-Agent");
        }

        @Override
        public void run() {

        }
    }
}