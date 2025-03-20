package apincer.android.mmate.dlna.transport;
import static java.nio.file.StandardCopyOption.REPLACE_EXISTING;
import static apincer.android.mmate.Constants.COVER_ARTS;
import static apincer.android.mmate.Constants.DEFAULT_COVERART_DLNA_RES;
import static apincer.android.mmate.Constants.DEFAULT_COVERART_FILE;
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

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;

import apincer.android.mmate.MusixMateApp;
import apincer.android.mmate.repository.FileRepository;
import apincer.android.mmate.repository.MusicTag;
import apincer.android.mmate.utils.ApplicationUtils;
import apincer.android.mmate.utils.MimeTypeUtils;
import apincer.android.utils.FileUtils;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.PooledByteBufAllocator;
import io.netty.buffer.Unpooled;
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

    EventLoopGroup bossGroup = new MultiThreadIoEventLoopGroup(1, NioIoHandler.newFactory()); // Single boss thread is usually sufficient
    int processorCount = Runtime.getRuntime().availableProcessors();
    EventLoopGroup workerGroup = new MultiThreadIoEventLoopGroup(processorCount * 2, NioIoHandler.newFactory());
    private ChannelFuture channelFuture;

    public NettyUPnpServerImpl(Context context, Router router, StreamServerConfigurationImpl configuration)  {
        super(context, router, configuration);
    }

    @Override
    synchronized public void initServer(InetAddress bindAddress) throws InitializationException {
        Thread thread = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    Log.v(TAG, "Running Netty4 UPNP Server: " + bindAddress.getHostAddress() + ":" + getListenPort());

                    // Pooled buffers help reduce memory fragmentation and improve performance.
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
                    // Add connection limiting to prevent resource exhaustion
                    b.option(ChannelOption.SO_BACKLOG, 256)  //Set the size of the backlog of TCP connections.  The default and exact meaning of this parameter is JDK specific.
                    .childOption(ChannelOption.WRITE_BUFFER_WATER_MARK, new WriteBufferWaterMark(8 * 1024, 32 * 1024));  // Flow control
                    b.group(bossGroup, workerGroup)
                            .channel(NioServerSocketChannel.class)  // use nio
                            .option(ChannelOption.SO_REUSEADDR, true)
                            .option(ChannelOption.TCP_NODELAY, true) // true - great for low latency
                            .option(ChannelOption.SO_KEEPALIVE, true)

                            // for minimized memory usage
                            .childOption(ChannelOption.ALLOCATOR, allocator) //PooledByteBufAllocator.DEFAULT)
                            .childOption(ChannelOption.SO_RCVBUF, 1024) // 1kB receive buffer
                            .childOption(ChannelOption.SO_SNDBUF, 2048) // 2KB send buffer

                            .option(ChannelOption.SO_TIMEOUT, 30000) // 30 sec
                            .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 30000) // 30 sec
                            .childHandler(new HttpServerInitializer(getProtocolFactory(), getConfiguration()));
                    // Bind and start to accept incoming connections but don't block this thread
                    channelFuture = b.bind(getListenPort()).sync();

                    // DON'T call this as it blocks forever until server is closed:
                    // channelFuture.channel().closeFuture().sync();

                    Log.v(TAG, "Netty4 UPNP Server started successfully");
                } catch (Exception ex) {
                    Log.e(TAG, "Failed to initialize server: " + ex.getMessage(), ex);
                    stopServer(); // Clean up resources if startup fails
                    throw new RuntimeException("Could not initialize " + getClass().getSimpleName() + ": " + ex, ex);
                }
            }
        });
        thread.setName("UPNP-Server-Init");
        thread.start();
    }

    synchronized public void stopServer() {
        Log.v(TAG, "Stopping Netty4 UPNP Server");
        try {
            if (channelFuture != null) {
                channelFuture.channel().close().sync();
                channelFuture = null;
            }
        } catch (Exception e) {
            Log.e(TAG, "Error closing server channel: " + e.getMessage(), e);
        }

        try {
            if (bossGroup != null) {
                bossGroup.shutdownGracefully().sync();
            }
        } catch (Exception e) {
            Log.e(TAG, "Error shutting down boss group: " + e.getMessage(), e);
        }

        try {
            if (workerGroup != null) {
                workerGroup.shutdownGracefully().sync();
            }
        } catch (Exception e) {
            Log.e(TAG, "Error shutting down worker group: " + e.getMessage(), e);
        }

        Log.v(TAG, "Netty4 UPNP Server stopped successfully");
    }

    @Override
    protected String getServerVersion() {
        return "Netty/4.2.0";
    }

    @Sharable
    private class HttpServerHandler extends ChannelInboundHandlerAdapter {
        final String upnpPath;
        final UpnpStream upnpStream;
        final File defaultCoverartDir;

        public HttpServerHandler(ProtocolFactory protocolFactory, String path) {
            super();
            upnpPath = path;
            defaultCoverartDir = new File(getCoverartDir(COVER_ARTS),DEFAULT_COVERART_FILE);
            upnpStream = new UpnpStream(protocolFactory) {
                @Override
                public void run() {

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
                if (uri.startsWith(upnpPath)) {
                    ContentHolder contentHolder = handleUPnpStream(request, data);
                    DefaultFullHttpResponse response = new DefaultFullHttpResponse(request.protocolVersion(), contentHolder.statusCode, Unpooled.wrappedBuffer(contentHolder.content));
                    response.headers().set(HttpHeaderNames.CONTENT_TYPE, contentHolder.contentType);
                    for (String key : contentHolder.headers.keySet()) {
                        response.headers().set(AsciiString.of(key), contentHolder.headers.get(key));
                    }
                    sendAndCleanupConnection(ctx, request, response);
                }else if (uri.startsWith("/coverart/")) {
                        ContentHolder contentHolder = handleAlbumart(request, uri);
                        DefaultFullHttpResponse response = new DefaultFullHttpResponse(request.protocolVersion(), contentHolder.statusCode, Unpooled.wrappedBuffer(contentHolder.content));
                        response.headers().set(HttpHeaderNames.CONTENT_TYPE, contentHolder.contentType);
                        for(String key: contentHolder.headers.keySet()) {
                            response.headers().set(AsciiString.of(key), contentHolder.headers.get(key));
                        }
                        sendAndCleanupConnection(ctx, request, response);
                }else {
                    // if not upnp stream
                    sendForbidden(ctx, request);
                }
            }
        }

        private ContentHolder handleAlbumart(FullHttpRequest request, String uri) {
            try {
                String albumUniqueKey = uri.substring("/coverart/".length(), uri.indexOf(".png"));
                MusicTag tag = MusixMateApp.getInstance().getOrmLite().findByAlbumUniqueKey(albumUniqueKey);
                if (tag != null) {
                    File covertFile = FileRepository.getFolderCoverArt(tag.getPath());
                    if (covertFile != null) {
                            ByteBuffer buffer = FileUtils.getBytes(covertFile);
                            String mime = MimeTypeUtils.getMimeTypeFromPath(covertFile.getAbsolutePath());
                            return new ContentHolder(AsciiString.of(mime), HttpResponseStatus.OK, buffer.array());
                    }
                }
            } catch (Exception e) {
                Log.e(TAG, "lookupAlbumArt: - not found " + uri, e);
            }

            try {
                if(!defaultCoverartDir.exists()) {
                    FileUtils.createParentDirs(defaultCoverartDir);
                    InputStream in = ApplicationUtils.getAssetsAsStream(getContext(), DEFAULT_COVERART_DLNA_RES);
                    Files.copy(in, defaultCoverartDir.toPath(), REPLACE_EXISTING);
                }

                ByteBuffer buffer = FileUtils.getBytes(defaultCoverartDir);
                String mime = MimeTypeUtils.getMimeTypeFromPath(defaultCoverartDir.getAbsolutePath());
                return new ContentHolder(AsciiString.of(mime), HttpResponseStatus.OK, buffer.array());
            } catch (IOException e) {
                Log.e(TAG, "Init default missing cover art", e);
            }
            return null;
        }

        private  ContentHolder handleUPnpStream(FullHttpRequest request, byte[] bodyBytes) {
            ContentHolder holder;
            try {
                StreamRequestMessage requestMessage = readRequestMessage(request, bodyBytes);
                // Log.v(TAG, "Processing new request message: " + requestMessage);

                StreamResponseMessage responseMessage = upnpStream.process(requestMessage);

                if (responseMessage != null) {
                    // Log.v(TAG, "Preparing HTTP response message: " + responseMessage);
                    holder = buildResponseMessage(responseMessage);
                } else {
                    // If it's null, it's 404
                    holder = new ContentHolder(HttpHeaderValues.TEXT_PLAIN, HttpResponseStatus.NOT_FOUND, "Not Found");
                    Log.v(TAG, "Sending HTTP response status: 404" );
                }
            } catch (Throwable t) {
                Log.e(TAG, "Exception occurred during UPnP stream processing: ", t);
                holder = new ContentHolder(HttpHeaderValues.TEXT_PLAIN, HttpResponseStatus.INTERNAL_SERVER_ERROR, "INTERNAL SERVER ERROR");
                // upnpStream.responseException(t);
            }
            return  holder;
        }

        private String getUserAgent(StreamRequestMessage requestMessage) {
            try {
                return requestMessage.getHeaders().getFirstHeader("User-agent");
            }catch (Exception ignore) {
            }
            return "";
        }

        protected StreamRequestMessage readRequestMessage(FullHttpRequest request, byte[] bodyBytes) {
            // Extract what we need from the HTTP httpRequest
            String requestMethod = request.method().name();
            String requestURI = request.uri();

            // Log.v(TAG, "Processing HTTP request: " + requestMethod + " " + requestURI);

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
            HttpHeaders httpHeaders = request.headers();
            for (Map.Entry<String, String> name : httpHeaders) {
                headers.add(name.getKey(), name.getValue());
            }
            requestMessage.setHeaders(headers);

            // Body
            if (bodyBytes == null) {
                bodyBytes = new byte[]{};
            }
            // Log.v(TAG, "Reading request body bytes: " + bodyBytes.length);

            if (bodyBytes.length > 0 && requestMessage.isContentTypeMissingOrText()) {

                // Log.v(TAG, "Request contains textual entity body, converting then setting string on message");
                requestMessage.setBodyCharacters(bodyBytes);

            } else if (bodyBytes.length > 0) {

                // Log.v(TAG, "Request contains binary entity body, setting bytes on message");
                requestMessage.setBody(UpnpMessage.BodyType.BYTES, bodyBytes);

           // } else {
           //     Log.v(TAG, "Request did not contain entity body");
            }
            //  Log.v(TAG, "Request entity body: "+requestMessage.getBodyString());
            return requestMessage;
        }

        protected ContentHolder buildResponseMessage(StreamResponseMessage responseMessage) {
            //   Log.v(TAG, "Sending HTTP response status: " + responseMessage.getOperation().getStatusCode());
            ContentHolder holder;

            // Body
            byte[] responseBodyBytes = responseMessage.hasBody() ? responseMessage.getBodyBytes() : null;
            int contentLength = responseBodyBytes != null ? responseBodyBytes.length : -1;

            if (contentLength > 0) {
                String contentType = "application/xml";
                if (responseMessage.getContentTypeHeader() != null) {
                    contentType = responseMessage.getContentTypeHeader().getValue().toString();
                }
                holder = new ContentHolder(new AsciiString(contentType), HttpResponseStatus.valueOf(responseMessage.getOperation().getStatusCode()), responseBodyBytes);
            }else {
                holder = new ContentHolder(HttpHeaderValues.TEXT_PLAIN, HttpResponseStatus.valueOf(responseMessage.getOperation().getStatusCode()), "");
            }

            // Headers
            for (Map.Entry<String, List<String>> entry : responseMessage.getHeaders().entrySet()) {
                for (String value : entry.getValue()) {
                    holder.headers.put(entry.getKey(), value);
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
            p.addLast(new HttpObjectAggregator(5 * 1024 * 1024)); // 5 MB max  //Integer.MAX_VALUE));
            p.addLast(new IdleStateHandler(60, 30, 0));
            p.addLast(new HttpServerHandler(protocolFactory, configuration.getNamespace().getBasePath().getPath()));
        }
    }

    static class ContentHolder {
        private final AsciiString contentType;
        private final byte[] content;
        private final HttpResponseStatus statusCode;
        private final Map<String, String> headers = new HashMap<>();

        public ContentHolder(AsciiString mimeType, HttpResponseStatus statusCode, byte[] content) {
            this.content = content;
            this.statusCode = statusCode;
            this.contentType = mimeType;
        }
        public ContentHolder(AsciiString mimeType, HttpResponseStatus statusCode, String content) {
            this.content = content.getBytes(StandardCharsets.UTF_8);
            this.statusCode = statusCode;
            this.contentType = mimeType;
        }
    }
}