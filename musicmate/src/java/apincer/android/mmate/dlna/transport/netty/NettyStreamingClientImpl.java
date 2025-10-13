package apincer.android.mmate.dlna.transport.netty;

import android.util.Log;

import apincer.android.mmate.dlna.transport.StreamClientConfigurationImpl;
import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.http.DefaultFullHttpRequest;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpClientCodec;
import io.netty.handler.codec.http.HttpContentDecompressor;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpHeaderValues;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.handler.timeout.ReadTimeoutHandler;
import io.netty.util.CharsetUtil;

import org.jupnp.http.Headers;
import org.jupnp.model.message.StreamRequestMessage;
import org.jupnp.model.message.StreamResponseMessage;
import org.jupnp.model.message.UpnpHeaders;
import org.jupnp.model.message.UpnpMessage;
import org.jupnp.model.message.UpnpRequest;
import org.jupnp.model.message.UpnpResponse;
import org.jupnp.model.message.header.UpnpHeader;
import org.jupnp.transport.spi.AbstractStreamClient;
import org.jupnp.transport.spi.InitializationException;
import org.jupnp.util.SpecificationViolationReporter;

import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public class NettyStreamingClientImpl extends AbstractStreamClient<StreamClientConfigurationImpl, HttpRequest> {
    private static final String TAG = "NettyStreamingClient";

    final protected StreamClientConfigurationImpl configuration;
    protected final EventLoopGroup eventLoopGroup;
    protected final Bootstrap bootstrap;
    protected final ConcurrentHashMap<HttpRequest, HttpClientHandler> requestHandlerMap = new ConcurrentHashMap<>();

    public NettyStreamingClientImpl(StreamClientConfigurationImpl configuration) throws InitializationException {
        this.configuration = configuration;

        int cpus = Runtime.getRuntime().availableProcessors();
        int threads = 5 * cpus;

        try {
            eventLoopGroup = new NioEventLoopGroup(threads);
            bootstrap = new Bootstrap();

            bootstrap.group(eventLoopGroup)
                    .channel(NioSocketChannel.class)
                    .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, (getConfiguration().getTimeoutSeconds() + 5) * 1000)
                    .option(ChannelOption.SO_KEEPALIVE, false)
                    .option(ChannelOption.TCP_NODELAY, true);
        } catch (Exception e) {
            Log.e(TAG, "Failed to instantiate Netty client", e);
            throw new InitializationException("Failed to instantiate Netty client", e);
        }
    }

    @Override
    public StreamClientConfigurationImpl getConfiguration() {
        return configuration;
    }

    @Override
    protected HttpRequest createRequest(StreamRequestMessage requestMessage) {
        final UpnpRequest upnpRequest = requestMessage.getOperation();
        URI uri;

        try {
            uri = upnpRequest.getURI();
        } catch (IllegalArgumentException e) {
            Log.d(TAG, "Cannot create request because URI is invalid: " + upnpRequest.getURI(), e);
            return null;
        }

        HttpMethod method;
        switch (upnpRequest.getMethod()) {
            case GET:
                method = HttpMethod.GET;
                break;
            case POST:
                method = HttpMethod.POST;
                break;
            case NOTIFY:
                method = HttpMethod.valueOf("NOTIFY");
                break;
            case SUBSCRIBE:
                method = HttpMethod.valueOf("SUBSCRIBE");
                break;
            case UNSUBSCRIBE:
                method = HttpMethod.valueOf("UNSUBSCRIBE");
                break;
            default:
                throw new RuntimeException("Unknown HTTP method: " + upnpRequest.getHttpMethodName());
        }

        HttpVersion httpVersion = upnpRequest.getHttpMinorVersion() == 0
                ? HttpVersion.HTTP_1_0
                : HttpVersion.HTTP_1_1;

        // Create request with appropriate method and URI path
        String path = uri.getPath();
        if (uri.getRawQuery() != null) {
            path += "?" + uri.getRawQuery();
        }

        FullHttpRequest request = new DefaultFullHttpRequest(
                httpVersion,
                method,
                path
        );

        // Set host header
        request.headers().set(HttpHeaderNames.HOST, uri.getHost() + ":" + (uri.getPort() == -1 ? 80 : uri.getPort()));

        // Set Connection: close header for HTTP 1.1
        if (httpVersion.equals(HttpVersion.HTTP_1_1)) {
            request.headers().set(HttpHeaderNames.CONNECTION, HttpHeaderValues.CLOSE);
        }

        // Add User-Agent if not already present
        if (!requestMessage.getHeaders().containsKey(UpnpHeader.Type.USER_AGENT)) {
            request.headers().set(
                    HttpHeaderNames.USER_AGENT,
                    getConfiguration().getUserAgentValue(
                            requestMessage.getUdaMajorVersion(),
                            requestMessage.getUdaMinorVersion()
                    )
            );
        }

        // Add all headers from the UPnP message
        addHeaders(request.headers(), requestMessage.getHeaders());

        // Add body for POST and NOTIFY requests
        if (upnpRequest.getMethod() == UpnpRequest.Method.POST ||
                upnpRequest.getMethod() == UpnpRequest.Method.NOTIFY) {

            ByteBuf content;
            if (requestMessage.getBodyType().equals(UpnpMessage.BodyType.STRING)) {
                content = Unpooled.copiedBuffer(requestMessage.getBodyString(), CharsetUtil.UTF_8);

                // Ensure content type charset is set
                if (requestMessage.getContentTypeCharset() != null) {
                    request.headers().set(
                            HttpHeaderNames.CONTENT_TYPE,
                            "text/xml; charset=\"" + requestMessage.getContentTypeCharset().name() + "\""
                    );
                }
            } else {
                content = Unpooled.wrappedBuffer(requestMessage.getBodyBytes());
            }

            request.headers().set(HttpHeaderNames.CONTENT_LENGTH, content.readableBytes());
            request.content().writeBytes(content);
            content.release();
        }

        return request;
    }

    private void addHeaders(HttpHeaders headers, UpnpHeaders upnpHeaders) {
        for (Map.Entry<String, List<String>> entry : upnpHeaders.entrySet()) {
            for (final String value : entry.getValue()) {
                headers.add(entry.getKey(), value);
            }
        }
    }

    @Override
    protected Callable<StreamResponseMessage> createCallable(
            final StreamRequestMessage requestMessage,
            final HttpRequest request) {

        return () -> {
            URI uri = requestMessage.getOperation().getURI();

            final HttpClientHandler handler = new HttpClientHandler();
            requestHandlerMap.put(request, handler);

            bootstrap.handler(new ChannelInitializer<SocketChannel>() {
                @Override
                protected void initChannel(SocketChannel ch) {
                    ChannelPipeline p = ch.pipeline();
                    p.addLast(new ReadTimeoutHandler(getConfiguration().getTimeoutSeconds()));
                    p.addLast(new HttpClientCodec());
                    p.addLast(new HttpContentDecompressor());
                    p.addLast(new HttpObjectAggregator(1048576)); // 1MB max
                    p.addLast(handler);
                }
            });

            // Connect and send request
            try {
                Channel channel = bootstrap.connect(uri.getHost(), uri.getPort() == -1 ? 80 : uri.getPort())
                        .sync().channel();

                channel.writeAndFlush(request).sync();

                // Wait for response with timeout
                FullHttpResponse response = handler.responseFuture.get(
                        getConfiguration().getTimeoutSeconds(),
                        TimeUnit.SECONDS
                );

                // Status
                final UpnpResponse responseOperation = new UpnpResponse(
                        response.status().code(),
                        response.status().reasonPhrase()
                );

                // Message
                final StreamResponseMessage responseMessage = new StreamResponseMessage(responseOperation);

                // Headers
                responseMessage.setHeaders(new UpnpHeaders(readHeaders(response.headers())));

                // Body
                ByteBuf content = response.content();
                if (content.isReadable()) {
                    byte[] bytes = new byte[content.readableBytes()];
                    content.readBytes(bytes);
                    responseMessage.setBodyCharacters(bytes);
                }

                channel.close().sync();
                return responseMessage;

            } catch (InterruptedException | ExecutionException | TimeoutException e) {
                Log.e(TAG, "Error executing HTTP request", e);
                throw new Exception("Error executing HTTP request: " + e.getMessage(), e);
            } finally {
                requestHandlerMap.remove(request);
            }
        };
    }

    private Headers readHeaders(HttpHeaders headers) {
        final Headers result = new Headers();
        for (Map.Entry<String, String> entry : headers) {
            result.add(entry.getKey(), entry.getValue());
        }
        return result;
    }

    @Override
    protected void abort(HttpRequest request) {
        HttpClientHandler handler = requestHandlerMap.get(request);
        if (handler != null && handler.channel != null) {
            handler.channel.close();
        }
    }

    @Override
    protected boolean logExecutionException(Throwable t) {
        if (t instanceof IllegalStateException) {
            Log.v(TAG, "Illegal state: " + t.getMessage());
            return true;
        } else if (t.getMessage() != null && t.getMessage().contains("HTTP protocol violation")) {
            SpecificationViolationReporter.report(t.getMessage());
            return true;
        }
        return false;
    }

    @Override
    public void stop() {
        Log.v(TAG, "Shutting down Netty event loop group");
        try {
            eventLoopGroup.shutdownGracefully(0, 2, TimeUnit.SECONDS);
        } catch (Exception e) {
            Log.i(TAG, "Shutting down of Netty client threw exception", e);
        }
    }

    // Handler for HTTP responses
    private static class HttpClientHandler extends io.netty.channel.SimpleChannelInboundHandler<FullHttpResponse> {
        private final java.util.concurrent.CompletableFuture<FullHttpResponse> responseFuture = new java.util.concurrent.CompletableFuture<>();
        private Channel channel;

        @Override
        public void channelActive(io.netty.channel.ChannelHandlerContext ctx) {
            this.channel = ctx.channel();
        }

        @Override
        protected void channelRead0(io.netty.channel.ChannelHandlerContext ctx, FullHttpResponse msg) {
            // Complete future with response
            responseFuture.complete(msg.retain());
        }

        @Override
        public void exceptionCaught(io.netty.channel.ChannelHandlerContext ctx, Throwable cause) {
            responseFuture.completeExceptionally(cause);
            ctx.close();
        }
    }
}
