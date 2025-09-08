package apincer.android.mmate.dlna.transport;

import static android.content.Context.BIND_AUTO_CREATE;
import static java.nio.file.StandardCopyOption.REPLACE_EXISTING;
import static apincer.android.mmate.Constants.COVER_ARTS;
import static apincer.android.mmate.Constants.DEFAULT_COVERART_DLNA_RES;
import static apincer.android.mmate.Constants.DEFAULT_COVERART_FILE;
import static apincer.android.mmate.dlna.MediaServerConfiguration.WEB_SERVER_PORT;
import static apincer.android.mmate.utils.StringUtils.isEmpty;
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

import androidx.annotation.NonNull;

import com.google.gson.Gson;
import com.j256.ormlite.dao.Dao;
import com.j256.ormlite.stmt.DeleteBuilder;

import org.jupnp.model.meta.RemoteDevice;
import org.jupnp.transport.Router;
import org.jupnp.transport.spi.InitializationException;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.net.InetAddress;
import java.nio.file.Files;
import java.sql.SQLException;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import apincer.android.mmate.MusixMateApp;
import apincer.android.mmate.info.MusicInfoService;
import apincer.android.mmate.info.TrackInfo;
import apincer.android.mmate.playback.NowPlaying;
import apincer.android.mmate.playback.PlaybackService;
import apincer.android.mmate.repository.FileRepository;
import apincer.android.mmate.repository.database.MusicTag;
import apincer.android.mmate.repository.PlaylistRepository;
import apincer.android.mmate.repository.TagRepository;
import apincer.android.mmate.repository.database.QueueItem;
import apincer.android.mmate.repository.model.PlaylistEntry;
import apincer.android.mmate.utils.ApplicationUtils;
import apincer.android.mmate.utils.MimeTypeUtils;
import apincer.android.mmate.utils.MusicTagUtils;
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
import io.netty.util.concurrent.GlobalEventExecutor;
import io.reactivex.rxjava3.subjects.BehaviorSubject;

public class NettyWebServerImpl extends StreamServerImpl.StreamServer {
    private final Context context;
    private static final String TAG = "NettyWebServer";
    private static final String DEFAULT_COVERART_KEY = "DEFAULT_COVERART_KEY";
    private static final int CACHE_SIZE = 100;

    // Add a memory cache for album art to reduce database queries and disk I/O
    private final LruCache<String, NettyContentHolder> albumArtCache = new LruCache<>(CACHE_SIZE); // Cache ~50 album arts

    // Audio streaming optimizations - now with adaptive configuration
    private int AUDIO_CHUNK_SIZE = 16384; // 16KB chunks for audio streaming, will be adjusted
    private int INITIAL_BUFFER_SIZE = 65536; // 64KB initial buffer for quick start
    private int HIGH_WATERMARK = 262144; // 256KB high watermark for buffer control
    private int LOW_WATERMARK = 131072; // 128KB low watermark

    private final int serverPort;
    private final EventLoopGroup bossGroup;
    private final EventLoopGroup workerGroup;
    private Channel serverChannel;
    private Thread serverThread;
    private volatile boolean isRunning = false;

    //RFC 1123 format required by HTTP/DLNA // EEE, dd MMM yyyy HH:mm:ss z
    DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern("EEE, dd MMM yyyy HH:mm:ss z");

    public NettyWebServerImpl(Context context, Router router, StreamServerConfigurationImpl configuration) {
        super(context, router, configuration);
        this.context = context;
        this.serverPort = WEB_SERVER_PORT;

        // Initialize adaptive buffer settings based on device capabilities
        initializeBuffers();

        // Optimize thread allocation based on device capabilities
        int cpuCores = Runtime.getRuntime().availableProcessors();
        this.bossGroup = new MultiThreadIoEventLoopGroup(2, NioIoHandler.newFactory());
        this.workerGroup = new MultiThreadIoEventLoopGroup(Math.min(4,cpuCores), NioIoHandler.newFactory());
    }

    /**
     * Initialize buffer sizes adaptively based on device capabilities
     */
    private void initializeBuffers() {
        AUDIO_CHUNK_SIZE = 8192;   // 8KB chunks
            INITIAL_BUFFER_SIZE = 32768;  // 32KB initial
            HIGH_WATERMARK = 131072;   // 128KB high
            LOW_WATERMARK = 65536;     // 64KB low

        // Network type-based adjustments. always use wifi
        // On WiFi we can be more aggressive with buffer sizes
        AUDIO_CHUNK_SIZE = (int)(AUDIO_CHUNK_SIZE * 1.5);
    }

    @Override
    public void initServer(InetAddress bindAddress) throws InitializationException {
        if (isRunning) {
            Log.w(TAG, "Http WebUI server already running");
            return;
        }

       // Log.d(TAG, "Starting netty content server: " + bindAddress.getHostAddress() + ":" + serverPort);

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
                        .option(ChannelOption.SO_BACKLOG, 128)
                        .option(ChannelOption.SO_REUSEADDR, true)
                        .option(ChannelOption.TCP_NODELAY, true)  // Critical for audio streaming
                        .option(ChannelOption.SO_KEEPALIVE, true)
                        .option(ChannelOption.SO_RCVBUF, 16384)   // 16KB receive buffer
                        .option(ChannelOption.SO_SNDBUF, INITIAL_BUFFER_SIZE) // Adaptive send buffer
                        .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 5000)
                        .childOption(ChannelOption.WRITE_BUFFER_WATER_MARK,
                                new WriteBufferWaterMark(LOW_WATERMARK, HIGH_WATERMARK));

