package apincer.android.mmate.dlna.transport;
import static java.nio.file.StandardCopyOption.REPLACE_EXISTING;
import static apincer.android.mmate.Constants.COVER_ARTS;
import static apincer.android.mmate.Constants.DEFAULT_COVERART_DLNA_RES;
import static apincer.android.mmate.Constants.DEFAULT_COVERART_FILE;
import static io.netty.handler.codec.http.HttpVersion.HTTP_1_1;

import android.content.Context;
import android.util.Log;
import android.util.LruCache;

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
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;
import java.util.concurrent.TimeUnit;

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
    private static final String DEFAULT_COVERART_KEY = "DEFAULT_COVERART_KEY";
    // Configuration constants - making hardcoded values configurable
    private static final int CACHE_SIZE = 50;
    private static final long CACHE_EXPIRY_MS = TimeUnit.MINUTES.toMillis(15);
    private static final int SERVER_BACKLOG = 256;
    private static final int SOCKET_TIMEOUT_MS = 15000;
    private static final int WRITE_BUFFER_LOW = 8 * 1024;
    private static final int WRITE_BUFFER_HIGH = 24 * 1024;
    private static final int RECEIVE_BUFFER_SIZE = 1024;
    private static final int SEND_BUFFER_SIZE = 2048;
    private static final int MAX_HTTP_CONTENT_LENGTH = 5 * 1024 * 1024; // 5 MB

    // Add a memory cache for album art to reduce database queries and disk I/O
    private final LruCache<String, ContentHolder> albumArtCache = new LruCache<>(CACHE_SIZE); // Cache ~50 album arts

    EventLoopGroup bossGroup = new MultiThreadIoEventLoopGroup(1, NioIoHandler.newFactory()); // Single boss thread is usually sufficient
    int processorCount = Runtime.getRuntime().availableProcessors();
    EventLoopGroup workerGroup = new MultiThreadIoEventLoopGroup(processorCount, NioIoHandler.newFactory());

    private Thread serverThread;
    private ChannelFuture channelFuture;
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
                    Log.i(TAG, "Starting Netty4 UPNP Server: " + bindAddress.getHostAddress() + ":" + getListenPort());

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
                    channelFuture = b.bind(getListenPort()).sync();
                    isInitialized = true;

                    Log.i(TAG, "Netty4 UPNP Server started successfully");

                    // Start a background thread to clean up expired cache entries
                    startCacheCleanupTask();
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
            cleanup(); // Ensure resources are released
            throw new InitializationException("Failed to initialize server: " + e.getMessage(), e);
        }
    }

    private void startCacheCleanupTask() {
        Thread cleanupThread = new Thread(() -> {
            try {
                while (isInitialized) {
                    try {
                        purgeExpiredCacheEntries();
                        Thread.sleep(TimeUnit.MINUTES.toMillis(30)); // Clean cache every 15 minutes
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        break;
                    } catch (Exception e) {
                        Log.e(TAG, "Error during cache cleanup", e);
                    }
                }
            } catch (Exception e) {
                Log.e(TAG, "Cache cleanup thread terminated with exception", e);
            }
        });
        cleanupThread.setName("UPNP-Cache-Cleanup");
        cleanupThread.setDaemon(true);
        cleanupThread.start();
    }

    private void purgeExpiredCacheEntries() {
        // LruCache doesn't support removeIf, so we need to collect keys to remove first
        List<String> keysToRemove = new ArrayList<>();

        // Iterate through all keys using LruCache's snapshot method
        Map<String, ContentHolder> snapshot = albumArtCache.snapshot();
        for (Map.Entry<String, ContentHolder> entry : snapshot.entrySet()) {
            if (entry.getValue().isExpired()) {
                keysToRemove.add(entry.getKey());
            }
        }

        // Now remove the expired entries
        for (String key : keysToRemove) {
            albumArtCache.remove(key);
        }

        Log.d(TAG, "Cache cleanup: removed " + keysToRemove.size() + " expired entries");
    }

    synchronized public void stopServer() {
        if (!isInitialized) {
            return; // Already stopped or not initialized
        }

        Log.i(TAG, "Stopping Netty4 UPNP Server");
        isInitialized = false;
        cleanup();

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

        Log.i(TAG, "Netty4 UPNP Server stopped successfully");
    }

    private void cleanup() {
        try {
            if (channelFuture != null) {
                channelFuture.channel().close().sync();
                channelFuture = null;
            }
        } catch (Exception e) {
            Log.e(TAG, "Error closing server channel", e);
        }

        try {
            if (bossGroup != null) {
                bossGroup.shutdownGracefully().sync();
                bossGroup = null;
            }
        } catch (Exception e) {
            Log.e(TAG, "Error shutting down boss group", e);
        }

        try {
            if (workerGroup != null) {
                workerGroup.shutdownGracefully().sync();
                workerGroup = null;
            }
        } catch (Exception e) {
            Log.e(TAG, "Error shutting down worker group", e);
        }
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
                    // Implementation not needed for this usage
                }
            };

            // Initialize default cover art
            initDefaultCoverArt();
        }

        private void initDefaultCoverArt() {
            try {
                if (!defaultCoverartDir.exists()) {
                    Log.d(TAG, "Default cover art not found, creating from assets");
                    FileUtils.createParentDirs(defaultCoverartDir);
                    try (InputStream in = ApplicationUtils.getAssetsAsStream(getContext(), DEFAULT_COVERART_DLNA_RES)) {
                        if (in != null) {
                            Files.copy(in, defaultCoverartDir.toPath(), REPLACE_EXISTING);

                            // Pre-cache the default cover art
                            ByteBuffer buffer = FileUtils.getBytes(defaultCoverartDir);
                            String mime = MimeTypeUtils.getMimeTypeFromPath(defaultCoverartDir.getAbsolutePath());
                            albumArtCache.put(DEFAULT_COVERART_KEY, new ContentHolder(DEFAULT_COVERART_KEY, AsciiString.of(mime), HttpResponseStatus.OK, buffer.array()));
                            Log.d(TAG, "Default cover art cached successfully");
                        } else {
                            Log.e(TAG, "Could not load default cover art from assets");
                        }
                    }
                } else if (albumArtCache.get(DEFAULT_COVERART_KEY) == null) {
                    // If file exists but not in cache
                    ByteBuffer buffer = FileUtils.getBytes(defaultCoverartDir);
                    String mime = MimeTypeUtils.getMimeTypeFromPath(defaultCoverartDir.getAbsolutePath());
                    albumArtCache.put(DEFAULT_COVERART_KEY, new ContentHolder(DEFAULT_COVERART_KEY, AsciiString.of(mime), HttpResponseStatus.OK, buffer.array()));
                    Log.d(TAG, "add default coverart to cache");
                }
            } catch (IOException e) {
                Log.e(TAG, "Failed to initialize default cover art", e);
            }
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
                ContentHolder contentHolder;

                if (uri.startsWith(upnpPath)) {
                   // Log.d(TAG, "Processing UPnP request: " + request.method() + " " + uri);
                    contentHolder = handleUPnpStream(request, data);
                } else if (uri.startsWith("/coverart/")) {
                   // Log.d(TAG, "Processing album art request: " + uri);
                    contentHolder = handleAlbumArt(request, uri);
                } else {
                    Log.d(TAG, "Invalid request path: " + uri);
                    sendForbidden(ctx, request);
                    return;
                }

                DefaultFullHttpResponse response = new DefaultFullHttpResponse(
                        request.protocolVersion(),
                        contentHolder.statusCode,
                        Unpooled.wrappedBuffer(contentHolder.content));

                response.headers().set(HttpHeaderNames.CONTENT_TYPE, contentHolder.contentType);

                for (Map.Entry<String, String> entry : contentHolder.headers.entrySet()) {
                    response.headers().set(AsciiString.of(entry.getKey()), entry.getValue());
                }

                sendAndCleanupConnection(ctx, request, response);
            }
        }

        private ContentHolder handleAlbumArt(FullHttpRequest request, String uri) {
            try {
                String albumUniqueKey = uri.substring("/coverart/".length(), uri.indexOf(".png"));

                // First check cache
                ContentHolder cachedArt = albumArtCache.get(albumUniqueKey);
                if (cachedArt != null) {
                    Log.d(TAG, "Album art cache hit for: " + albumUniqueKey);
                    return cachedArt;
                }

                // Not in cache, fetch from database
                MusicTag tag = MusixMateApp.getInstance().getOrmLite().findByAlbumUniqueKey(albumUniqueKey);
                if (tag != null) {
                    Log.d(TAG, "get coverart for " + albumUniqueKey);
                    File coverFile = FileRepository.getCoverArt(tag);
                    if (coverFile != null && coverFile.exists()) {
                        try {
                            ByteBuffer buffer = FileUtils.getBytes(coverFile);
                            String mime = MimeTypeUtils.getMimeTypeFromPath(coverFile.getAbsolutePath());

                            // Cache the album art for future requests
                            ContentHolder albumArt = new ContentHolder(albumUniqueKey, AsciiString.of(mime), HttpResponseStatus.OK, buffer.array());
                            albumArtCache.put(albumUniqueKey, albumArt);

                            return albumArt;
                        } catch (IOException e) {
                            Log.e(TAG, "Error reading cover file: " + coverFile, e);
                        }
                    } else {
                        Log.d(TAG, "No cover file found for: " + tag.getPath());
                    }
                } else {
                    Log.d(TAG, "No tag found for album key: " + albumUniqueKey);
                }

                // Fall back to default cover art
                ContentHolder defaultArt = albumArtCache.get(DEFAULT_COVERART_KEY);
                if (defaultArt != null) {
                    Log.d(TAG, "Using default cover art for: " + albumUniqueKey);
                    return defaultArt;
                }

                // Last resort - try to load default cover art
                initDefaultCoverArt();
                defaultArt = albumArtCache.get(DEFAULT_COVERART_KEY);
                if (defaultArt != null) {
                    return defaultArt;
                }

                // If everything fails
                Log.e(TAG, "Failed to find any cover art for: " + albumUniqueKey);
                return new ContentHolder(null,
                        HttpHeaderValues.TEXT_PLAIN,
                        HttpResponseStatus.NOT_FOUND,
                        "Cover art not found");

            } catch (Exception e) {
                Log.e(TAG, "Error handling album art request: " + uri, e);
                return new ContentHolder(null,
                        HttpHeaderValues.TEXT_PLAIN,
                        HttpResponseStatus.INTERNAL_SERVER_ERROR,
                        "Error processing album art");
            }
        }

        private  ContentHolder handleUPnpStream(FullHttpRequest request, byte[] bodyBytes) {
            try {
                StreamRequestMessage requestMessage = readRequestMessage(request, bodyBytes);

                StreamResponseMessage responseMessage = upnpStream.process(requestMessage);

                if (responseMessage != null) {
                    return buildResponseMessage(responseMessage);
                } else {
                    Log.d(TAG, "UPnP stream returned null response for: " + request.uri());
                    return new ContentHolder(null,
                            HttpHeaderValues.TEXT_PLAIN,
                            HttpResponseStatus.NOT_FOUND,
                            "Resource not found");
                }
            } catch (Throwable t) {
                Log.e(TAG, "Exception in UPnP stream processing: ", t);
                return new ContentHolder(null,
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

        protected ContentHolder buildResponseMessage(StreamResponseMessage responseMessage) {
            // Create response body
            byte[] responseBodyBytes = responseMessage.hasBody() ? responseMessage.getBodyBytes() : new byte[0];

            String contentType = "application/xml";
            if (responseMessage.getContentTypeHeader() != null) {
                contentType = responseMessage.getContentTypeHeader().getValue().toString();
            }

            ContentHolder holder = new ContentHolder(null,
                    new AsciiString(contentType),
                    HttpResponseStatus.valueOf(responseMessage.getOperation().getStatusCode()),
                    responseBodyBytes);

            // Add headers
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
            p.addLast(new HttpObjectAggregator(MAX_HTTP_CONTENT_LENGTH)); // 5 MB max  //Integer.MAX_VALUE));
            p.addLast(new IdleStateHandler(60, 30, 0));
            p.addLast(new HttpServerHandler(protocolFactory, configuration.getNamespace().getBasePath().getPath()));
        }
    }

    static class ContentHolder {
        private final AsciiString contentType;
        private final byte[] content;
        private final HttpResponseStatus statusCode;
        private final Map<String, String> headers = new HashMap<>();
        final long timestamp;
        final String key;

        public ContentHolder(String key, AsciiString mimeType, HttpResponseStatus statusCode, byte[] content) {
            this.content = content;
            this.statusCode = statusCode;
            this.contentType = mimeType;
            this.timestamp = System.currentTimeMillis();
            this.key = key;
        }
        public ContentHolder(String key, AsciiString mimeType, HttpResponseStatus statusCode, String content) {
            this.content = content.getBytes(StandardCharsets.UTF_8);
            this.statusCode = statusCode;
            this.contentType = mimeType;
            this.key = key;
            this.timestamp = System.currentTimeMillis();
        }

        boolean isExpired() {
            return System.currentTimeMillis() - timestamp > CACHE_EXPIRY_MS &&
                    !DEFAULT_COVERART_KEY.equals(key); // Don't expire default cover art
        }
    }
}