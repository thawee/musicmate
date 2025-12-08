package apincer.android.jupnp.transport.netty;

import static apincer.music.core.server.ProfileManager.calculateBufferSize;
import static io.netty.handler.codec.http.HttpResponseStatus.*;
import static io.netty.handler.codec.http.HttpVersion.HTTP_1_1;

import android.content.Context;
import android.util.Log;

import com.google.gson.Gson;

import java.io.File;
import java.io.RandomAccessFile;
import java.net.InetAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ThreadFactory;

import apincer.music.core.database.MusicTag;
import apincer.music.core.repository.FileRepository;
import apincer.music.core.repository.TagRepository;
import apincer.music.core.server.BaseServer;
import apincer.music.core.server.ProfileManager;
import apincer.music.core.server.model.ClientProfile;
import apincer.music.core.server.spi.WebServer;
import apincer.music.core.utils.MimeTypeUtils;
import apincer.music.core.utils.StringUtils;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandler;
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
import io.netty.handler.codec.http.*;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import io.netty.handler.codec.http.websocketx.WebSocketFrame;
import io.netty.handler.codec.http.websocketx.WebSocketServerProtocolHandler;
import io.netty.handler.stream.ChunkedFile;
import io.netty.handler.stream.ChunkedWriteHandler;
import io.netty.util.CharsetUtil;
import io.netty.util.concurrent.DefaultEventExecutorGroup;
import io.netty.util.concurrent.DefaultThreadFactory;
import io.netty.util.concurrent.EventExecutorGroup;
import io.netty.util.concurrent.GlobalEventExecutor;

public class NettyWebServerImpl extends BaseServer implements WebServer {
    private static final String TAG = "NettyWebServer";
    private final Context context;

    // Buffer settings
    private final int HIGH_WATERMARK = 262144;
    private final int LOW_WATERMARK = 131072;

    private final TagRepository repos;
    private final int serverPort;

    // Netty Groups
    private EventLoopGroup bossGroup;
    private EventLoopGroup workerGroup;

    // CRITICAL: Separate thread pool for DB access and File checks to prevent blocking I/O threads
    private EventExecutorGroup logicExecutorGroup;

    private Channel serverChannel;
    private Thread serverThread;
    private volatile boolean isRunning = false;
    private final WebSocketFrameHandler wsHandler;

    private final ProfileManager profileManager;

    public NettyWebServerImpl(Context context, FileRepository fileRepos, TagRepository tagRepos) {
        super(context, fileRepos, tagRepos);
        this.context = context;
        this.serverPort = WEB_SERVER_PORT;
        this.repos = tagRepos;

        addLibInfo("Netty", "4.2.6");

        // Calculate buffer size based on RAM once
        int bufferSize = calculateBufferSize(context);

        // Initialize the shared manager
        this.profileManager = new ProfileManager(bufferSize);

        // WebSocket handler is stateless regarding the pipeline, so we create it once
        wsHandler = new WebSocketFrameHandler();
    }

