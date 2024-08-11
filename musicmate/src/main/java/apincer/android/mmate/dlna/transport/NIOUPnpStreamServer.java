package apincer.android.mmate.dlna.transport;

import android.util.Log;

import com.koushikdutta.async.ByteBufferList;
import com.koushikdutta.async.DataEmitter;
import com.koushikdutta.async.DataSink;
import com.koushikdutta.async.callback.DataCallback;
import com.koushikdutta.async.http.Multimap;
import com.koushikdutta.async.http.NameValuePair;
import com.koushikdutta.async.http.body.AsyncHttpRequestBody;
import com.koushikdutta.async.http.body.StringBody;
import com.koushikdutta.async.http.server.AsyncHttpServer;
import com.koushikdutta.async.http.server.HttpServerRequestCallback;
import com.koushikdutta.async.http.server.AsyncHttpServerRequest;
import com.koushikdutta.async.http.server.AsyncHttpServerResponse;
import com.koushikdutta.async.http.server.UnknownRequestBody;

import org.apache.commons.io.IOUtils;
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
import java.util.List;
import java.util.Map;

import apincer.android.mmate.dlna.content.ContentBrowser;
import apincer.android.mmate.utils.StringUtils;

public class NIOUPnpStreamServer implements StreamServer<StreamServerConfigurationImpl> {
    private static final String TAG = "NIOUPnpStreamServer";

    final protected StreamServerConfigurationImpl configuration;
    protected int localPort;
    AsyncHttpServer server;

    public NIOUPnpStreamServer(StreamServerConfigurationImpl configuration) {
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
                        server = new AsyncHttpServer();
                        HttpServerRequestCallback callback = new UPnpServerRequestCallback(router.getProtocolFactory());
                        server.get("/.*", callback);
                        server.post("/.*", callback);
                        server.listen(getConfiguration().getListenPort());
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
        if(server != null) {
            server.stop();
        }
    }

    public void run() {
        //do nothing all stuff done in init
    }

    private static class UPnpServerRequestCallback extends UpnpStream implements HttpServerRequestCallback {
        protected UPnpServerRequestCallback(ProtocolFactory protocolFactory) {
            super(protocolFactory);
        }

        @Override
        public void onRequest(AsyncHttpServerRequest request, AsyncHttpServerResponse response) {
            try {
                String userAgent = getUserAgent(request);
                if("CyberGarage-HTTP/1.0".equals(userAgent)) { // ||
                    // "Panasonic iOS VR-CP UPnP/2.0".equals(userAgent)) {//     requestMessage.getHeaders().getFirstHeader("User-agent"))) {
                    Log.v(TAG, "Interim FIX for MConnect on IPadOS 18 beta, return all songs for MConnect(fix show only 20 songs)");
                    ContentBrowser.forceFullContent = true;
                }

                StreamRequestMessage requestMessage = readRequestMessage(request);
                // Log.v(TAG, "Processing new request message: " + requestMessage);

                StreamResponseMessage responseMessage = process(requestMessage);

                if (responseMessage != null) {
                    // Log.v(TAG, "Preparing HTTP response message: " + responseMessage);
                    writeResponseMessage(responseMessage, response);
                } else {
                    // If it's null, it's 404
                    Log.v(TAG, "Sending HTTP response status: 404");
                    applyHeaders(response);
                    response.code(404);
                    response.send("not found");
                }

            } catch (Throwable t) {
                Log.e(TAG, "Exception occurred during UPnP stream processing: ", t);
                applyHeaders(response);
                response.code(500);
                response.send("INTERNAL SERVER ERROR");
                responseException(t);
            }
        }

        private void applyHeaders(AsyncHttpServerResponse response) {
            response.getHeaders().set("Expires:","-1");
            response.getHeaders().set("Cache-Control:","no-cache");
            response.getHeaders().set("Pragma:","no-cache");
            response.getHeaders().set("Connection:","keep-alive");
            response.getHeaders().set("Server","MusicMate Server");
        }

        private void writeResponseMessage(StreamResponseMessage responseMessage, AsyncHttpServerResponse resp) throws IOException {
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

                String contentType = "application/xml";
                if (responseMessage.getContentTypeHeader() != null) {
                    contentType = responseMessage.getContentTypeHeader().getValue().toString();
                }

                resp.code(statusCode);
                resp.send(contentType, responseBodyBytes);
            }
        }

        private StreamRequestMessage readRequestMessage(AsyncHttpServerRequest request) throws IOException {
            String requestMethod = request.getMethod();
            String requestURI = request.getPath();
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

            Multimap httpHeaders = request.getHeaders().getMultiMap();
            for (NameValuePair header : httpHeaders) {
                headers.add(header.getName(), header.getValue());
            }
            requestMessage.setHeaders(headers);

            // Body
            AsyncHttpRequestBody body = request.getBody();

            if(body instanceof StringBody) {
                 Log.v(TAG, "Request contains text body, converting then setting string on message");
                String message = ((StringBody)body).get();
                requestMessage.setBody(message);
            }else {
                requestMessage.setBodyCharacters(new byte[]{});
                request.setDataCallback(new DataCallback() {
                    @Override
                    public void onDataAvailable(DataEmitter emitter, ByteBufferList bb) {
                        requestMessage.setBody(UpnpMessage.BodyType.BYTES, bb.getAllByteArray());
                    }
                });
              //  if(body instanceof UnknownRequestBody) {
                    Log.v(TAG, "Request contains unknown body, converting then setting string on message");
              //      UnknownRequestBody ubody = (UnknownRequestBody) body;
                    // ubody.getEmitter().
               // }
            }
/*
            byte[] bodyBytes = null;

            //IOUtils.toByteArray(request.getBody().);
            if (bodyBytes == null) {
                bodyBytes = new byte[]{};
            }
             Log.v(TAG, "Reading request body bytes: " + bodyBytes.length);

            if (bodyBytes.length > 0 && requestMessage.isContentTypeMissingOrText()) {

                // Log.v(TAG, "Request contains textual entity body, converting then setting string on message");
                requestMessage.setBodyCharacters(bodyBytes);

            } else if (bodyBytes.length > 0) {

                // Log.v(TAG, "Request contains binary entity body, setting bytes on message");
                requestMessage.setBody(UpnpMessage.BodyType.BYTES, bodyBytes);

            } else {
                Log.v(TAG, "Request did not contain entity body");
            } */
            //  Log.v(TAG, "Request entity body: "+requestMessage.getBodyString());
            return requestMessage;
        }

        private String getUserAgent(AsyncHttpServerRequest request) {
            return request.getHeaders().get("User-Agent");
        }

        @Override
        public void run() {

        }
    }
}