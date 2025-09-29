package apincer.android.mmate.dlna.transport.netty;
import static io.netty.handler.codec.http.HttpVersion.HTTP_1_1;

import android.content.Context;
import android.util.Log;

import org.jupnp.UpnpServiceConfiguration;
import org.jupnp.model.message.StreamRequestMessage;
import org.jupnp.model.message.StreamResponseMessage;
import org.jupnp.model.message.UpnpHeaders;
import org.jupnp.model.message.UpnpMessage;
import org.jupnp.model.message.UpnpRequest;
import org.jupnp.protocol.ProtocolFactory;
import org.jupnp.transport.Router;
import org.jupnp.transport.spi.InitializationException;
import org.jupnp.transport.spi.UpnpStream;

import java.net.InetAddress;
import java.net.URI;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;

import apincer.android.mmate.dlna.transport.StreamServerConfigurationImpl;
import apincer.android.mmate.dlna.transport.StreamServerImpl;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.PooledByteBufAllocator;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandler.Sharable;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.MultiThreadIoEventLoopGroup;
import io.netty.channel.WriteBufferWaterMark;
import io.netty.channel.nio.NioIoHandler;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpHeaderValues;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpServerCodec;
import io.netty.handler.codec.http.HttpUtil;
import io.netty.handler.timeout.IdleStateHandler;
import io.netty.util.AsciiString;
import io.netty.util.CharsetUtil;

public class NettyUPnpServerImpl extends StreamServerImpl.StreamServer {
    private static final String TAG = "NettyUPnpServer";
    private static final int SERVER_BACKLOG = 256;
    private static final int SOCKET_TIMEOUT_MS = 15000;
    private static final int WRITE_BUFFER_LOW = 8 * 1024;
    private static final int WRITE_BUFFER_HIGH = 24 * 1024;
    private static final int RECEIVE_BUFFER_SIZE = 1024;
    private static final int SEND_BUFFER_SIZE = 2048;
    private static final int MAX_HTTP_CONTENT_LENGTH = 5 * 1024 * 1024; // 5 MB

    EventLoopGroup bossGroup = new MultiThreadIoEventLoopGroup(1, NioIoHandler.newFactory()); // Single boss thread is usually sufficient
    int processorCount = Runtime.getRuntime().availableProcessors();
    EventLoopGroup workerGroup = new MultiThreadIoEventLoopGroup(processorCount, NioIoHandler.newFactory());

    private Thread serverThread;
    private Channel serverChannel;
    private boolean isInitialized = false;

    public NettyUPnpServerImpl(Context context, Router router, StreamServerConfigurationImpl configuration)  {
        super(context, router, configuration);
    }

