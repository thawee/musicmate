package apincer.android.mmate.dlna.transport.netty;

import static android.content.Context.BIND_AUTO_CREATE;
import static java.nio.file.StandardCopyOption.REPLACE_EXISTING;
import static apincer.android.mmate.Constants.COVER_ARTS;
import static apincer.android.mmate.Constants.DEFAULT_COVERART_DLNA_RES;
import static apincer.android.mmate.Constants.DEFAULT_COVERART_FILE;
import static apincer.android.mmate.dlna.MediaServerConfiguration.WEB_SERVER_PORT;
import static io.netty.handler.codec.http.HttpResponseStatus.BAD_REQUEST;
import static io.netty.handler.codec.http.HttpResponseStatus.INTERNAL_SERVER_ERROR;
import static io.netty.handler.codec.http.HttpResponseStatus.METHOD_NOT_ALLOWED;
import static io.netty.handler.codec.http.HttpResponseStatus.NOT_FOUND;
import static io.netty.handler.codec.http.HttpResponseStatus.OK;
import static io.netty.handler.codec.http.HttpVersion.HTTP_1_1;

import android.annotation.SuppressLint;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.IBinder;
import android.util.Log;
import android.util.LruCache;

import com.google.gson.Gson;

import org.jupnp.transport.Router;
import org.jupnp.transport.spi.InitializationException;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.net.InetAddress;
import java.nio.file.Files;
import java.util.Map;
import java.util.concurrent.ThreadFactory;

import apincer.android.mmate.dlna.content.WebSocketContent;
import apincer.android.mmate.dlna.transport.StreamServerConfigurationImpl;
import apincer.android.mmate.dlna.transport.StreamServerImpl;
import apincer.android.mmate.playback.NowPlaying;
import apincer.android.mmate.playback.PlaybackService;
import apincer.android.mmate.repository.FileRepository;
import apincer.android.mmate.utils.ApplicationUtils;
import apincer.android.mmate.utils.MimeTypeUtils;
import apincer.android.utils.FileUtils;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.MultiThreadIoEventLoopGroup;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.WriteBufferWaterMark;
import io.netty.channel.group.ChannelGroup;
import io.netty.channel.group.DefaultChannelGroup;
import io.netty.channel.nio.NioIoHandler;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.DefaultHttpResponse;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpChunkedInput;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpHeaderValues;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpResponse;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpServerCodec;
import io.netty.handler.codec.http.HttpUtil;
import io.netty.handler.codec.http.LastHttpContent;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import io.netty.handler.codec.http.websocketx.WebSocketFrame;
import io.netty.handler.codec.http.websocketx.WebSocketServerProtocolHandler;
import io.netty.handler.stream.ChunkedFile;
import io.netty.handler.stream.ChunkedWriteHandler;
import io.netty.handler.timeout.IdleStateEvent;
import io.netty.handler.timeout.IdleStateHandler;
import io.netty.util.AsciiString;
import io.netty.util.CharsetUtil;
import io.netty.util.concurrent.DefaultThreadFactory;
import io.netty.util.concurrent.GlobalEventExecutor;
import io.reactivex.rxjava3.disposables.CompositeDisposable;

public class NettyWebServerImpl extends StreamServerImpl.StreamServer {
    private final Context context;
    private static final String TAG = "NettyWebServer";
    private static final String DEFAULT_COVERART_KEY = "DEFAULT_COVERART_KEY";
    private static final int CACHE_SIZE = 100;

    // Add a memory cache for album art to reduce database queries and disk I/O
    private final LruCache<String, NettyContentHolder> albumArtCache = new LruCache<>(CACHE_SIZE); // Cache ~50 album arts

    // Audio streaming optimizations - now with adaptive configuration
   // private int AUDIO_CHUNK_SIZE = 16384; // 16KB chunks for audio streaming, will be adjusted
   // private int INITIAL_BUFFER_SIZE = 65536; // 64KB initial buffer for quick start
    private int HIGH_WATERMARK = 262144; // 256KB high watermark for buffer control
    private int LOW_WATERMARK = 131072; // 128KB low watermark

    private final int serverPort;
    private final EventLoopGroup bossGroup;
    private final EventLoopGroup workerGroup;
    private Channel serverChannel;
    private Thread serverThread;
    private volatile boolean isRunning = false;

    private final WebSocketContent wsContent = new WebSocketContent();
    private final WebSocketFrameHandler wsHandler = new WebSocketFrameHandler();

    private PlaybackService playbackService;
    private boolean isPlaybackServiceBound = false;
    private final CompositeDisposable disposables = new CompositeDisposable();

