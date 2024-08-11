package apincer.android.mmate.dlna.transport;

import android.util.Log;

import org.eclipse.jetty.http.HttpField;
import org.eclipse.jetty.http.HttpFields;
import org.eclipse.jetty.http.HttpHeader;
import org.eclipse.jetty.io.Content;
import org.eclipse.jetty.server.Handler;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.Response;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.util.Callback;
import org.jupnp.model.message.StreamRequestMessage;
import org.jupnp.model.message.StreamResponseMessage;
import org.jupnp.model.message.UpnpHeaders;
import org.jupnp.model.message.UpnpMessage;
import org.jupnp.model.message.UpnpRequest;
import org.jupnp.protocol.ProtocolFactory;
import org.jupnp.transport.Router;
import org.jupnp.transport.spi.InitializationException;
import org.jupnp.transport.spi.StreamServer;
import org.jupnp.transport.spi.UpnpStream;

import java.io.IOException;
import java.net.InetAddress;
import java.net.URI;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.Map;

import apincer.android.mmate.dlna.content.ContentBrowser;

public class Jetty12StreamServer implements StreamServer<StreamServerConfigurationImpl> {
    private static final String TAG = "JettyStreamServer";

    final protected StreamServerConfigurationImpl configuration;
    protected int localPort;
    private Server server;

    public Jetty12StreamServer(StreamServerConfigurationImpl configuration) {
        this.configuration = configuration;
        this.localPort = configuration.getListenPort();
    }

    public StreamServerConfigurationImpl getConfiguration() {
        return configuration;
    }

    synchronized public void init(InetAddress bindAddress, final Router router) throws InitializationException {
        Thread thread = new Thread(new Runnable() {
            @Override
            public void run() {
                    try {
                        Log.i(TAG, "Adding upnp stream server connector: " + bindAddress.getHostAddress() + ":" + getConfiguration().getListenPort());
                        resetServer();
                        final ServerConnector connector = new ServerConnector(server);
                        connector.setIdleTimeout(300);
                        connector.setPort(getConfiguration().getListenPort());
                        connector.open();
                        server.addConnector(connector);
                        server.setHandler(new UpnpStreamHandler(router.getProtocolFactory()));
                        startIfNotRunning();
                    } catch (Exception ex) {
                        throw new InitializationException("Could not initialize " + getClass().getSimpleName() + ": " + ex, ex);
                    }
            }
        });

        thread.start();

    }

    synchronized public int getPort() {
        return this.localPort;
    }

    synchronized public void stop() {
       stopIfRunning();
    }

    public synchronized void startIfNotRunning() {
        if (!this.server.isStarted() && !this.server.isStarting()) {
            try {
                this.server.start();
            }
            catch (final Exception e) {
                throw new RuntimeException(e);
            }
        }
    }

    public synchronized void stopIfRunning() {
        if (!this.server.isStopped() && !this.server.isStopping()) {
            try {
                this.server.stop();
            }
            catch (final Exception e) {
                throw new RuntimeException(e);
            }
            finally {
                resetServer();
            }
        }
    }

    protected void resetServer() {
        this.server = new Server();
    }

    public void run() {
        //do nothing all stuff done in init
    }

    private static class UpnpStreamHandler extends  Handler.Abstract {
        private UpnpStream upnpStream;
        protected UpnpStreamHandler(ProtocolFactory protocolFactory) {
            super();
            upnpStream = new UpnpStream(protocolFactory) {
                @Override
                public void run() {

                }
            };
        }

        private void writeResponseMessage(StreamResponseMessage responseMessage, Response resp ) throws IOException {
            int statusCode = responseMessage.getOperation().getStatusCode();

            // Headers
            for (Map.Entry<String, List<String>> entry : responseMessage.getHeaders().entrySet()) {
                for (String value : entry.getValue()) {
                    resp.getHeaders().add(entry.getKey(), value);
                }
            }
            // The Date header is recommended in UDA
            resp.getHeaders().add("Date", "" + System.currentTimeMillis());

            // Body
            byte[] responseBodyBytes = responseMessage.hasBody() ? responseMessage.getBodyBytes() : null;
            int contentLength = responseBodyBytes != null ? responseBodyBytes.length : -1;

            if (contentLength > 0) {
                Log.v(TAG, "Response message has body, writing bytes to stream...");
                 Log.d(TAG, "Response message has body, "+new String(responseBodyBytes));

                String contentType = "application/xml; charset=UTF-8";
                if (responseMessage.getContentTypeHeader() != null) {
                    contentType = responseMessage.getContentTypeHeader().getValue().toString();
                }

                resp.getHeaders().put(HttpHeader.CONTENT_TYPE, contentType);
                resp.setStatus(statusCode);
                Content.Sink.write(resp, true, ByteBuffer.wrap(responseBodyBytes));
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
            HttpFields httpHeaders = req.getHeaders();
            for (HttpField header : httpHeaders) {
                headers.add(header.getName(), header.getValue());
            }
            requestMessage.setHeaders(headers);

            // Body
            byte[] bodyBytes = Content.Source.asByteBuffer(req).array();
            Log.v(TAG, "Reading request body bytes: " + bodyBytes.length);

            if (bodyBytes.length > 0 && requestMessage.isContentTypeMissingOrText()) {

                // Log.v(TAG, "Request contains textual entity body, converting then setting string on message");
                requestMessage.setBodyCharacters(bodyBytes);

            } else if (bodyBytes.length > 0) {

                // Log.v(TAG, "Request contains binary entity body, setting bytes on message");
                requestMessage.setBody(UpnpMessage.BodyType.BYTES, bodyBytes);

            } else {
                Log.v(TAG, "Request did not contain entity body");
            }
            //  Log.v(TAG, "Request entity body: "+requestMessage.getBodyString());
            return requestMessage;
        }

        private String getUserAgent(Request req) {
            return req.getHeaders().get("User-Agent");
        }

        @Override
        public boolean handle(Request request, Response response, Callback callback) throws Exception {
            try {
                String userAgent = getUserAgent(request);
                if("CyberGarage-HTTP/1.0".equals(userAgent)) { // ||
                    // "Panasonic iOS VR-CP UPnP/2.0".equals(userAgent)) {//     requestMessage.getHeaders().getFirstHeader("User-agent"))) {
                    Log.v(TAG, "Interim FIX for MConnect on IPadOS 18 beta, return all songs for MConnect(fix show only 20 songs)");
                    ContentBrowser.forceFullContent = true;
                }

                StreamRequestMessage requestMessage = readRequestMessage(request);
                // Log.v(TAG, "Processing new request message: " + requestMessage);

                StreamResponseMessage responseMessage = upnpStream.process(requestMessage);

                if (responseMessage != null) {
                    // Log.v(TAG, "Preparing HTTP response message: " + responseMessage);
                    writeResponseMessage(responseMessage, response);
                } else {
                    // If it's null, it's 404
                    Log.v(TAG, "Sending HTTP response status: 404");
                    response.setStatus(404);
                    Content.Sink.write(response, true, "Not Found", callback);
                }

            } catch (Throwable t) {
                Log.e(TAG, "Exception occurred during UPnP stream processing: ", t);
                response.setStatus(500);
                Content.Sink.write(response, true, "INTERNAL SERVER ERROR", callback);
                //upnpStream.responseException(t);
            }


            // Succeed the callback to signal that the
            // request/response processing is complete.
            callback.succeeded();
            return true;
        }
    }
}