    @Override
    public void initServer(InetAddress bindAddress) throws Exception {
        if (isRunning) return;

        ThreadFactory bossFactory = new DefaultThreadFactory("netty-boss");
        ThreadFactory workerFactory = new DefaultThreadFactory("netty-io");
        ThreadFactory logicFactory = new DefaultThreadFactory("netty-logic"); // Thread pool for DB/Disk logic

        this.bossGroup = new MultiThreadIoEventLoopGroup(1, bossFactory, NioIoHandler.newFactory());
        // Limit IO threads on Android to save battery/resources
        this.workerGroup = new MultiThreadIoEventLoopGroup(2, workerFactory, NioIoHandler.newFactory());

        // This group handles the "business logic" (DB queries) so IO threads don't block
        this.logicExecutorGroup = new DefaultEventExecutorGroup(4, logicFactory);

        serverThread = new Thread(() -> {
            try {
                ServerBootstrap b = new ServerBootstrap();
                b.group(bossGroup, workerGroup)
                        .channel(NioServerSocketChannel.class)
                        .option(ChannelOption.SO_BACKLOG, 128)
                        .option(ChannelOption.SO_REUSEADDR, true)
                        .option(ChannelOption.TCP_NODELAY, true)
                        .option(ChannelOption.SO_KEEPALIVE, true)
                        .childOption(ChannelOption.WRITE_BUFFER_WATER_MARK,
                                new WriteBufferWaterMark(LOW_WATERMARK, HIGH_WATERMARK));

                b.childHandler(new ContentServerInitializer());

                ChannelFuture f = b.bind(bindAddress.getHostAddress(), serverPort).sync();
                serverChannel = f.channel();
                isRunning = true;
                Log.i(TAG, "Netty WebServer started on " + bindAddress.getHostAddress() + ":" + serverPort);

                serverChannel.closeFuture().sync();
            } catch (Exception ex) {
                Log.e(TAG, "WebServer failed start", ex);
                isRunning = false;
            } finally {
                stopServer();
            }
        }, "NettyServer");

        serverThread.start();
    }

    @Override
    public void stopServer() {
        isRunning = false;
        try {
            if (serverChannel != null) serverChannel.close();
            if (bossGroup != null) bossGroup.shutdownGracefully();
            if (workerGroup != null) workerGroup.shutdownGracefully();
            if (logicExecutorGroup != null) logicExecutorGroup.shutdownGracefully();
        } catch (Exception e) {
            Log.e(TAG, "Error stopping server", e);
        }
    }

    @Override
    public String getComponentName() { return "WebServer"; }

    @Override
    public int getListenPort() { return serverPort; }

    public class ContentServerInitializer extends ChannelInitializer<SocketChannel> {
        @Override
        protected void initChannel(SocketChannel ch) {
            ChannelPipeline pipeline = ch.pipeline();
            pipeline.addLast(new HttpServerCodec());
            pipeline.addLast(new HttpObjectAggregator(65536));
            pipeline.addLast(new ChunkedWriteHandler()); // Handles Async File IO writing

            pipeline.addLast(new WebSocketServerProtocolHandler(CONTEXT_PATH_WEBSOCKET, null, true));
            // WebSocket logic is usually fast (JSON parsing), can stay on IO thread or move to logic group
            pipeline.addLast(wsHandler);

            // CRITICAL: We pass 'logicExecutorGroup' here.
            // This ensures channelRead0 in WebContentHandler runs on a background thread, NOT the IO thread.
            pipeline.addLast(logicExecutorGroup, new WebContentHandler());
        }
    }

    // [ContentHolder class remains the same]
    static class ContentHolder {
        private final String contentType;
        private final String filePath;
        private final String fileName;
        private final MusicTag musicTag;

        public ContentHolder(String contentType, String filePath, MusicTag tag) {
            this.filePath = filePath;
            this.contentType = contentType;
            this.musicTag = tag;
            this.fileName = new File(filePath).getName();
        }
        public boolean exists() { return filePath != null && new File(filePath).exists(); }
        public MusicTag getMusicTag() { return musicTag; }
    }

    private class WebContentHandler extends SimpleChannelInboundHandler<FullHttpRequest> {

        @Override
        protected void channelRead0(ChannelHandlerContext ctx, FullHttpRequest request) {
            if (!request.method().equals(HttpMethod.GET) && !request.method().equals(HttpMethod.HEAD)) {
                sendError(ctx, METHOD_NOT_ALLOWED);
                return;
            }

            String userAgent = request.headers().get(HttpHeaderNames.USER_AGENT);

            // ONE LINE DETECTION
            ClientProfile profile = profileManager.detect(userAgent);

            // Heavy Lifting: DB Lookup & File Access (Safe here because we are in logicExecutorGroup)
            ContentHolder holder = getContent(ctx, request);

            if (holder == null || !holder.exists()) {
                sendError(ctx, NOT_FOUND);
                return;
            }

            boolean keepAlive = HttpUtil.isKeepAlive(request) && profile.keepAlive;
            serveContent(ctx, request, holder, keepAlive, profile);
        }

