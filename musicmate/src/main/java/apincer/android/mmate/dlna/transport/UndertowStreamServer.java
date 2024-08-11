package apincer.android.mmate.dlna.transport;

import android.util.Log;

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
import org.xnio.Options;

import java.io.IOException;
import java.net.InetAddress;
import java.net.URI;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.Map;

import apincer.android.mmate.dlna.content.ContentBrowser;
import io.undertow.Undertow;
import io.undertow.UndertowOptions;
import io.undertow.io.Receiver;
import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.util.HeaderMap;
import io.undertow.util.Headers;
import io.undertow.util.HttpString;

public class UndertowStreamServer implements StreamServer<StreamServerConfigurationImpl> {
    private static final String TAG = "UndertowStreamServer";

    final protected StreamServerConfigurationImpl configuration;
    protected int localPort;
    private Undertow server;
    private final int ioTheads = Math.max(Runtime.getRuntime().availableProcessors(), 2);
    private final int maxWorkerThreads = 5000;
    private final int coreWorkerThreads = 200;

    public UndertowStreamServer(StreamServerConfigurationImpl configuration) {
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
                        server = Undertow.builder().setServerOption(UndertowOptions.ENABLE_HTTP2, true)
                                .setServerOption(UndertowOptions.ALLOW_UNESCAPED_CHARACTERS_IN_URL,true)
                                .setServerOption(UndertowOptions.ALWAYS_SET_KEEP_ALIVE,true)
                                .setServerOption(UndertowOptions.ENABLE_STATISTICS,false)
                                .setServerOption(UndertowOptions.ALLOW_UNKNOWN_PROTOCOLS,false)
                                .setServerOption(UndertowOptions.HTTP2_SETTINGS_ENABLE_PUSH,false)
                                .setServerOption(UndertowOptions.REQUIRE_HOST_HTTP11,false)
                                .addHttpListener(configuration.listenPort, bindAddress.getHostAddress())
                                .setWorkerOption(Options.WORKER_IO_THREADS, ioTheads)
                                .setWorkerOption(Options.WORKER_TASK_CORE_THREADS, coreWorkerThreads)
                                .setWorkerOption(Options.WORKER_TASK_MAX_THREADS, maxWorkerThreads)
                                .setWorkerThreads(ioTheads)
                                .setIoThreads(ioTheads)
                                .setHandler(new UpnpStreamHandler(router.getProtocolFactory())).build();
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
            try {
                this.server.start();
            }
            catch (final Exception e) {
                throw new RuntimeException(e);
            }
    }

    public synchronized void stopIfRunning() {
            try {
                this.server.stop();
            }
            catch (final Exception e) {
                throw new RuntimeException(e);
            }
    }

    @Override
    public void run() {

    }

    private static class UpnpStreamHandler extends UpnpStream implements HttpHandler {
        protected UpnpStreamHandler(ProtocolFactory protocolFactory) {
            super(protocolFactory);
        }

        private void writeResponseMessage (StreamResponseMessage
            responseMessage, HttpServerExchange exchange ) throws IOException {
                int statusCode = responseMessage.getOperation().getStatusCode();

                // Headers
                for (Map.Entry<String, List<String>> entry : responseMessage.getHeaders().entrySet()) {
                    for (String value : entry.getValue()) {
                        exchange.getResponseHeaders().add(HttpString.tryFromString(entry.getKey()), value);
                    }
                }
                // The Date header is recommended in UDA
                exchange.getResponseHeaders().add(Headers.DATE, "" + System.currentTimeMillis());

                // Body
                byte[] responseBodyBytes = responseMessage.hasBody() ? responseMessage.getBodyBytes() : null;
                int contentLength = responseBodyBytes != null ? responseBodyBytes.length : -1;

                if (contentLength > 0) {
                    Log.v(TAG, "Response message has body, writing bytes to stream...");
                   // Log.d(TAG, "Response message has body, " + new String(responseBodyBytes));

                    String contentType = "application/xml; charset=UTF-8";
                    if (responseMessage.getContentTypeHeader() != null) {
                        contentType = responseMessage.getContentTypeHeader().getValue().toString();
                    }

                    exchange.getResponseHeaders().add(Headers.CONTENT_TYPE, contentType);
                    exchange.setStatusCode(statusCode);
                    exchange.getResponseSender().send(ByteBuffer.wrap(responseBodyBytes));
                }
        }

        private StreamRequestMessage readRequestMessage (HttpServerExchange exchange) throws
            IOException {
                String requestMethod = exchange.getRequestMethod().toString();
                String requestURI = exchange.getRequestURI();
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
                HeaderMap httpHeaders = exchange.getRequestHeaders();
                for (HttpString header : httpHeaders.getHeaderNames()) {
                    headers.add(header.toString(), httpHeaders.getLast(header));
                }
                requestMessage.setHeaders(headers);

                // Body
                exchange.getRequestReceiver().receiveFullBytes(new Receiver.FullBytesCallback() {
                    @Override
                    public void handle(HttpServerExchange exchange, byte[] message) {
                        Log.v(TAG, "Reading request body bytes: " + message.length);
                        if (message.length > 0 && requestMessage.isContentTypeMissingOrText()) {
                            // Log.v(TAG, "Request contains textual entity body, converting then setting string on message");
                            requestMessage.setBodyCharacters(message);
                        } else if (message.length > 0) {
                            // Log.v(TAG, "Request contains binary entity body, setting bytes on message");
                            requestMessage.setBody(UpnpMessage.BodyType.BYTES, message);
                        } else {
                            Log.v(TAG, "Request did not contain entity body");
                        }
                    }
                });

                //  Log.v(TAG, "Request entity body: "+requestMessage.getBodyString());
                return requestMessage;
            }

            private String getUserAgent (HttpServerExchange req){
                return req.getRequestHeaders().getLast(Headers.USER_AGENT);
            }

            @Override
            public void handleRequest (HttpServerExchange exchange) throws Exception {
                try {
                    String userAgent = getUserAgent(exchange);
                    if ("CyberGarage-HTTP/1.0".equals(userAgent)) { // ||
                        // "Panasonic iOS VR-CP UPnP/2.0".equals(userAgent)) {//     requestMessage.getHeaders().getFirstHeader("User-agent"))) {
                        Log.v(TAG, "Interim FIX for MConnect on IPadOS 18 beta, return all songs for MConnect(fix show only 20 songs)");
                        ContentBrowser.forceFullContent = true;
                    }

                    StreamRequestMessage requestMessage = readRequestMessage(exchange);
                    // Log.v(TAG, "Processing new request message: " + requestMessage);

                    StreamResponseMessage responseMessage = process(requestMessage);

                    if (responseMessage != null) {
                        // Log.v(TAG, "Preparing HTTP response message: " + responseMessage);
                        writeResponseMessage(responseMessage, exchange);
                    } else {
                        // If it's null, it's 404
                        Log.v(TAG, "Sending HTTP response status: 404");
                        exchange.setStatusCode(404);
                        exchange.getResponseSender().send("Not Found");
                    }

                } catch (Throwable t) {
                    Log.e(TAG, "Exception occurred during UPnP stream processing: ", t);
                    exchange.setStatusCode(500);
                    exchange.getResponseSender().send("INTERNAL SERVER ERROR");
                    responseException(t);
                }
                exchange.endExchange();
            }

        @Override
        public void run() {

        }
    }
}