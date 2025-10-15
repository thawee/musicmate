package apincer.music.server.jupnp.transport;

import android.content.Context;
import android.util.Log;

import org.apache.commons.io.IOUtils;
import org.jupnp.model.message.StreamRequestMessage;
import org.jupnp.model.message.StreamResponseMessage;
import org.jupnp.model.message.UpnpHeaders;
import org.jupnp.model.message.UpnpMessage;
import org.jupnp.model.message.UpnpRequest;
import org.jupnp.protocol.ProtocolFactory;
import org.jupnp.protocol.ReceivingSync;
import org.jupnp.transport.Router;
import org.jupnp.transport.spi.InitializationException;

import java.io.IOException;
import java.net.InetAddress;
import java.net.URI;
import java.util.List;
import java.util.Map;

import apincer.music.core.http.HTTPServer;
import apincer.music.core.server.BaseServer;
import apincer.music.core.server.spi.UpnpServer;

public class DefaultUPnpServerImpl extends BaseServer implements UpnpServer {
    private static final String TAG = "DefaultUPnpServerImpl";

    private HTTPServer server;
    private Router router;

    public DefaultUPnpServerImpl(Context context) {
        super(context);
        addLibInfo("JLHTTP", "3.2");
    }

    @Override
    public void initServer(InetAddress bindAddress, Object router) throws InitializationException {
        Thread serverThread = new Thread(() -> {
            try {
                this.router = (Router) router;
                server = new HTTPServer(getListenPort());
               // server.setSocketTimeout(30000); // 30 seconds

                HTTPServer.VirtualHost host = server.getDefaultHost();  // default virtual host
                host.setAllowGeneratedIndex(false);
                host.addContext("/{*}", new UpnpStreamHandler(this.router.getProtocolFactory()), "GET", "POST");
                server.start();

                Log.i(TAG, "UPnP Server started on " + bindAddress.getHostAddress() + ":" + getListenPort() + " successfully.");
            } catch (IOException e) {
                Log.e(TAG, "Failed to start or run UPnP server", e);
            }
        });
        serverThread.setName("nanohttpd-upnp-runner");
        serverThread.start();
    }

    @Override
    public void stopServer() {
        Log.i(TAG, "Stopping UPnP Server");
        if (server != null) {
            server.stop();
        }
    }

    @Override
    public int getListenPort() {
        return UPNP_SERVER_PORT;
    }

    @Override
    public String getComponentName() {
        return "java.net";
    }

    private class UpnpStreamHandler implements HTTPServer.ContextHandler {
        ProtocolFactory protocolFactory;
        protected UpnpStreamHandler(ProtocolFactory protocolFactory) {
            super();
            this.protocolFactory = protocolFactory;
        }

        @Override
        public int serve(HTTPServer.Request req, HTTPServer.Response resp) throws IOException {
            try {

                StreamRequestMessage requestMessage = readRequestMessage(req);
                // Log.v(TAG, "Processing new request message: " + requestMessage);
                // IMPROVEMENT: Use the protocol factory to create and run the right processor.
                // This is the most robust way to handle requests in jupnp.
                ReceivingSync protocol = protocolFactory.createReceivingSync(requestMessage);
                protocol.run();
                StreamResponseMessage responseMessage = protocol.getOutputMessage();

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
              //  protocol.responseException(t);
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
                       // String sName = String.format("%s %s %s",SERVER_SUFFIX,value,SERVER_SUFFIX);
                        resp.getHeaders().add("Server", getServerSignature(getComponentName()));
                    }else {
                        resp.getHeaders().add(entry.getKey(), value);
                    }
                }
            }

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
                headers.add(header.name(), header.value());
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
    }
}