        private void serveContent(ChannelHandlerContext ctx, HttpRequest request,
                                  ContentHolder holder, boolean keepAlive, ClientProfile profile) {
            RandomAccessFile raf = null;
            try {
                File file = new File(holder.filePath);
                raf = new RandomAccessFile(file, "r");
                long fileLength = raf.length();

                long start = 0;
                long length = fileLength;

                // Range Handling
                String rangeHeader = request.headers().get(HttpHeaderNames.RANGE);
                HttpResponseStatus status = OK;

                if (rangeHeader != null) {
                    // Robust Range Parsing
                    try {
                        String rangeValue = rangeHeader.trim().substring("bytes=".length());
                        int dashIdx = rangeValue.indexOf('-');
                        start = Long.parseLong(rangeValue.substring(0, dashIdx));
                        if (dashIdx < rangeValue.length() - 1) {
                            length = Long.parseLong(rangeValue.substring(dashIdx + 1)) - start + 1;
                        } else {
                            length = fileLength - start;
                        }
                        status = PARTIAL_CONTENT;
                    } catch (Exception e) {
                        // If parsing fails, ignore range and send full file (fallback)
                        Log.w(TAG, "Invalid range header: " + rangeHeader);
                        start = 0;
                        length = fileLength;
                    }
                }

                HttpResponse response = new DefaultHttpResponse(HTTP_1_1, status);
                HttpUtil.setContentLength(response, length);
                setContentTypeHeader(response, holder.contentType);
                setDateAndCacheHeaders(response, holder.fileName);
                setAudioHeaders(response, holder, profile);

                if (status == PARTIAL_CONTENT) {
                    response.headers().set(HttpHeaderNames.CONTENT_RANGE, "bytes " + start + "-" + (start + length - 1) + "/" + fileLength);
                }
                if (keepAlive) {
                    response.headers().set(HttpHeaderNames.CONNECTION, HttpHeaderValues.KEEP_ALIVE);
                }

                ctx.write(response);

                // ChunkedFile takes ownership of RAF and closes it when done
                ctx.write(new HttpChunkedInput(new ChunkedFile(raf, start, length, profile.chunkSize)), ctx.newProgressivePromise());

                ChannelFuture lastContentFuture = ctx.writeAndFlush(LastHttpContent.EMPTY_LAST_CONTENT);
                if (!keepAlive) {
                    lastContentFuture.addListener(ChannelFutureListener.CLOSE);
                }

            } catch (Exception e) {
                Log.e(TAG, "Failed to serve file", e);
                // Ensure RAF is closed if we crash before ChunkedFile takes it
                if (raf != null) {
                    try { raf.close(); } catch (Exception ignored) {}
                }
                sendError(ctx, INTERNAL_SERVER_ERROR);
            }
        }

        // [Helper methods: setContentTypeHeader, setDateAndCacheHeaders, setAudioHeaders, getDLNAContentFeatures remain same]
        private void setContentTypeHeader(HttpResponse response, String contentType) {
            response.headers().set(HttpHeaderNames.CONTENT_TYPE, contentType);
        }
        private void setDateAndCacheHeaders(HttpResponse response, String fileName) {
            response.headers().set(HttpHeaderNames.SERVER, getServerSignature(getComponentName()));
            response.headers().set(HttpHeaderNames.DATE, formatDate(System.currentTimeMillis()));
            response.headers().set(HttpHeaderNames.CONTENT_DISPOSITION, "inline; filename=\"" + fileName + "\"");
            response.headers().set(HttpHeaderNames.CACHE_CONTROL, "private, max-age=30");
        }
        private void setAudioHeaders(HttpResponse response, ContentHolder holder, ClientProfile profile) {
            // [Keep your detailed Audiophile/DLNA header logic]
            response.headers().set(HttpHeaderNames.ACCEPT_RANGES, "bytes");
            if (holder.getMusicTag() != null) {
                MusicTag tag = holder.getMusicTag();
                response.headers().set("TransferMode.DLNA.ORG", "Streaming");
                // ... [Rest of your logic]
            }
        }