    @Override
    public void initServer(InetAddress bindAddress) throws InitializationException {
        if (isInitialized) {
            Log.w(TAG, "Server already initialized");
            return;
        }

        int processorCount = Runtime.getRuntime().availableProcessors();

        try {
            bossGroup = new MultiThreadIoEventLoopGroup(1, NioIoHandler.newFactory());

            workerGroup = new MultiThreadIoEventLoopGroup(processorCount, NioIoHandler.newFactory());

            serverThread = new Thread(() -> {
                try {
                   // Log.i(TAG, "Starting Netty4 UPNP Server: " + bindAddress.getHostAddress() + ":" + getListenPort());

                    // Pooled buffers for better memory management
                    PooledByteBufAllocator allocator = new PooledByteBufAllocator(
                            true, // preferDirect
                            2, // nHeapArena
                            2, // nDirectArena
                            8192, // pageSize
                            11, // maxOrder
                            64, // smallCacheSize
                            32, // normalCacheSize
                            true // useCacheForAllThreads
                    );

                    ServerBootstrap b = new ServerBootstrap();
                    b.option(ChannelOption.SO_BACKLOG, SERVER_BACKLOG)
                            .childOption(ChannelOption.WRITE_BUFFER_WATER_MARK,
                                    new WriteBufferWaterMark(WRITE_BUFFER_LOW, WRITE_BUFFER_HIGH));

                    b.group(bossGroup, workerGroup)
                            .channel(NioServerSocketChannel.class)
                            .option(ChannelOption.SO_REUSEADDR, true)
                            .option(ChannelOption.TCP_NODELAY, true)
                            .option(ChannelOption.SO_KEEPALIVE, true)
                            .childOption(ChannelOption.ALLOCATOR, allocator)
                            .childOption(ChannelOption.SO_RCVBUF, RECEIVE_BUFFER_SIZE)
                            .childOption(ChannelOption.SO_SNDBUF, SEND_BUFFER_SIZE)
                            .option(ChannelOption.SO_TIMEOUT, SOCKET_TIMEOUT_MS)
                            .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, SOCKET_TIMEOUT_MS)
                            .childHandler(new HttpServerInitializer(getProtocolFactory(), getConfiguration()));

                    // Bind and start to accept incoming connections
                    ChannelFuture f = b.bind(bindAddress.getHostAddress(), getListenPort()).sync();
                    serverChannel = f.channel();
                    isInitialized = true;

                    Log.i(TAG, "UPnP Server started on "+bindAddress.getHostAddress()+":"+getListenPort()+" successfully.");

                } catch (Exception ex) {
                    Log.e(TAG, "Failed to initialize server: " + ex.getMessage(), ex);
                    stopServer(); // Clean up resources if startup fails
                    throw new RuntimeException("Could not initialize " + getClass().getSimpleName() + ": " + ex, ex);
                }
            });

            serverThread.setName("UPNP-Server-Init");
            serverThread.start();

        } catch (Exception e) {
            Log.e(TAG, "Error initializing server groups", e);
            //cleanup(); // Ensure resources are released
            throw new InitializationException("Failed to initialize server: " + e.getMessage(), e);
        }
    }

    synchronized public void stopServer() {
        if (!isInitialized) {
            return; // Already stopped or not initialized
        }

      //  Log.i(TAG, "Stopping Http UPNP Server");
        isInitialized = false;

        try {
            // Close the server channel to stop accepting new connections
            if (serverChannel != null) {
                serverChannel.close().syncUninterruptibly();
            }
        } finally {
            // Shut down the event loop groups to release all threads and resources
            if (workerGroup != null) {
                workerGroup.shutdownGracefully().syncUninterruptibly();
            }
            if (bossGroup != null) {
                bossGroup.shutdownGracefully().syncUninterruptibly();
            }
        }

        // Interrupt server thread if still running
        if (serverThread != null && serverThread.isAlive()) {
            serverThread.interrupt();
            try {
                serverThread.join(2000); // Wait up to 2 seconds for thread to terminate
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                serverThread = null;
            }
        }

        Log.i(TAG, "Http UPNP Server stopped successfully");
    }

    @Override
    protected String getServerVersion() {
        return "Netty/4.2.0";
    }

    @Sharable
    private class HttpServerHandler extends ChannelInboundHandlerAdapter {
        final String upnpPath;
        final UpnpStream upnpStream;

        public HttpServerHandler(ProtocolFactory protocolFactory, String path) {
            super();
            upnpPath = path;
            upnpStream = new UpnpStream(protocolFactory) {
                @Override
                public void run() {
                    // Implementation not needed for this usage
                }
            };
        }

        @Override
        public void channelReadComplete(ChannelHandlerContext ctx) {
            ctx.flush();
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
            ctx.close();
            Log.w(TAG, "exceptionCaught: "+cause.getMessage());
        }

        private void sendForbidden(ChannelHandlerContext ctx, FullHttpRequest request) {
            FullHttpResponse response = new DefaultFullHttpResponse(
                    HTTP_1_1, HttpResponseStatus.FORBIDDEN, Unpooled.copiedBuffer("Failure: " + HttpResponseStatus.FORBIDDEN + "\r\n", CharsetUtil.UTF_8));
            response.headers().set(HttpHeaderNames.CONTENT_TYPE, "text/plain; charset=UTF-8");

            sendAndCleanupConnection(ctx, request, response);
        }

        /**
         * If Keep-Alive is disabled, attaches "Connection: close" header to the response
         * and closes the connection after the response being sent.
         */
        private void sendAndCleanupConnection(ChannelHandlerContext ctx, FullHttpRequest request, FullHttpResponse response) {
            final boolean keepAlive = HttpUtil.isKeepAlive(request);
            HttpUtil.setContentLength(response, response.content().readableBytes());
            HttpUtil.setKeepAlive(response.headers(), request.protocolVersion(), keepAlive);
            // Add date header with RFC 1123 format required by HTTP/DLNA
            SimpleDateFormat dateFormatter = new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss z", Locale.US);
            dateFormatter.setTimeZone(TimeZone.getTimeZone("GMT"));
            response.headers().set(HttpHeaderNames.DATE, dateFormatter.format(new Date()));

            // Add more comprehensive DLNA headers for better compatibility
            response.headers().set("TransferMode.DLNA.ORG", "Interactive");
            response.headers().set("Connection-Timeout", "60");

            response.headers().set(HttpHeaderNames.SERVER, getFullServerName());
            ChannelFuture flushPromise = ctx.writeAndFlush(response);

            if (!keepAlive) {
                // Close the connection as soon as the response is sent.
                flushPromise.addListener(ChannelFutureListener.CLOSE);
            }
        }

        @Override
        public void channelRead( final ChannelHandlerContext ctx, Object msg) {
            if (msg instanceof FullHttpRequest request) {
                final ByteBuf buf = request.content();
                byte[] data = new byte[buf.readableBytes()];
                buf.readBytes(data);
                buf.release();

                String uri = request.uri();
                NettyContentHolder contentHolder;

                if (uri.startsWith(upnpPath)) {
                   // Log.d(TAG, "Processing UPnP request: " + request.method() + " " + uri);
                    contentHolder = handleUPnpStream(request, data);
                } else {
                    Log.d(TAG, "Invalid request path: " + uri);
                    sendForbidden(ctx, request);
                    return;
                }

                DefaultFullHttpResponse response = new DefaultFullHttpResponse(
                        request.protocolVersion(),
                        contentHolder.getStatusCode(),
                        Unpooled.wrappedBuffer(contentHolder.getContent()));

                response.headers().set(HttpHeaderNames.CONTENT_TYPE, contentHolder.getContentType());

                for (Map.Entry<String, String> entry : contentHolder.getHeaders().entrySet()) {
                    response.headers().set(AsciiString.of(entry.getKey()), entry.getValue());
                }

                sendAndCleanupConnection(ctx, request, response);
            }
        }

        private NettyContentHolder handleUPnpStream(FullHttpRequest request, byte[] bodyBytes) {
            try {
                StreamRequestMessage requestMessage = readRequestMessage(request, bodyBytes);

                StreamResponseMessage responseMessage = upnpStream.process(requestMessage);

                if (responseMessage != null) {
                    return buildResponseMessage(responseMessage);
                } else {
                    Log.d(TAG, "UPnP stream returned null response for: " + request.uri());
                    return new NettyContentHolder(null,
                            HttpHeaderValues.TEXT_PLAIN,
                            HttpResponseStatus.NOT_FOUND,
                            "Resource not found");
                }
            } catch (Throwable t) {
                Log.e(TAG, "Exception in UPnP stream processing: ", t);
                return new NettyContentHolder(null,
                        HttpHeaderValues.TEXT_PLAIN,
                        HttpResponseStatus.INTERNAL_SERVER_ERROR,
                        "Error: " + t.getMessage());
            }
        }

        protected StreamRequestMessage readRequestMessage(FullHttpRequest request, byte[] bodyBytes) {
            // Extract what we need from the HTTP request
            String requestMethod = request.method().name();
            String requestURI = request.uri();

            StreamRequestMessage requestMessage;
            try {
                requestMessage = new StreamRequestMessage(
                        UpnpRequest.Method.getByHttpName(requestMethod),
                        URI.create(requestURI)
                );
            } catch (IllegalArgumentException ex) {
                throw new RuntimeException("Invalid request URI: " + requestURI, ex);
            }

            if (requestMessage.getOperation().getMethod().equals(UpnpRequest.Method.UNKNOWN)) {
                throw new RuntimeException("Method not supported: " + requestMethod);
            }

            // Convert headers
            UpnpHeaders headers = new UpnpHeaders();
            HttpHeaders httpHeaders = request.headers();
            for (Map.Entry<String, String> entry : httpHeaders) {
                headers.add(entry.getKey(), entry.getValue());
            }
            requestMessage.setHeaders(headers);

            // Handle body
            if (bodyBytes != null && bodyBytes.length > 0) {
                if (requestMessage.isContentTypeMissingOrText()) {
                    requestMessage.setBodyCharacters(bodyBytes);
                } else {
                    requestMessage.setBody(UpnpMessage.BodyType.BYTES, bodyBytes);
                }
            }

            return requestMessage;
        }

        protected NettyContentHolder buildResponseMessage(StreamResponseMessage responseMessage) {
            // Create response body
            byte[] responseBodyBytes = responseMessage.hasBody() ? responseMessage.getBodyBytes() : new byte[0];

            String contentType = "application/xml";
            if (responseMessage.getContentTypeHeader() != null) {
                contentType = responseMessage.getContentTypeHeader().getValue().toString();
            }

            NettyContentHolder holder = new NettyContentHolder(null,
                    new AsciiString(contentType),
                    HttpResponseStatus.valueOf(responseMessage.getOperation().getStatusCode()),
                    responseBodyBytes);

            // Add headers
            for (Map.Entry<String, List<String>> entry : responseMessage.getHeaders().entrySet()) {
                for (String value : entry.getValue()) {
                    holder.getHeaders().put(entry.getKey(), value);
                }
            }

            return holder;
        }
    }

    private class HttpServerInitializer extends ChannelInitializer<SocketChannel> {
        private final ProtocolFactory protocolFactory;
        private final UpnpServiceConfiguration configuration;

        public HttpServerInitializer(ProtocolFactory protocolFactory, UpnpServiceConfiguration configuration) {
            this.protocolFactory = protocolFactory;
            this.configuration = configuration;
        }

        @Override
        public void initChannel(SocketChannel ch) {
            ChannelPipeline p = ch.pipeline();
            p.addLast(new HttpServerCodec());
            p.addLast(new HttpObjectAggregator(MAX_HTTP_CONTENT_LENGTH)); // 5 MB max  //Integer.MAX_VALUE));
            p.addLast(new IdleStateHandler(60, 30, 0));
            p.addLast(new HttpServerHandler(protocolFactory, configuration.getNamespace().getBasePath().getPath()));
        }
    }

}