    //RFC 1123 format required by HTTP/DLNA // EEE, dd MMM yyyy HH:mm:ss z
   // DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern("EEE, dd MMM yyyy HH:mm:ss z");

    private final ServiceConnection serviceConnection = new ServiceConnection() {
        @SuppressLint("CheckResult")
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            // Get the binder from the service and set the mediaServerService instance
            PlaybackService.PlaybackServiceBinder binder = (PlaybackService.PlaybackServiceBinder) service;
            playbackService = binder.getService();
            isPlaybackServiceBound = true;
            if(playbackService != null) {
                // Add the subscription to the CompositeDisposable
                disposables.add(
                        playbackService.getNowPlayingSubject().subscribe(wsHandler::broadcastNowPlaying)
                );
            }
            wsContent.setPlaybackService(playbackService);
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            playbackService = null;
            isPlaybackServiceBound = false;
            wsContent.setPlaybackService(null);
            // Dispose of all subscriptions when the service disconnects
            disposables.clear();
        }
    };

    public NettyWebServerImpl(Context context, Router router, StreamServerConfigurationImpl configuration) {
        super(context, router, configuration);
        this.context = context;
        this.serverPort = WEB_SERVER_PORT;

        // Initialize adaptive buffer settings based on device capabilities
        initializeBuffers();

        // Optimize thread allocation based on device capabilities
        // Name threads for better debugging
        ThreadFactory bossThreadFactory = new DefaultThreadFactory("webui-boss");
        ThreadFactory workerThreadFactory = new DefaultThreadFactory("webui-worker");
        int processorCount = Runtime.getRuntime().availableProcessors();

        this.bossGroup = new MultiThreadIoEventLoopGroup(1, bossThreadFactory, NioIoHandler.newFactory());
        this.workerGroup = new MultiThreadIoEventLoopGroup(processorCount, workerThreadFactory, NioIoHandler.newFactory());
    }

    /**
     * Initialize buffer sizes adaptively based on device capabilities
     */
    private void initializeBuffers() {
       // AUDIO_CHUNK_SIZE = 8192;   // 8KB chunks
       //     INITIAL_BUFFER_SIZE = 32768;  // 32KB initial
            HIGH_WATERMARK = 131072;   // 128KB high
            LOW_WATERMARK = 65536;     // 64KB low

        // Network type-based adjustments. always use wifi
        // On WiFi we can be more aggressive with buffer sizes
       // AUDIO_CHUNK_SIZE = (int)(AUDIO_CHUNK_SIZE * 1.5);
    }

    @Override
    public void initServer(InetAddress bindAddress) throws InitializationException {
        if (isRunning) {
            Log.w(TAG, "Http WebUI server already running");
            return;
        }

        serverThread = new Thread(() -> {
            // Web UI handler for music management
            try {
                ApplicationUtils.deleteFiles(getContext(), "webui");
                ApplicationUtils.copyFileOrDirToCache(getContext(), "webui");
            } catch (IOException e) {
                throw new RuntimeException(e);
            }

            try {
                ServerBootstrap b = new ServerBootstrap();

                // Configure server with optimal settings for audio streaming
                b.group(bossGroup, workerGroup)
                        .channel(NioServerSocketChannel.class)
                        //.option(ChannelOption.SO_BACKLOG, 128)
                        .option(ChannelOption.SO_REUSEADDR, true)
                        .option(ChannelOption.TCP_NODELAY, true)  // Critical for audio streaming
                        .option(ChannelOption.SO_KEEPALIVE, true)
                        //.option(ChannelOption.SO_RCVBUF, 16384)   // 16KB receive buffer
                        //.option(ChannelOption.SO_SNDBUF, INITIAL_BUFFER_SIZE) // Adaptive send buffer
                        //.option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 5000)
                        .childOption(ChannelOption.WRITE_BUFFER_WATER_MARK,
                                new WriteBufferWaterMark(LOW_WATERMARK, HIGH_WATERMARK));

                b.childHandler(new ContentServerInitializer());

                // Bind and start to accept incoming connections
                ChannelFuture f = b.bind(bindAddress.getHostAddress(), serverPort).sync();
                serverChannel = f.channel();
                isRunning = true;
                Log.i(TAG, "WebUI Server started on " +
                        bindAddress.getHostAddress() + ":" + serverPort+" successfully.");

                // Bind to the MediaServerService as soon as this service is created
                Intent intent = new Intent(getContext(), PlaybackService.class);
                getContext().bindService(intent, serviceConnection, BIND_AUTO_CREATE);

                // Block this thread until the server channel is closed.
                f.channel().closeFuture().sync();
            } catch (Exception ex) {
                Log.e(TAG, "WebUI Server initialization failed: " + ex.getMessage(), ex);
            } finally {
                // Clean up resources if startup fails
                stopServer();
            }
        }, "WebServer");

        serverThread.start();
    }

    @Override
    public void stopServer() {
        isRunning = false;

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

        if(isPlaybackServiceBound) {
            context.unbindService(serviceConnection);
        }

        // Also clear disposables on full server stop
        disposables.clear();

        Log.i(TAG, "Http WebUI server stopped successfully");
    }

    @Override
    protected String getServerVersion() {
        return "Netty/4.2.x";
    }

    public Context getContext() {
        return context;
    }

    public class ContentServerInitializer extends ChannelInitializer<SocketChannel> {
        @Override
        protected void initChannel(SocketChannel ch) {

            ChannelPipeline pipeline = ch.pipeline();
            // 1. Decodes bytes to HTTP requests and encodes responses back to bytes.
            pipeline.addLast(new HttpServerCodec());

            // 2. Aggregates HTTP message chunks into a single FullHttpRequest.
            // This is now safe because there are no large file uploads.
            pipeline.addLast(new HttpObjectAggregator(65536)); // 64KB max request size

            // 3. Enables asynchronous writing of large data streams (like file downloads).
            // Still best practice for files, even under 10 MB.
            pipeline.addLast(new ChunkedWriteHandler());

            // 4. Handles idle connection timeouts.
            pipeline.addLast(new IdleStateHandler(120, 60, 1800));
            //pipeline.addLast(new IdleStateHandler(120, 60, 0));

            // 5. Handles the WebSocket handshake and protocol details for any requests to "/ws".
            // It will pass all other requests down the pipeline. THIS MUST COME FIRST.
            pipeline.addLast(new WebSocketServerProtocolHandler(CONTEXT_PATH_WEBSOCKET, null, true));

            // 6. Your custom logic for handling WebSocket messages (frames).
            pipeline.addLast(wsHandler);

            // 7. Your custom logic for all other HTTP requests (HTML, CSS, images).
            pipeline.addLast(new ContentServerHandler());

        }
    }

    private class ContentServerHandler extends SimpleChannelInboundHandler<FullHttpRequest> {
        final File defaultCoverartDir;

        public ContentServerHandler() {
            defaultCoverartDir = new File(getCoverartDir(COVER_ARTS),DEFAULT_COVERART_FILE);
            initDefaultCoverArt();
        }

        @Override
        protected void channelRead0(ChannelHandlerContext ctx, FullHttpRequest request) {
            // If the request is for the WebSocket, pass it to the next handler in the pipeline
            if (CONTEXT_PATH_WEBSOCKET.equalsIgnoreCase(request.uri())) {
                ctx.fireChannelRead(request.retain());
                return;
            }

            // Validate request
            if (!request.decoderResult().isSuccess()) {
                sendError(ctx, BAD_REQUEST);
                return;
            }

            // Check if we support the HTTP method
            if (!request.method().equals(HttpMethod.GET) && !request.method().equals(HttpMethod.HEAD)) {
                sendError(ctx, METHOD_NOT_ALLOWED);
                return;
            }

            // Get requested content
            NettyContentHolder holder = getContent(ctx, request);

            // Check if content exists
            if (holder == null || !holder.exists()) {
                sendError(ctx, NOT_FOUND);
                return;
            }

            boolean keepAlive = HttpUtil.isKeepAlive(request);

            // Handle file content
            if (holder.getFilePath() != null) {
                serveFileContent(ctx, request, holder, keepAlive);
                return;
            }

            // Should never reach here if proper validation is done
            sendError(ctx, INTERNAL_SERVER_ERROR);
        }

        private void serveFileContent(ChannelHandlerContext ctx, FullHttpRequest request,
                                      NettyContentHolder holder, boolean keepAlive) {
            try {
                File file = new File(holder.getFilePath());
                long fileLength = file.length();


                // Handle normal file serving
                HttpResponse response = new DefaultHttpResponse(HTTP_1_1, OK);
                HttpUtil.setContentLength(response, fileLength);
                setContentTypeHeader(response, holder.getContentType().toString());
                setDateAndCacheHeaders(response, file.getName());

                if (keepAlive) {
                    response.headers().set(HttpHeaderNames.CONNECTION, HttpHeaderValues.KEEP_ALIVE);
                }

                // Write the initial line and headers
                ctx.write(response);

                // Write the content
                if (request.method() != HttpMethod.HEAD) {
                    // Use optimized chunk size for audio streaming
                    RandomAccessFile rfile = new RandomAccessFile(file, "r");
                    ChunkedFile chunkedFile = new ChunkedFile(rfile, 0, fileLength, 16384);

                    ctx.write(new HttpChunkedInput(chunkedFile));
                }

                // Write the end marker
                ChannelFuture lastContentFuture = ctx.writeAndFlush(LastHttpContent.EMPTY_LAST_CONTENT);

                // Close the connection if not keep-alive
                if (!keepAlive) {
                    lastContentFuture.addListener(ChannelFutureListener.CLOSE);
                }

            } catch (IOException e) {
                Log.e(TAG, "Failed to serve file: " + e.getMessage(), e);
                sendError(ctx, INTERNAL_SERVER_ERROR);
            }
        }

        private NettyContentHolder handleAlbumArt(FullHttpRequest request, String uri) {
            try {
                String albumUniqueKey = uri.substring(CONTEXT_PATH_COVERART.length(), uri.indexOf(".png"));

                // First check cache
                NettyContentHolder cachedArt = albumArtCache.get(albumUniqueKey);
                if (cachedArt != null) {
                    //Log.d(TAG, "Album art cache hit for: " + albumUniqueKey);
                    return cachedArt;
                }

                // Not in cache, fetch from database
                    File coverFile = FileRepository.getCoverArt(albumUniqueKey);
                    if (coverFile != null && coverFile.exists()) {
                        String mime = MimeTypeUtils.getMimeTypeFromPath(coverFile.getAbsolutePath());

                        // Cache the album art for future requests
                        NettyContentHolder albumArt = new NettyContentHolder(albumUniqueKey, AsciiString.of(mime), coverFile.getAbsolutePath());
                        albumArtCache.put(albumUniqueKey, albumArt);

                        return albumArt;
                    }

                // Fall back to default cover art
                NettyContentHolder defaultArt = albumArtCache.get(DEFAULT_COVERART_KEY);
                if (defaultArt != null) {
                    //Log.d(TAG, "Using default cover art for: " + albumUniqueKey);
                    return defaultArt;
                }

                // If everything fails
                Log.e(TAG, "Failed to find any cover art for: " + albumUniqueKey);
                return new NettyContentHolder(null,
                        HttpHeaderValues.TEXT_PLAIN,
                        NOT_FOUND,
                        "Cover art not found");

            } catch (Exception e) {
                Log.e(TAG, "Error handling album art request: " + uri, e);
                return new NettyContentHolder(null,
                        HttpHeaderValues.TEXT_PLAIN,
                        INTERNAL_SERVER_ERROR,
                        "Error processing album art");
            }
        }

        private void setContentTypeHeader(HttpResponse response, String contentType) {
            response.headers().set(HttpHeaderNames.CONTENT_TYPE, contentType);
        }

        private void setDateAndCacheHeaders(HttpResponse response, String fileName) {
            // Add server header
            response.headers().set(HttpHeaderNames.SERVER, getFullServerName());

            // Add date header with RFC 1123 format required by HTTP/DLNA
          //  ZoneId zoneId = ZoneId.systemDefault();
          //  ZonedDateTime now = ZonedDateTime.now(zoneId);

           // response.headers().set(HttpHeaderNames.DATE, now.format(dateFormatter));

            // Set content disposition for better download behavior
            response.headers().set(
                    HttpHeaderNames.CONTENT_DISPOSITION,
                    "inline; filename=\"" + fileName + "\""
            );

            // For audio streaming, we want minimal caching to ensure players get fresh content
            // but allow for short-term caching to optimize repeated segment requests
            response.headers().set(
                    HttpHeaderNames.CACHE_CONTROL,
                    "private, max-age=30"  // 30 seconds of caching can help with seeking operations
            );
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
                            String mime = MimeTypeUtils.getMimeTypeFromPath(defaultCoverartDir.getAbsolutePath());
                            albumArtCache.put(DEFAULT_COVERART_KEY, new NettyContentHolder(DEFAULT_COVERART_KEY, AsciiString.of(mime), defaultCoverartDir.getAbsolutePath()));
                            Log.d(TAG, "Default cover art cached successfully");
                        } else {
                            Log.e(TAG, "Could not load default cover art from assets");
                        }
                    }
                } else if (albumArtCache.get(DEFAULT_COVERART_KEY) == null) {
                    // If file exists but not in cache
                    String mime = MimeTypeUtils.getMimeTypeFromPath(defaultCoverartDir.getAbsolutePath());
                    albumArtCache.put(DEFAULT_COVERART_KEY, new NettyContentHolder(DEFAULT_COVERART_KEY, AsciiString.of(mime), defaultCoverartDir.getAbsolutePath()));
                   // Log.d(TAG, "add default coverart to cache");
                }
            } catch (IOException e) {
                Log.e(TAG, "Failed to initialize default cover art", e);
            }
        }

        private void sendError(ChannelHandlerContext ctx, HttpResponseStatus status) {
            FullHttpResponse response = new DefaultFullHttpResponse(
                    HTTP_1_1, status,
                    Unpooled.copiedBuffer("Error: " + status + "\r\n", CharsetUtil.UTF_8)
            );

            response.headers().set(HttpHeaderNames.CONTENT_TYPE, "text/plain; charset=UTF-8");

            // Close the connection as soon as the error message is sent
            ctx.writeAndFlush(response).addListener(ChannelFutureListener.CLOSE);
        }

        @Override
        public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
            if (evt instanceof IdleStateEvent) {
                Log.d(TAG, "Connection idle timeout, closing: " + ctx.channel().remoteAddress());
                ctx.close();
            } else {
                super.userEventTriggered(ctx, evt);
            }
        }

        @Override
        public void channelReadComplete(ChannelHandlerContext ctx) {
            ctx.flush();
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
            Log.w(TAG, "Exception caught in content server: " + cause.getMessage(), cause);
            if (ctx.channel().isActive()) {
                sendError(ctx, INTERNAL_SERVER_ERROR);
            }
        }

        private NettyContentHolder getContent(ChannelHandlerContext ctx, FullHttpRequest request) {
            String requestUri = request.uri();
            if (requestUri.equals("/")) {
                requestUri = "/index.html";
            }
            if (requestUri.startsWith(CONTEXT_PATH_COVERART)) {
                return handleAlbumArt(request, requestUri);
            }else {
                return getFile(ctx, request, requestUri);
            }
        }

        private NettyContentHolder getFile(ChannelHandlerContext ctx, FullHttpRequest request, String path) {

            try {
                File filePath = new File(getContext().getFilesDir(), "webui");
                if(path.contains("?")) {
                    path = path.substring(0, path.indexOf("?"));
                }
                filePath = new File(filePath, path);

                // Verify the file exists
                if (!filePath.exists() || !filePath.canRead()) {
                    Log.w(TAG, "File not found or not readable: " + filePath.getPath());
                    return null;
                }

                // Get MIME type for the file
                String mimeType = MimeTypeUtils.getMimeTypeFromPath(filePath.getPath());

               // Log.i(TAG, "Serving media: " + tag.getTitle() + " [" + mimeType + "]");
                return new NettyContentHolder(null, new AsciiString(mimeType), filePath.getPath());
            } catch (Exception e) {
                Log.e(TAG, "Error retrieving song: " + e.getMessage(), e);
                return null;
            }
        }
    }

    private class WebSocketFrameHandler extends SimpleChannelInboundHandler<WebSocketFrame> {

        @SuppressLint("CheckResult")
        public WebSocketFrameHandler() {
        }

        private final ChannelGroup channels = new DefaultChannelGroup(GlobalEventExecutor.INSTANCE);
        private final Gson gson = new Gson();

        @Override
        public void handlerAdded(ChannelHandlerContext ctx) {
            channels.add(ctx.channel());
        }

        @Override
        public void handlerRemoved(ChannelHandlerContext ctx) {
            channels.remove(ctx.channel());
        }

        @Override
        protected void channelRead0(ChannelHandlerContext ctx, WebSocketFrame frame) {
            if (frame instanceof TextWebSocketFrame) {
                String request = ((TextWebSocketFrame) frame).text();
                Log.d(TAG, "Received message: " + request);
                Map<String, Object> message = gson.fromJson(request, Map.class);
                String command = message.getOrDefault("command", "").toString();

                Map<String, Object> response = wsContent.handleCommand(command, message);
                if(response != null) {
                    ctx.channel().writeAndFlush(new TextWebSocketFrame(gson.toJson(response)));
                    Log.d(TAG, "Response message: " + gson.toJson(response));
                }
            } else {
                String message = "unsupported frame type: " + frame.getClass().getName();
                throw new UnsupportedOperationException(message);
            }
        }

        private void broadcastNowPlaying(NowPlaying nowPlaying) {
            Map<String, Object> response = wsContent.getNowPlaying(nowPlaying);
            if(response != null) {
                channels.writeAndFlush(new TextWebSocketFrame(gson.toJson(response)));
            }
        }
    }
}