                b.childHandler(new ContentServerInitializer());

                // Bind and start to accept incoming connections
                ChannelFuture f = b.bind(bindAddress.getHostAddress(), serverPort).sync();
                serverChannel = f.channel();
                isRunning = true;
                Log.i(TAG, "\tHttp WebUI server started successfully on " +
                        bindAddress.getHostAddress() + ":" + serverPort);

            } catch (Exception ex) {
                isRunning = false;
                Log.e(TAG, "Http WebUI server initialization failed: " + ex.getMessage(), ex);
                // Clean up resources if startup fails
                try {
                    if (serverChannel != null) {
                        serverChannel.close();
                        serverChannel = null;
                    }
                } catch (Exception ignored) {}
            }
        }, "WebUIServer");

        serverThread.start();

        // Wait a short time to verify server started properly
        try {
            Thread.sleep(500);
            if (!isRunning) {
                throw new InitializationException("Failed to start http WebUI server");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new InitializationException("Http WebUI server start was interrupted", e);
        }
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
            pipeline.addLast(new IdleStateHandler(120, 60, 0));

            // 5. Handles the WebSocket handshake and protocol details for any requests to "/ws".
            // It will pass all other requests down the pipeline. THIS MUST COME FIRST.
            pipeline.addLast(new WebSocketServerProtocolHandler("/ws", null, true));

            // 6. Your custom logic for handling WebSocket messages (frames).
            pipeline.addLast(new WebSocketFrameHandler());

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
            if ("/ws".equalsIgnoreCase(request.uri())) {
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
                String albumUniqueKey = uri.substring("/coverart/".length(), uri.indexOf(".png"));

                // First check cache
                NettyContentHolder cachedArt = albumArtCache.get(albumUniqueKey);
                if (cachedArt != null) {
                    //Log.d(TAG, "Album art cache hit for: " + albumUniqueKey);
                    return cachedArt;
                }

                // Not in cache, fetch from database
                MusicTag tag = MusixMateApp.getInstance().getOrmLite().findByAlbumUniqueKey(albumUniqueKey);
                if (tag != null) {
                    File coverFile = FileRepository.getCoverArt(tag);
                    if (coverFile != null && coverFile.exists()) {
                        String mime = MimeTypeUtils.getMimeTypeFromPath(coverFile.getAbsolutePath());

                        // Cache the album art for future requests
                        NettyContentHolder albumArt = new NettyContentHolder(albumUniqueKey, AsciiString.of(mime), coverFile.getAbsolutePath());
                        albumArtCache.put(albumUniqueKey, albumArt);

                        return albumArt;
                    }
                } else {
                    Log.d(TAG, "No tag found for album key: " + albumUniqueKey);
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
            ZoneId zoneId = ZoneId.systemDefault();
            ZonedDateTime now = ZonedDateTime.now(zoneId);

            response.headers().set(HttpHeaderNames.DATE, now.format(dateFormatter));

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
            //errorCounter.incrementAndGet();
            if (ctx.channel().isActive()) {
                sendError(ctx, INTERNAL_SERVER_ERROR);
            }
        }

        private NettyContentHolder getContent(ChannelHandlerContext ctx, FullHttpRequest request) {
            String requestUri = request.uri();
            if (requestUri.equals("/")) {
                requestUri = "/index.html";
            }
            if (requestUri.startsWith("/coverart/")) {
                // Log.d(TAG, "Processing album art request: " + uri);
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
        private final MusicInfoService musicInfoService = new MusicInfoService();

        private PlaybackService playbackService;
        private boolean isPlaybackServiceBound = false;

        private final ServiceConnection serviceConnection = new ServiceConnection() {
            @SuppressLint("CheckResult")
            @Override
            public void onServiceConnected(ComponentName name, IBinder service) {
                // Get the binder from the service and set the mediaServerService instance
                PlaybackService.PlaybackServiceBinder binder = (PlaybackService.PlaybackServiceBinder) service;
                playbackService = binder.getService();
                isPlaybackServiceBound = true;
                playbackService.getNowPlayingSubject().subscribe(nowPlaying -> broadcastNowPlaying(nowPlaying));
               // Log.i(TAG, "PlaybackService bound successfully.");
            }

            @Override
            public void onServiceDisconnected(ComponentName name) {
                isPlaybackServiceBound = false;
                playbackService = null;
               // Log.w(TAG, "PlaybackService disconnected unexpectedly.");
            }
        };

        @SuppressLint("CheckResult")
        public WebSocketFrameHandler() {
            // Bind to the MediaServerService as soon as this service is created
            Intent intent = new Intent(getContext(), PlaybackService.class);
            getContext().bindService(intent, serviceConnection, BIND_AUTO_CREATE);
            //MusixMateApp.getInstance().getNowPlayingSubject().subscribe(this::broadcastNowPlaying);
        }

        private final ChannelGroup channels = new DefaultChannelGroup(GlobalEventExecutor.INSTANCE);
        private final Gson gson = new Gson();

        @Override
        public void handlerAdded(ChannelHandlerContext ctx) {
            channels.add(ctx.channel());
           // System.out.println("Client connected: " + ctx.channel().remoteAddress());
        }

        @Override
        public void handlerRemoved(ChannelHandlerContext ctx) {
            channels.remove(ctx.channel());
           // System.out.println("Client disconnected: " + ctx.channel().remoteAddress());
        }

        @Override
        protected void channelRead0(ChannelHandlerContext ctx, WebSocketFrame frame) {
            if (frame instanceof TextWebSocketFrame) {
                String request = ((TextWebSocketFrame) frame).text();
                Log.d(TAG, "Received message: " + request);
                Map<String, Object> message = gson.fromJson(request, Map.class);
                String command = message.getOrDefault("command", "").toString();

                // Use a switch statement for clarity
                switch (command) {
                    case "browse":
                        String path = message.getOrDefault("path", "Library").toString();
                        sendBrowseResult(ctx, path);
                        break;
                    case "getNowPlaying":
                        sendNowPlaying(ctx);
                        break;
                    case "getRenderers": // Corrected from "dlnaRenderers"
                        //System.out.println("getRenderers: ");
                        sendDlnaRenderers(ctx);
                        break;
                    case "setRenderer": // Corrected from "dlnaRenderers"
                       // System.out.println("setRenderer: "+ message.get("udn"));
                        setRenderer((String) message.get("udn"));
                        sendDlnaRenderers(ctx);
                        break;
                    case "getQueue": // Corrected from "updateQueue"
                        sendQueueUpdate(ctx); // Send queue only to the requesting client
                        break;
                    case "addToQueue":
                        handleAddToQueue((String) message.get("id")); // Use ID instead of name
                        break;
                    case "emptyQueue":
                        handleEmptyQueue();
                        sendQueueUpdate(ctx);
                        break;
                    case "play":
                        // Add logic to start playing the song with this ID
                        handlePlay((String) message.get("id"));
                        sendNowPlaying(ctx);
                        break;
                    case "playFromContext":
                        handlePlayFormContext((String) message.get("id"), (String) message.get("path"));
                        sendQueueUpdate(ctx);
                        sendNowPlaying(ctx);
                        break;
                    case "next":
                        handlePlayNext();
                        break;
                    case "previous":
                        handlePlayPrevious();
                        break;
                    case "getStats":
                        handleGetStats(ctx);
                        break;
                    case "getTrackDetails":
                        handleGetTrackDetails(ctx, (String) message.get("id"));
                        break;
                    case "getTrackInfo":
                        String artist = message.get("artist").toString();
                        String album = message.get("album").toString();
                        handleGetTrackInfo(ctx.channel(), artist, album);
                        break;
                    case "setRepeatMode":
                        String mode = message.get("mode").toString();
                        handleSetRepeatMode(ctx.channel(),mode);
                        break;
                    case "setShuffle":
                        boolean enabled = (boolean) message.get("enabled");
                        handleSetShuffleMode(ctx.channel(),enabled);
                        break;
                    case "ping":
                        Log.i(TAG, "ping from "+ctx.channel().remoteAddress().toString());
                        break;
                    default:
                        System.err.println("Unknown command received: " + command);
                        break;
                }
            } else {
                String message = "unsupported frame type: " + frame.getClass().getName();
                throw new UnsupportedOperationException(message);
            }
        }

        private void handleSetShuffleMode(Channel channel, boolean enabled) {
            if(isPlaybackServiceBound) {
                BehaviorSubject<NowPlaying> subject = playbackService.getNowPlayingSubject();
                if (subject.getValue() != null) {
                    NowPlaying nowPlaying = (NowPlaying) subject.getValue();
                    nowPlaying.setShuffleMode(enabled);
                    subject.onNext(nowPlaying);
                }
            }
        }

        private void handleSetRepeatMode(Channel channel, String mode) {
            if(isPlaybackServiceBound) {
                BehaviorSubject<NowPlaying> subject = playbackService.getNowPlayingSubject();
                if (subject.getValue() != null) {
                    NowPlaying nowPlaying = (NowPlaying) subject.getValue();
                    nowPlaying.setRepeatMode(mode);
                    subject.onNext(nowPlaying);
                }
            }
        }

        private void handleGetTrackDetails(ChannelHandlerContext ctx, String id) {
           MusicTag tag = MusixMateApp.getInstance().getOrmLite().findById(Long.parseLong(id));
            if(tag != null) {
                Map<String, Object> track = new HashMap<>(Map.of(
                        "title", tag.getTitle(),
                        "artist", tag.getArtist(),
                        "album", tag.getAlbum(),
                        "duration", tag.getAudioDuration(),
                        "elapsed", 0,
                        "state", "playing",
                        "artUrl", "/coverart/"+tag.getAlbumUniqueKey()+".png",
                        "bitDepth", tag.getAudioBitsDepth(),
                        "sampleRate", tag.getAudioSampleRate()
                ));

                String mqaIndicator = tag.getMqaInd();
                boolean isMqaFile = (mqaIndicator != null && mqaIndicator.contains("MQA"));

                // Only add the 'isMQA' field if it is explicitly true.
                if (isMqaFile) {
                    track.put("isMQA", true);
                }

                Map<String, Object> response = Map.of("type", "trackDetailsResult", "track", track);
                ctx.channel().writeAndFlush(new TextWebSocketFrame(gson.toJson(response)));
            }
        }

        private void handleEmptyQueue() {
            try {
                Dao<QueueItem, Long> queueDao = MusixMateApp.getInstance().getOrmLite().getQueueItemDao();

                DeleteBuilder<QueueItem, Long> deleteBuilder = queueDao.deleteBuilder();

                deleteBuilder.delete(); // This efficiently deletes all rows from the queue_item table.
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
        }

        private void handlePlayFormContext(String id, String path) {
            if(!isPlaybackServiceBound) return;

            // update playing queue
            try {
                // --- 1. Get references to our Data Access Objects (DAOs) ---
                Dao<QueueItem, Long> queueDao = MusixMateApp.getInstance().getOrmLite().getQueueItemDao();

                List<MusicTag> songsInContext = new ArrayList<>();
                if (path.equalsIgnoreCase("Library/Songs")) {
                    // Contents of the "All Songs"
                    songsInContext = MusixMateApp.getInstance().getOrmLite().findMySongs();
                }else if(path.startsWith("Library/Genres/")) {
                    String name = path.substring("Library/Genres/".length());
                    songsInContext = MusixMateApp.getInstance().getOrmLite().findByGenre(name);
                }else if(path.startsWith("Library/Artists/")) {
                    String name = path.substring("Library/Artists/".length());
                    songsInContext = MusixMateApp.getInstance().getOrmLite().findByArtist(name,0,0);
                }else if(path.startsWith("Library/Groupings/")) {
                    String name = path.substring("Library/Groupings/".length());
                    songsInContext = MusixMateApp.getInstance().getOrmLite().findByGrouping(name,0,0);
                }else if(path.startsWith("Library/Playlists/")) {
                    String name = path.substring("Library/Playlists/".length());
                    List<MusicTag> songs = MusixMateApp.getInstance().getOrmLite().findMySongs();
                    songsInContext = songs.stream()
                            .filter(musicTag -> PlaylistRepository.isSongInPlaylistName(musicTag, name))
                            .collect(Collectors.toList());
                }

                if(!songsInContext.isEmpty()) {
                    // --- 2. Clear the entire existing playing queue ---
                    DeleteBuilder<QueueItem, Long> deleteBuilder = queueDao.deleteBuilder();
                    deleteBuilder.delete(); // This efficiently deletes all rows from the queue_item table.

                    // Populate the database with the new queue items.
                    int queueIndex = 1;
                    for (MusicTag tag : songsInContext) {
                        QueueItem newItem = new QueueItem(tag, queueIndex++);
                        queueDao.create(newItem);
                    }
                }
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }

            // play song
            MusicTag tag = MusixMateApp.getInstance().getOrmLite().findById(Long.parseLong(id));
          //  if(tag != null) {
                playbackService.play(tag);
           //     MusixMateApp.getInstance().getActivePlayer().play(tag);
               // PlaybackManager.playSong(MusixMateApp.getInstance(), tag);
           // }
        }

        private void handlePlayPrevious() {
           // MusixMateApp.getInstance().getActivePlayer().previous();
         //   PlaybackManager.playPreviousSong(getContext());
        }

        private void handlePlayNext() {
            if(isPlaybackServiceBound) {
                playbackService.next();
            }
            //MusixMateApp.getInstance().getActivePlayer().next();
           // PlaybackManager.playNextSong(getContext());
        }

        private void setRenderer(String udn) {
          /*  if(ExternalPlayer.SUPPORTED_PLAYERS.contains(udn)) {
               // Player player = PlayerInfo.buildLocalPlayer(getContext(), udn, udn);
                Player player = Player.Factory.create()
                MusixMateApp.getInstance().setActivePlayer(player);
            }else { */
               /* RemoteDevice remoteDevice =  MusixMateApp.getInstance().getRendererByUDN(udn);
                if(remoteDevice != null) {
                    Player player = Player.Factory.create(getContext(), remoteDevice);
                    MusixMateApp.getInstance().setActivePlayer(player);
                    //monitor renderer
                   // MediaServerService.getInstance().setupRender(udn);
                } */
         //   }
            if(isPlaybackServiceBound) {
                playbackService.setDlnaPlayer(udn);
            }

           // PlaybackService.setDlnaPlayer(udn);
        }

        private void handlePlay(String id) {
            if(isPlaybackServiceBound) {
                MusicTag tag = TagRepository.findById(Long.parseLong(id));
                playbackService.play(tag);
                // MusixMateApp.getInstance().getActivePlayer().play(tag);
                // PlaybackManager.playSong(MusixMateApp.getInstance(), tag);
            }
        }

        private void handleGetStats(ChannelHandlerContext ctx) {
             long artistCount = TagRepository.getArtistList().size();
             //long albumCount = TagRepository..getAlbumCount();
             long songCount = TagRepository.getMusicTotal();

            // 2. Create and send the response.
            Map<String, Object> stats = Map.of(
                    "artists", artistCount, // Replace with your actual data
                    "playlists", PlaylistRepository.getPlaylistNames().size(),
                    "genres", TagRepository.getActualGenreList().size(),
                    "songs", songCount
            );
            Map<String, Object> response = Map.of("type", "statsUpdate", "stats", stats);
            ctx.channel().writeAndFlush(new TextWebSocketFrame(gson.toJson(response)));
        }

        private void sendBrowseResult(ChannelHandlerContext ctx, String path) {
           // System.out.println("Browsing path: " + path);
            // In a real app, you would use the 'path' to query a database or filesystem.
            // Here, we'll return different mock data based on the path to simulate navigation.

            List<Map<String, ?>> items = Collections.emptyList();
            /*
             Library
             ├── Songs
             ├── Genres
             ├── Groupings
             └── Playlists
             */
            if (path.equalsIgnoreCase("Library/Songs")) {
                // Contents of the "All Songs"
                List<MusicTag> songs = TagRepository.getAllMusicsForPlaylist();
               // List<MusicTag> songs = MusixMateApp.getInstance().getOrmLite().findMySongs();
                // Convert the List<MusicTag> to a List<Map<String, ?>>
                items = songs.stream()
                        .map(WebSocketFrameHandler::getMap)
                        .collect(Collectors.toList());
            }else if(path.equalsIgnoreCase("Library/Genres")) {
                // Contents of the "Genres"
                List<String> genres = TagRepository.getActualGenreList();
                items = genres.stream()
                        .map(entry -> Map.of(
                                "type", "folder",
                                "name", entry,
                                "path", "Library/Genres/" + entry
                        ))
                        .collect(Collectors.toList());
            }else if(path.startsWith("Library/Genres/")) {
                String name = path.substring("Library/Genres/".length());
                List<MusicTag> songs = MusixMateApp.getInstance().getOrmLite().findByGenre(name);
                // Convert the List<MusicTag> to a List<Map<String, ?>>
                items = songs.stream()
                        .map(WebSocketFrameHandler::getMap)
                        .collect(Collectors.toList());
            }else if(path.equalsIgnoreCase("Library/Artists")) {
                // Contents of the "Genres"
                List<String> genres = TagRepository.getArtistList();
                items = genres.stream()
                        .map(entry -> Map.of(
                                "type", "folder",
                                "name", entry,
                                "path", "Library/Artists/" + entry
                        ))
                        .collect(Collectors.toList());
            }else if(path.startsWith("Library/Artists/")) {
                String name = path.substring("Library/Artists/".length());
                List<MusicTag> songs = MusixMateApp.getInstance().getOrmLite().findByArtist(name,0,0);
                items = songs.stream()
                        .map(WebSocketFrameHandler::getMap)
                        .collect(Collectors.toList());
            }else if(path.equalsIgnoreCase("Library/Playlists")) {
                List<PlaylistEntry> list = PlaylistRepository.getPlaylists();
                items = list.stream()
                        .sorted(Comparator.comparing(entry -> entry.getName()))
                        .map(entry -> Map.of(
                                "type", "folder",
                                "name", entry.getName(),
                                "path", "Library/Playlists/" + entry.getName()
                        ))
                        .collect(Collectors.toList());
            }else if(path.startsWith("Library/Playlists/")) {
                String name = path.substring("Library/Playlists/".length());
                List<MusicTag> songs = TagRepository.getAllMusicsForPlaylist();
                items = songs.stream()
                        .filter(musicTag -> PlaylistRepository.isSongInPlaylistName(musicTag, name))
                        .map(WebSocketFrameHandler::getMap)
                        .collect(Collectors.toList());
            } else { // Default top-level view
                items = List.of(
                        // Folders
                        Map.of("type", "folder", "name", "Songs", "path", "Library/Songs"),
                        Map.of("type", "folder", "name", "Artists", "path", "Library/Artists"),
                        Map.of("type", "folder", "name", "Genres", "path", "Library/Genres"),
                       // Map.of("type", "folder", "name", "Groupings", "path", "Library/Groupings"),
                        // A single song at the top level for demonstration
                        Map.of("type", "folder", "name", "Playlists", "path", "Library/Playlists")
                );
            }

            // Also return the current path in the response so the UI can update the breadcrumb
            Map<String, Object> response = Map.of("type", "browseResult", "items", items, "path", path);
            ctx.channel().writeAndFlush(new TextWebSocketFrame(gson.toJson(response)));
        }

        @NonNull
        private static Map<String, Object> getMap(MusicTag song) {
          /*  return Map.of(
                    "type", "song",
                    "id", song.getId(),
                    "name", song.getTitle(),
                    "title", song.getTitle(),
                    "artist", song.getArtist(),
                    "album", song.getAlbum(),
                    "duration", song.getAudioDuration(),
                    "bitDepth", song.getAudioBitsDepth(),
                    "sampleRate", song.getAudioSampleRate(),
                    "artUrl", "/coverart/" + song.getAlbumUniqueKey() + ".png"
            ); */

            Map<String, Object> track = new HashMap<>(Map.of(
                    "type", "song",
                    "id", song.getId(),
                    "title", song.getTitle(),
                    "artist", song.getArtist(),
                    "album", song.getAlbum(),
                    "duration", song.getAudioDuration(),
                    "elapsed", 0,
                    "state", "playing",
                    "artUrl", "/coverart/"+song.getAlbumUniqueKey()+".png"
            ));

            //if(!MusicTagUtils.isLossy(song)) {
                track.put("bitDepth", song.getAudioBitsDepth());
                track.put("sampleRate", song.getAudioSampleRate());
            //}

            String qualityIndicator = MusicTagUtils.getQualityIndicator(song);
            if(!isEmpty(qualityIndicator)) {
                track.put("quality", qualityIndicator);
            }

           /* String mqaIndicator = song.getMqaInd();
            boolean isMqaFile = (mqaIndicator != null && mqaIndicator.contains("MQA"));

            // Only add the 'isMQA' field if it is explicitly true.
            if (isMqaFile) {
                track.put("isMQA", true);
            } */

            return track;
        }

        private void sendNowPlaying(ChannelHandlerContext ctx) {
            if(isPlaybackServiceBound) {
                NowPlaying nowPlaying = playbackService.getNowPlayingSubject().getValue();
                if (nowPlaying != null && nowPlaying.getSong() != null) {
                    MusicTag tag = nowPlaying.getSong();
                    Map<String, Object> track = getMap(tag);

                    Map<String, Object> response = Map.of("type", "nowPlaying", "track", track);
                    ctx.channel().writeAndFlush(new TextWebSocketFrame(gson.toJson(response)));
                }
            }
        }

        private void broadcastNowPlaying(NowPlaying nowPlaying) {
            long elapsed = nowPlaying.getElapsed();
            MusicTag tag = nowPlaying.getSong();
            if(tag != null) {
                Map<String, Object> track = getMap(tag);
                track.put("elapsed", elapsed);
                track.put("state", nowPlaying.getPlayingState());

              /*  float[] dynamicWaveform = new float[]{
                        0.02f, 0.04f, 0.05f, 0.07f, 0.09f, 0.11f, 0.13f, 0.16f, 0.19f, 0.22f,
                        0.26f, 0.30f, 0.35f, 0.40f, 0.45f, 0.50f, 0.56f, 0.62f, 0.68f, 0.74f,
                        0.80f, 0.85f, 0.90f, 0.94f, 0.97f, 0.99f, 1.00f, 0.98f, 0.95f, 0.91f,
                        0.86f, 0.81f, 0.75f, 0.69f, 0.63f, 0.57f, 0.51f, 0.46f, 0.41f, 0.37f,
                        0.33f, 0.30f, 0.27f, 0.25f, 0.23f, 0.21f, 0.20f, 0.19f, 0.18f, 0.17f,
                        0.16f, 0.15f, 0.14f, 0.13f, 0.12f, 0.11f, 0.10f, 0.10f, 0.11f, 0.12f,
                        0.13f, 0.15f, 0.17f, 0.19f, 0.22f, 0.25f, 0.29f, 0.33f, 0.37f, 0.41f,
                        0.45f, 0.49f, 0.53f, 0.57f, 0.61f, 0.65f, 0.69f, 0.73f, 0.77f, 0.81f,
                        0.85f, 0.89f, 0.92f, 0.95f, 0.97f, 0.99f, 1.00f, 1.00f, 0.99f, 0.97f,
                        0.95f, 0.92f, 0.89f, 0.86f, 0.83f, 0.80f, 0.77f, 0.74f, 0.71f, 0.68f,
                        0.65f, 0.62f, 0.59f, 0.56f, 0.53f, 0.50f, 0.47f, 0.44f, 0.41f, 0.38f,
                        0.35f, 0.32f, 0.30f, 0.28f, 0.26f, 0.24f, 0.22f, 0.20f, 0.18f, 0.17f,
                        0.16f, 0.15f, 0.14f, 0.13f, 0.12f, 0.11f, 0.10f, 0.09f, 0.08f, 0.07f,
                        0.06f, 0.06f, 0.05f, 0.05f, 0.04f, 0.04f, 0.04f, 0.03f, 0.03f, 0.03f,
                        0.02f, 0.02f, 0.02f, 0.02f, 0.02f, 0.02f, 0.02f, 0.02f, 0.02f, 0.02f
                };
               */

                /*
                float[] dynamicWaveform = new float[]{
                        // Intro: A gentle, subtle build-up
                        0.05f, 0.08f, 0.12f, 0.15f, 0.18f, 0.22f, 0.25f, 0.28f, 0.32f, 0.35f,
                        0.37f, 0.40f, 0.43f, 0.46f, 0.49f, 0.52f, 0.55f, 0.58f, 0.61f, 0.64f,
                        0.67f, 0.70f, 0.73f, 0.76f, 0.79f, 0.81f, 0.83f, 0.85f, 0.87f, 0.88f,
                        0.89f, 0.90f, 0.91f, 0.92f, 0.93f, 0.94f, 0.95f, 0.96f, 0.97f, 0.98f,
                        0.99f, 1.00f,

                        // Chorus: The main event with high peaks and sharp drops
                        0.98f, 0.95f, 0.89f, 0.80f, 0.70f, 0.55f, 0.40f, 0.25f, 0.15f, 0.08f,
                        0.05f, 0.10f, 0.20f, 0.35f, 0.50f, 0.65f, 0.78f, 0.90f, 0.98f, 1.00f,
                        0.95f, 0.88f, 0.75f, 0.60f, 0.45f, 0.30f, 0.18f, 0.10f, 0.06f, 0.04f,
                        0.08f, 0.15f, 0.25f, 0.40f, 0.55f, 0.70f, 0.85f, 0.95f, 1.00f, 0.98f,
                        0.90f, 0.80f, 0.65f, 0.50f, 0.35f, 0.20f, 0.10f, 0.05f, 0.02f, 0.01f,

                        // Outro: A calm and gradual fade-out
                        0.03f, 0.05f, 0.08f, 0.11f, 0.14f, 0.17f, 0.20f, 0.18f, 0.15f, 0.12f,
                        0.09f, 0.06f, 0.04f, 0.02f, 0.01f, 0.005f
                }; */

                float[] relaxingWaveform = new float[]{
                        // Intro: A very slow, gentle rise
                        0.05f, 0.06f, 0.07f, 0.08f, 0.09f, 0.10f, 0.11f, 0.12f, 0.13f, 0.14f,
                        0.15f, 0.16f, 0.17f, 0.18f, 0.19f, 0.20f, 0.21f, 0.22f, 0.23f, 0.24f,
                        0.25f, 0.26f, 0.27f, 0.28f, 0.29f, 0.30f, 0.31f, 0.32f, 0.33f, 0.34f,
                        0.35f, 0.36f, 0.37f, 0.38f, 0.39f, 0.40f,

                        // Main section: Gentle, rolling peaks and valleys
                        0.41f, 0.45f, 0.48f, 0.50f, 0.51f, 0.52f, 0.51f, 0.50f, 0.48f, 0.45f,
                        0.42f, 0.40f, 0.38f, 0.35f, 0.32f, 0.30f, 0.32f, 0.35f, 0.38f, 0.40f,
                        0.42f, 0.45f, 0.48f, 0.50f, 0.52f, 0.53f, 0.52f, 0.50f, 0.48f, 0.45f,
                        0.42f, 0.40f, 0.38f, 0.35f, 0.32f, 0.30f, 0.28f, 0.25f, 0.23f, 0.20f,

                        // Outro: A gentle, clean fade-out
                        0.18f, 0.16f, 0.14f, 0.12f, 0.10f, 0.08f, 0.06f, 0.04f, 0.02f, 0.01f,
                        0.005f, 0.002f, 0.001f, 0.0005f
                };

                float[] happyRelaxingWaveform = new float[]{
                        // Intro: A very slow, gentle rise with a subtle ripple
                        0.05f, 0.07f, 0.09f, 0.12f, 0.15f, 0.17f, 0.20f, 0.22f, 0.25f, 0.27f,
                        0.28f, 0.29f, 0.30f, 0.31f, 0.32f, 0.33f, 0.34f, 0.35f, 0.36f, 0.37f,
                        0.38f, 0.39f, 0.40f, 0.41f, 0.42f, 0.43f, 0.44f, 0.45f, 0.46f, 0.47f,

                        // Main section: Gentle, rolling hills with varied heights
                        0.48f, 0.50f, 0.52f, 0.55f, 0.58f, 0.60f, 0.59f, 0.57f, 0.55f, 0.52f,
                        0.50f, 0.47f, 0.45f, 0.43f, 0.41f, 0.40f, 0.42f, 0.45f, 0.48f, 0.52f,
                        0.55f, 0.58f, 0.60f, 0.62f, 0.61f, 0.59f, 0.57f, 0.55f, 0.52f, 0.50f,
                        0.47f, 0.45f, 0.42f, 0.40f, 0.38f, 0.35f, 0.32f, 0.30f, 0.28f, 0.25f,

                        // Outro: A gentle and clean fade-out
                        0.23f, 0.20f, 0.17f, 0.15f, 0.12f, 0.10f, 0.08f, 0.06f, 0.04f, 0.02f,
                        0.01f, 0.005f
                };

                float[] smoothHappyWaveform = new float[]{
                        // Intro: A very gradual, clean buildup without any sharp points
                        0.05f, 0.08f, 0.12f, 0.16f, 0.20f, 0.24f, 0.28f, 0.32f, 0.35f, 0.38f,
                        0.40f, 0.42f, 0.44f, 0.46f, 0.48f, 0.50f, 0.52f, 0.54f, 0.56f, 0.58f,
                        0.60f, 0.62f, 0.64f, 0.65f, 0.66f, 0.67f, 0.68f, 0.69f, 0.70f, 0.71f,

                        // Main section: Gentle, rolling peaks and valleys, like ocean waves
                        0.72f, 0.75f, 0.78f, 0.80f, 0.82f, 0.83f, 0.82f, 0.80f, 0.78f, 0.75f,
                        0.72f, 0.68f, 0.65f, 0.62f, 0.60f, 0.58f, 0.55f, 0.52f, 0.50f, 0.52f,
                        0.55f, 0.58f, 0.62f, 0.65f, 0.68f, 0.72f, 0.75f, 0.78f, 0.80f, 0.82f,
                        0.83f, 0.82f, 0.80f, 0.78f, 0.75f, 0.72f, 0.68f, 0.65f, 0.60f, 0.55f,
                        0.50f, 0.45f, 0.40f, 0.35f, 0.30f, 0.25f, 0.20f,

                        // Outro: A clean, smooth fade-out
                        0.18f, 0.15f, 0.12f, 0.10f, 0.08f, 0.06f, 0.04f, 0.02f, 0.01f, 0.005f
                };

                float[] cleanBlockWaveform = new float[]{
                        // Intro: A short, smooth ramp up to the main section
                        0.02f, 0.01f, 0.02f, 0.05f,0.18f,0.05f, 0.02f, 0.04f, 0.15f, 0.25f, 0.35f, 0.45f, 0.55f, 0.65f, 0.75f, 0.85f, 0.90f,
                        0.95f, 0.98f, 1.00f,

                        // Main Section: A consistent, stable "block" of sound
                        1.00f, 0.98f, 1.00f, 0.97f, 0.99f, 0.98f, 1.00f, 0.99f, 0.98f, 1.00f,
                        0.98f, 0.99f, 0.97f, 1.00f, 0.98f, 0.99f, 0.97f, 1.00f, 0.99f, 0.98f,
                        1.00f, 0.98f, 0.97f, 0.99f, 1.00f, 0.99f, 0.98f, 1.00f, 0.99f, 0.98f,

                        // Outro: A smooth ramp down to silence
                        0.95f, 0.90f, 0.85f, 0.75f, 0.65f, 0.55f, 0.45f, 0.35f, 0.25f, 0.15f,
                        0.05f, 0.02f, 0.01f
                };
                track.put("waveform", cleanBlockWaveform);

                Map<String, Object> response = Map.of("type", "nowPlaying", "track", track);
                channels.writeAndFlush(new TextWebSocketFrame(gson.toJson(response)));
            }
        }

        private void sendDlnaRenderers(ChannelHandlerContext ctx) {
            if(!isPlaybackServiceBound) return;

            String activeRendererUdn;
            if(playbackService.getActivePlayer() != null) {
                activeRendererUdn = playbackService.getActivePlayer().getId();
            } else {
                activeRendererUdn = null;
            }
            List<Map<String, ?>> renderers = new ArrayList<>();
            // In a real app, this would come from your DLNA discovery service
            List<RemoteDevice> rendererList = playbackService.getRenderers();
            if(!rendererList.isEmpty()) {
                // to send a list of renderers
                renderers = rendererList.stream()
                        .map(device -> Map.of(
                                "name", device.getDetails().getFriendlyName(), // Assuming methods to get details
                                "udn", device.getIdentity().getUdn().getIdentifierString(),
                                "active", device.getIdentity().getUdn().getIdentifierString().equals(activeRendererUdn)
                        ))
                        .collect(Collectors.toList());
            }

            Map<String, Object> response = Map.of("type", "dlnaRenderers", "renderers", renderers);
            ctx.channel().writeAndFlush(new TextWebSocketFrame(gson.toJson(response)));
        }

        private void handleAddToQueue(String songId) {
            if (songId == null || songId.isBlank()) return;

            // Find the song by ID and add it to the queue
            MusicTag song = MusixMateApp.getInstance().getOrmLite().findById(Long.parseLong(songId));
            if(song != null) {
                try {
                    Dao<QueueItem, Long> queueDao = MusixMateApp.getInstance().getOrmLite().getQueueItemDao();
                    // Get the current size of the queue to determine the next position.
                    // If the queue has 5 items (positions 0-4), countOf() returns 5, which is the correct next position.
                    long nextPosition = queueDao.countOf();
                    QueueItem item = new QueueItem(song, nextPosition);
                    queueDao.create(item);
                    broadcastQueueUpdate();
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }
        }

        private void handleGetTrackInfo(Channel channel, String artist, String album) {
            new Thread(() -> {
                TrackInfo info = null;
                try {
                    info = musicInfoService.getFullTrackInfo(artist, album);
                } catch (Exception ex) {
                    ex.printStackTrace();
                }
                // Now you have the info, create your response payload
                Map<String, Object> responsePayload = Map.of(
                        "type", "trackInfoResult",
                        "info", info != null ? info : Map.of() // Send info or an empty map
                );

                // Send the result back to the client
                channel.writeAndFlush(new TextWebSocketFrame(new Gson().toJson(responsePayload)));
            }).start();
        }

        private void sendQueueUpdate(ChannelHandlerContext ctx) {
            // Sends the current queue state to a single client (e.g., on initial connect)
            try {
                List<QueueItem> playingQueue = MusixMateApp.getInstance().getOrmLite().getQueueItemDao().queryForAll();
                List<Map<String, ?>> queueAsMaps = playingQueue.stream()
                        .filter(song -> song.getTrack() != null)
                        .map(song -> getMap(song.getTrack()))
                        .collect(Collectors.toList());
                Map<String, Object> response = Map.of("type", "updateQueue", "queue", queueAsMaps);
                ctx.channel().writeAndFlush(new TextWebSocketFrame(gson.toJson(response)));
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
        }

        private void broadcastQueueUpdate() {
            // Sends the current queue state to ALL connected clients
            try {
                List<QueueItem> playingQueue = MusixMateApp.getInstance().getOrmLite().getQueueItemDao().queryForAll();

                List<Map<String, ?>> queueAsMaps = playingQueue.stream()
                        .map(song -> getMap(song.getTrack()))
                        .collect(Collectors.toList());
                Map<String, Object> response = Map.of("type", "updateQueue", "queue", queueAsMaps);
                channels.writeAndFlush(new TextWebSocketFrame(gson.toJson(response)));
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
        }
    }
}
