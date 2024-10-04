package apincer.android.mmate.dlna.transport;
import static io.netty.handler.codec.http.HttpVersion.HTTP_1_1;

import android.content.Context;
import android.util.Log;
import android.util.LruCache;

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

import java.io.File;
import java.net.InetAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;

import apincer.android.mmate.MusixMateApp;
import apincer.android.mmate.dlna.MediaServerSession;
import apincer.android.mmate.provider.CoverArtProvider;
import apincer.android.mmate.repository.MusicTag;
import apincer.android.utils.FileUtils;
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
import io.netty.channel.nio.NioEventLoopGroup;
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
import okio.Buffer;
import okio.FileSystem;
import okio.Okio;

public class NettyStreamServerImpl implements StreamServer<StreamServerConfigurationImpl> {
    private static final String TAG = "NettyStreamServerImpl";
    protected int localPort;
    public static final String HTTP_DATE_FORMAT = "EEE, dd MMM yyyy HH:mm:ss zzz";
    public static final String HTTP_DATE_GMT_TIMEZONE = "GMT";

    final protected StreamServerConfigurationImpl configuration;
    private final HCContentServer contentServer;
   // private final NettyContentServer contentServer;

    // for 100 tps
    private final EventLoopGroup bossGroup = new NioEventLoopGroup(1);
    private final EventLoopGroup workerGroup = new NioEventLoopGroup(2);

    private static File coverartDir;

    public NettyStreamServerImpl(Context context, StreamServerConfigurationImpl configuration) {
        this.configuration = configuration;
        this.localPort = configuration.getListenPort();
        this.contentServer = new HCContentServer(context);
       // this.contentServer = new NettyContentServer(context);
        coverartDir = context.getExternalCacheDir();
    }

    public StreamServerConfigurationImpl getConfiguration() {
        return configuration;
    }

    synchronized public void init(InetAddress bindAddress, final Router router) throws InitializationException {
        initServer(bindAddress, router);
        contentServer.init(bindAddress);
    }