        private void sendError(ChannelHandlerContext ctx, HttpResponseStatus status) {
            FullHttpResponse response = new DefaultFullHttpResponse(HTTP_1_1, status,
                    Unpooled.copiedBuffer("Error: " + status + "\r\n", CharsetUtil.UTF_8));
            response.headers().set(HttpHeaderNames.CONTENT_TYPE, "text/plain; charset=UTF-8");
            ctx.writeAndFlush(response).addListener(ChannelFutureListener.CLOSE);
        }

        private ContentHolder getContent(ChannelHandlerContext ctx, HttpRequest request) {
            // FIX: Decode the URI (handling %20, etc.)
            String requestUri = request.uri();
            requestUri = URLDecoder.decode(requestUri, StandardCharsets.UTF_8);

            // Remove Query params
            if (requestUri.contains("?")) {
                requestUri = requestUri.substring(0, requestUri.indexOf("?"));
            }

            if (requestUri.equals("/") || requestUri.isEmpty()) requestUri = "/index.html";
            if (requestUri.contains("../")) return null; // Security check

            if (requestUri.startsWith(CONTEXT_PATH_COVERART)) {
                return getAlbumArt(request, requestUri);
            } else if (requestUri.startsWith(CONTEXT_PATH_MUSIC)) {
                return getSong(ctx, request, requestUri);
            } else {
                return getFile(ctx, request, requestUri);
            }
        }

        private ContentHolder getSong(ChannelHandlerContext ctx, HttpRequest request, String requestUri) {
            // [Keep your existing logic for ID parsing]
            // ...
            try {
                String contentId = requestUri.substring(requestUri.lastIndexOf('/') + 1);
                long id = StringUtils.toLong(contentId);
                MusicTag tag = repos.findById(id); // This is safe now (running in logic group)

                if (tag == null) return null;

                // NOTIFICATION: Still safest to wrap this or assume getPlaybackService handles threading
                if (getPlaybackService() != null) {
                    getPlaybackService().onMediaTrackChanged(tag);
                }

                String mimeType = MimeTypeUtils.getMimeTypeFromPath(tag.getPath());
                return new ContentHolder(mimeType, tag.getPath(), tag);
            } catch (Exception e) { return null; }
        }

        private ContentHolder getAlbumArt(HttpRequest request, String uri) {
            // [Keep your logic]
            return null; // Placeholder for brevity
        }

        private ContentHolder getFile(ChannelHandlerContext ctx, HttpRequest request, String path) {
            // [Keep your logic]
            return null; // Placeholder for brevity
        }
    }

    // Must be Sharable to be reused or used in ChannelGroup correctly
    @ChannelHandler.Sharable
    private class WebSocketFrameHandler extends SimpleChannelInboundHandler<WebSocketFrame> {
        private final ChannelGroup channels = new DefaultChannelGroup(GlobalEventExecutor.INSTANCE);
        //private final Gson gson = new Gson();
        // [Your existing WebSocketContent logic]

        @Override
        public void handlerAdded(ChannelHandlerContext ctx) { channels.add(ctx.channel()); }

        @Override
        public void handlerRemoved(ChannelHandlerContext ctx) { channels.remove(ctx.channel()); }

        @Override
        protected void channelRead0(ChannelHandlerContext ctx, WebSocketFrame frame) {
            if (frame instanceof TextWebSocketFrame) {
                // Logic
                String text = ((TextWebSocketFrame) frame).text();
                // Process command...
            }
        }
    }
}