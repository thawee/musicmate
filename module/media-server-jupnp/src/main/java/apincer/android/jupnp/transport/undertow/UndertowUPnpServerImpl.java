package apincer.android.jupnp.transport.undertow;

import android.content.Context;
import android.util.Log;

import org.jupnp.model.message.StreamRequestMessage;
import org.jupnp.model.message.StreamResponseMessage;
import org.jupnp.model.message.UpnpHeaders;
import org.jupnp.model.message.UpnpMessage;
import org.jupnp.model.message.UpnpRequest;
import org.jupnp.protocol.ProtocolFactory;
import org.jupnp.protocol.ReceivingSync;
import org.jupnp.transport.Router;
import org.jupnp.transport.spi.InitializationException;
import org.xnio.Options;

import java.net.InetAddress;
import java.net.URI;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.Map;

import apincer.android.jupnp.transport.StreamServerConfigurationImpl;
import apincer.android.jupnp.transport.StreamServerImpl;
import apincer.android.mmate.core.server.IMediaServer;
import io.undertow.Undertow;
import io.undertow.UndertowOptions;
import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.util.HeaderMap;
import io.undertow.util.Headers;
import io.undertow.util.HttpString;

public class UndertowUPnpServerImpl extends StreamServerImpl.JUpnpServer {
    private static final String TAG = "UndertowUPnpServer";

    private final int ioTheads = Math.max(Runtime.getRuntime().availableProcessors(), 2);

    private Undertow server;

    public UndertowUPnpServerImpl(Context context, IMediaServer mediaServer, Router router, StreamServerConfigurationImpl configuration) {
        super(context, mediaServer, router, configuration);
    }

    @Override
    public void initServer(InetAddress bindAddress) throws InitializationException {
        Thread serverThread = new Thread(() -> {
            try {
               // Log.i(TAG, "Starting UPnP Server (Jetty) on " + bindAddress.getHostAddress() + ":" + getListenPort());

                server = Undertow.builder().setServerOption(UndertowOptions.ENABLE_HTTP2, true)
                        .setServerOption(UndertowOptions.ALLOW_UNESCAPED_CHARACTERS_IN_URL,true)
                        .setServerOption(UndertowOptions.ALWAYS_SET_KEEP_ALIVE,true)
                        .setServerOption(UndertowOptions.ENABLE_STATISTICS,false)
                        .setServerOption(UndertowOptions.ALLOW_UNKNOWN_PROTOCOLS,false)
                        .setServerOption(UndertowOptions.HTTP2_SETTINGS_ENABLE_PUSH,false)
                        .setServerOption(UndertowOptions.REQUIRE_HOST_HTTP11,false)
                        .addHttpListener(getListenPort(), bindAddress.getHostAddress())
                        .setWorkerOption(Options.WORKER_IO_THREADS, ioTheads)
                        //.setWorkerOption(Options.WORKER_TASK_CORE_THREADS, coreWorkerThreads)
                        //.setWorkerOption(Options.WORKER_TASK_MAX_THREADS, maxWorkerThreads)
                        .setWorkerThreads(ioTheads)
                        .setIoThreads(ioTheads)
                        .setHandler(new UpnpStreamHandler(router.getProtocolFactory())).build();
                server.start();
                Log.i(TAG, "UPnP Server started on " + bindAddress.getHostAddress() + ":" + getListenPort() +" successfully.");

            } catch (Exception e) {
                Log.e(TAG, "Failed to start or run UPnP server", e);
            }
        });
        serverThread.setName("jetty-upnp-runner");
        serverThread.start();
    }

    @Override
    public void stopServer() {
        Log.i(TAG, "Stopping UPnP Server (Undertow)");
        try {
            if (server != null) {
                server.stop();
            }
        } catch (Exception e) {
            Log.e(TAG, "Error stopping UPnP server", e);
        }
    }

    @Override
    protected String getServerVersion() {
        return "Undertow/12.1.1";
    }

    private static class UpnpStreamHandler implements HttpHandler {
        ProtocolFactory protocolFactory;
        protected UpnpStreamHandler(ProtocolFactory protocolFactory) {
            super();
            this.protocolFactory = protocolFactory;
        }

        private void writeResponseMessage (StreamResponseMessage
                                                   responseMessage, HttpServerExchange exchange ) {
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

        private StreamRequestMessage readRequestMessage (HttpServerExchange exchange) {
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
            exchange.getRequestReceiver().receiveFullBytes((exchange1, message) -> {
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
                StreamRequestMessage requestMessage = readRequestMessage(exchange);
                // Log.v(TAG, "Processing new request message: " + requestMessage);

                // IMPROVEMENT: Use the protocol factory to create and run the right processor.
                // This is the most robust way to handle requests in jupnp.
                ReceivingSync protocol = protocolFactory.createReceivingSync(requestMessage);
                protocol.run();
                StreamResponseMessage responseMessage = protocol.getOutputMessage();

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
            }
            exchange.endExchange();
        }
    }
}