    private void initServer(InetAddress bindAddress, final Router router) throws InitializationException {
        Thread thread = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    Log.v(TAG, "Running Netty4 Stream Server: " + bindAddress.getHostAddress() + ":" + getConfiguration().getListenPort());

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

                    MediaServerSession.streamServerHost = bindAddress.getHostAddress();
                    ServerBootstrap b = new ServerBootstrap();
                    b.option(ChannelOption.SO_BACKLOG, 128);  //Set the size of the backlog of TCP connections.  The default and exact meaning of this parameter is JDK specific.
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
                            .childHandler(new HttpServerInitializer(router));
                    Channel ch = b.bind(localPort).sync().channel();
                    ch.closeFuture().sync();
                } catch (Exception ex) {
                    throw new InitializationException("Could not initialize " + getClass().getSimpleName() + ": " + ex, ex);
                } finally {
                    bossGroup.shutdownGracefully();
                    workerGroup.shutdownGracefully();
                }
            }
        });
        thread.start();
    }

    synchronized public int getPort() {
        return this.localPort;
    }

    synchronized public void stop() {
        Log.v(TAG, "Shutting down Netty4 Stream Server");
        try {
            bossGroup.shutdownGracefully().sync();
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
        try {
            workerGroup.shutdownGracefully().sync();
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
        contentServer.stop();
    } 

    @Override
    public void run() {

    }

    @Sharable
    private static class HttpServerHandler extends ChannelInboundHandlerAdapter {
        public static final String TYPE_IMAGE_PNG = "image/png";
        public static final String TYPE_IMAGE_JPEG = "image/jpeg";
        final String upnpPath;
        final UpnpStream upnpStream;
        static final SimpleDateFormat dateFormatter = new SimpleDateFormat(HTTP_DATE_FORMAT, Locale.US);

        public HttpServerHandler(ProtocolFactory protocolFactory, String path) {
            super();
            upnpPath = path;
            upnpStream = new UpnpStream(protocolFactory) {
                @Override
                public void run() {

                }
            };
            dateFormatter.setTimeZone(TimeZone.getTimeZone(HTTP_DATE_GMT_TIMEZONE));
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
         * Sets the Date header for the HTTP response
         *
         * @param response
         *            HTTP response
         */
        private static void setDateHeader(FullHttpResponse response) {
            SimpleDateFormat dateFormatter = new SimpleDateFormat(HTTP_DATE_FORMAT, Locale.US);
            dateFormatter.setTimeZone(TimeZone.getTimeZone(HTTP_DATE_GMT_TIMEZONE));

            Calendar time = new GregorianCalendar();
            response.headers().set(HttpHeaderNames.DATE, dateFormatter.format(time.getTime()));
        }

        private byte[] getDefaultIcon(String albumId) {
            if(albumId.contains(".")) {
                albumId = albumId.substring(0, albumId.indexOf("."));
            }
            MusicTag tag = MusixMateApp.getInstance().getOrmLite().findByAlbumUniqueKey(albumId);
            return MusixMateApp.getInstance().getDefaultNoCoverart(tag);
        }

        /**
         * If Keep-Alive is disabled, attaches "Connection: close" header to the response
         * and closes the connection after the response being sent.
         */
        private void sendAndCleanupConnection(ChannelHandlerContext ctx, FullHttpRequest request, FullHttpResponse response) {
            final boolean keepAlive = HttpUtil.isKeepAlive(request);
            HttpUtil.setContentLength(response, response.content().readableBytes());
            HttpUtil.setKeepAlive(response.headers(), request.protocolVersion(), keepAlive);
            setDateHeader(response);
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
                }else if (uri.startsWith("/album/")) {
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
            String albumId = uri.substring("/album/".length());
            try {

                String path = CoverArtProvider.COVER_ARTS + albumId;
                File pathFile = new File(coverartDir, path);
                if (pathFile.exists()) {
                    Buffer buffer = FileUtils.getBytes(pathFile);
                    return new ContentHolder(AsciiString.of(TYPE_IMAGE_PNG), HttpResponseStatus.OK , buffer.readByteArray());
                }
            } catch (Exception e) {
                Log.e(TAG, "lookupAlbumArt: - not found " + uri, e);
            }

            return new ContentHolder(AsciiString.of(TYPE_IMAGE_JPEG), HttpResponseStatus.OK, getDefaultIcon(albumId));
        }

        private  ContentHolder handleUPnpStream(FullHttpRequest request, byte[] bodyBytes) {
            ContentHolder holder;
            try {
                StreamRequestMessage requestMessage = readRequestMessage(request, bodyBytes);
                // Log.v(TAG, "Processing new request message: " + requestMessage);

                String userAgent = getUserAgent(requestMessage);
                if("CyberGarage-HTTP/1.0".equals(userAgent)) { // ||
                    // "Panasonic iOS VR-CP UPnP/2.0".equals(userAgent)) {//     requestMessage.getHeaders().getFirstHeader("User-agent"))) {
                    Log.v(TAG, "Interim FIX for MConnect on IPadOS 18 beta, return all songs for MConnect(fix show only 20 songs)");
                    MediaServerSession.forceFullContent = true;
                }

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

    private static class HttpServerInitializer extends ChannelInitializer<SocketChannel> {
        private final Router router;
        public HttpServerInitializer(Router router) {
            this.router = router;
        }

        @Override
        public void initChannel(SocketChannel ch) {
            ChannelPipeline p = ch.pipeline();
            p.addLast(new HttpServerCodec());
            p.addLast(new HttpObjectAggregator(5 * 1024 * 1024)); // 5 MB max  //Integer.MAX_VALUE));
            p.addLast(new IdleStateHandler(60, 30, 0));
            p.addLast(new HttpServerHandler(router.getProtocolFactory(), router.getConfiguration().getNamespace().getBasePath().getPath()));
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