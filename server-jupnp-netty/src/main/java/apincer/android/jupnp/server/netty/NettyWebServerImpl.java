package apincer.android.jupnp.server.netty;

import static io.netty.handler.codec.http.HttpResponseStatus.*;
import static io.netty.handler.codec.http.HttpVersion.HTTP_1_1;

import android.content.Context;
import android.util.Log;

import java.io.File;
import java.io.RandomAccessFile;
import java.net.InetAddress;

import apincer.music.core.model.Track;
import apincer.music.core.repository.FileRepository;
import apincer.music.core.repository.TagRepository;
import apincer.music.core.server.BaseServer;
import apincer.music.core.server.ContentHolder;
import apincer.music.core.server.spi.WebServer;
import apincer.music.core.utils.MimeTypeUtils;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.Unpooled;
import io.netty.channel.*;
import io.netty.channel.group.ChannelGroup;
import io.netty.channel.group.DefaultChannelGroup;
import io.netty.channel.nio.NioIoHandler;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.channel.MultiThreadIoEventLoopGroup;
import io.netty.handler.codec.http.*;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import io.netty.handler.codec.http.websocketx.WebSocketServerProtocolHandler;
import io.netty.handler.stream.*;
import io.netty.util.CharsetUtil;
import io.netty.util.Version;
import io.netty.util.concurrent.DefaultEventExecutorGroup;
import io.netty.util.concurrent.EventExecutorGroup;
import io.netty.util.concurrent.GlobalEventExecutor;

public class NettyWebServerImpl extends BaseServer implements WebServer {

    private static final String TAG = "NettyWebServer";

    private EventLoopGroup bossGroup;
    private EventLoopGroup workerGroup;
    private EventExecutorGroup logicExecutorGroup;

    private Channel serverChannel;
    private volatile boolean isRunning = false;

    private final WebSocketFrameHandler wsHandler = new WebSocketFrameHandler();

    public NettyWebServerImpl(Context context, FileRepository fileRepos, TagRepository tagRepos) {
        super(context, fileRepos, tagRepos);
        addLibInfo("Netty", getVersion());
       // serverSignature = getServerSignature();
    }

    private String getVersion() {
        return Version.identify().get("netty-common").artifactVersion();
    }

    @Override
    public void restartServer(InetAddress bindAddress) throws Exception {
        if(isRunning) {
            stopServer();
        }
        initServer(bindAddress);
    }

    // =========================
    // SERVER LIFECYCLE
    // =========================
    @Override
    public void initServer(InetAddress bindAddress) throws Exception {
        if (isRunning) return;

        bossGroup = new MultiThreadIoEventLoopGroup(
                1,
                NioIoHandler.newFactory()
        );

        workerGroup = new MultiThreadIoEventLoopGroup(
                2,
                NioIoHandler.newFactory()
        );
        logicExecutorGroup = new DefaultEventExecutorGroup(4);

        ServerBootstrap b = new ServerBootstrap();
        b.group(bossGroup, workerGroup)
                .channel(NioServerSocketChannel.class)
                .option(ChannelOption.SO_BACKLOG, 128)
                .option(ChannelOption.SO_REUSEADDR, true)

                .childOption(ChannelOption.TCP_NODELAY, true)     // 🔥 important for streaming
                .childOption(ChannelOption.SO_KEEPALIVE, true)    // 🔥 DLNA stability
                .childOption(ChannelOption.IP_TOS, 0x18)          // 🔥 priority tagging
                .childOption(ChannelOption.WRITE_BUFFER_WATER_MARK,
                        new WriteBufferWaterMark(262144, 524288))  // 256KB low, 512KB high watermark
                .childHandler(new ContentServerInitializer());

        serverChannel = b.bind(bindAddress, getListenPort()).sync().channel();
        isRunning = true;

        Log.i(TAG, "Server started on " + bindAddress + ":" + getListenPort());
    }

    @Override
    public void stopServer() {
        try {
            if (serverChannel != null) serverChannel.close();
            if (bossGroup != null) bossGroup.shutdownGracefully();
            if (workerGroup != null) workerGroup.shutdownGracefully();
            if (logicExecutorGroup != null) logicExecutorGroup.shutdownGracefully();
        } catch (Exception e) {
            Log.e(TAG, "Shutdown error", e);
        } finally {
            isRunning = false;
        }
    }

    @Override
    public int getListenPort() {
        return WEB_SERVER_PORT;
    }

    // =========================
    // PIPELINE
    // =========================
    private class ContentServerInitializer extends ChannelInitializer<SocketChannel> {
        @Override
        protected void initChannel(SocketChannel ch) {
            ChannelPipeline p = ch.pipeline();

            p.addLast(new HttpServerCodec());
            p.addLast(new HttpObjectAggregator(65536));
            p.addLast(new ChunkedWriteHandler());

            // WebSocket upgrade endpoint
            p.addLast(new WebSocketServerProtocolHandler(
                    CONTEXT_PATH_WEBSOCKET,   // "/ws"
                    null,
                    true
            ));

            // WebSocket handler (OFF IO thread to prevent DLNA stutter)
            p.addLast(logicExecutorGroup, wsHandler.createInboundHandler());

            // HTTP content handler (OFF IO thread)
            ChannelHandler httpHandler = new WebContentHandler();
            p.addLast(logicExecutorGroup, httpHandler);
        }
    }

    // =========================
    // RANGE SUPPORT (DLNA SAFE)
    // =========================
    static class Range {
        long start;
        long end;
        boolean partial;

        Range(long s, long e, boolean p) {
            start = s;
            end = e;
            partial = p;
        }
    }

    private Range parseRange(String header, long fileLength) {
        if (header == null || !header.startsWith("bytes=")) {
            return new Range(0, fileLength - 1, false);
        }

        try {
            String value = header.substring(6).trim();

            if (value.contains(",")) {
                value = value.split(",")[0];
            }

            long start = 0;
            long end = fileLength - 1;

            if (value.startsWith("-")) {
                long suffix = Long.parseLong(value.substring(1));
                start = Math.max(fileLength - suffix, 0);
            } else if (value.endsWith("-")) {
                start = Long.parseLong(value.substring(0, value.length() - 1));
            } else {
                String[] parts = value.split("-");
                start = Long.parseLong(parts[0]);
                end = Long.parseLong(parts[1]);
            }

            if (end >= fileLength) end = fileLength - 1;
            if (start < 0) start = 0;
            if (start > end) start = 0;

            return new Range(start, end, true);

        } catch (Exception e) {
            return new Range(0, fileLength - 1, false);
        }
    }

    private static boolean isClientDisconnect(Throwable ex) {
        if (ex == null) return false;
        String msg = ex.getMessage();
        if (msg != null) {
            String lower = msg.toLowerCase();
            if (lower.contains("connection reset") || lower.contains("broken pipe")
                    || lower.contains("connection abort") || lower.contains("closed by peer")
                    || lower.contains("shutdown") || lower.contains("forcibly closed")) {
                return true;
            }
        }
        Throwable cause = ex.getCause();
        if (cause != null && cause != ex) {
            return isClientDisconnect(cause);
        }
        return ex instanceof java.nio.channels.ClosedChannelException
                || ex instanceof java.io.EOFException
                || ex instanceof java.net.SocketTimeoutException
                || ex instanceof java.net.SocketException;
    }

    // =========================
    // CONTENT HANDLER
    // =========================
    private class WebContentHandler extends SimpleChannelInboundHandler<FullHttpRequest> {

        @Override
        protected void channelRead0(ChannelHandlerContext ctx, FullHttpRequest request) {
            // 1. REST JSON Commands (POST / PUT)
            if (request.method() == HttpMethod.POST || request.method() == HttpMethod.PUT) {
                handleJsonCommand(ctx, request);
                return;
            }

            if (request.method() != HttpMethod.GET && request.method() != HttpMethod.HEAD) {
                sendError(ctx, METHOD_NOT_ALLOWED);
                return;
            }

            String rawPath = request.uri();
            String remoteAddr = request.headers().get(HttpHeaderNames.SERVER);
            String userAgent = request.headers().get(HttpHeaderNames.USER_AGENT);
            if (rawPath == null) return;

            // Skip WebSocket path
            if (rawPath.startsWith(CONTEXT_PATH_WEBSOCKET)) {
                return;
            }

            ContentHolder holder = resolveRequest(rawPath, remoteAddr, userAgent);

            if (holder == null || !holder.exists()) {
                sendError(ctx, NOT_FOUND);
                return;
            }

            boolean keepAlive = HttpUtil.isKeepAlive(request);

            serveContent(ctx, request, holder, keepAlive);
        }

        private void handleJsonCommand(ChannelHandlerContext ctx, FullHttpRequest request) {
            try {
                String body = request.content().toString(CharsetUtil.UTF_8);
                if (body != null && !body.trim().isEmpty()) {
                    java.util.Map<String, Object> map = (java.util.Map<String, Object>) (java.util.Map<?, ?>) apincer.music.core.utils.JsonUtils.toMap(body);
                    String command = String.valueOf(map.getOrDefault("command", ""));
                    if (!command.isEmpty()) {
                        java.util.Map<String, Object> response = wsHandler.handleCommand(command, map);
                        if (response != null) {
                            String jsonResponse = apincer.music.core.utils.JsonUtils.toJson(response);
                            FullHttpResponse res = new DefaultFullHttpResponse(
                                    HTTP_1_1,
                                    OK,
                                    Unpooled.copiedBuffer(jsonResponse, CharsetUtil.UTF_8)
                            );
                            res.headers().set(HttpHeaderNames.CONTENT_TYPE, "application/json; charset=UTF-8");
                            res.headers().set(HttpHeaderNames.SERVER, getServerSignature());
                            HttpUtil.setContentLength(res, res.content().readableBytes());

                            boolean keepAlive = HttpUtil.isKeepAlive(request);
                            if (keepAlive) {
                                res.headers().set(HttpHeaderNames.CONNECTION, HttpHeaderValues.KEEP_ALIVE);
                            }
                            ChannelFuture f = ctx.writeAndFlush(res);
                            if (!keepAlive) {
                                f.addListener(ChannelFutureListener.CLOSE);
                            }
                            return;
                        }
                    }
                }
                sendError(ctx, BAD_REQUEST);
            } catch (Exception e) {
                if (isClientDisconnect(e)) {
                    Log.d(TAG, "Client disconnected during JSON command: " + e.getMessage());
                } else {
                    Log.e(TAG, "Error handling JSON command", e);
                }
                sendError(ctx, INTERNAL_SERVER_ERROR);
            }
        }

        private void serveContent(ChannelHandlerContext ctx, FullHttpRequest request, ContentHolder content, boolean keepAlive) {
            RandomAccessFile raf = null;

            try {
                // Dynamic ETag & 304 Not Modified Support
                File file = content.getFilePath() != null ? new File(content.getFilePath()) : null;
                String etag = file != null ? generateETag(file) : null;
                String ifNoneMatch = request.headers().get(HttpHeaderNames.IF_NONE_MATCH);
                if (etag != null && etag.equals(ifNoneMatch)) {
                    FullHttpResponse notModified = new DefaultFullHttpResponse(HTTP_1_1, NOT_MODIFIED);
                    notModified.headers().set(HttpHeaderNames.ETAG, etag);
                    notModified.headers().set(HttpHeaderNames.SERVER, getServerSignature());
                    ctx.writeAndFlush(notModified);
                    return;
                }

                raf = new RandomAccessFile(content.getFilePath(), "r");
                long fileLength = raf.length();

                Range range = parseRange(request.headers().get(HttpHeaderNames.RANGE), fileLength);

                long start = range.start;
                long end = range.end;
                long length = end - start + 1;

                HttpResponseStatus status = range.partial ? PARTIAL_CONTENT : OK;

                HttpResponse response = new DefaultHttpResponse(HTTP_1_1, status);

                HttpUtil.setContentLength(response, length);

                String mime = MimeTypeUtils.getMimeTypeFromPath(content.getFilePath());
                if (mime == null) mime = "application/octet-stream";

                response.headers().set(HttpHeaderNames.CONTENT_TYPE, mime);
                response.headers().set(HttpHeaderNames.ACCEPT_RANGES, "bytes");
                response.headers().set(HttpHeaderNames.SERVER, getServerSignature());

                if (etag != null) {
                    response.headers().set(HttpHeaderNames.ETAG, etag);
                }
                
                if (range.partial) {
                    response.headers().set(HttpHeaderNames.CONTENT_RANGE,
                            "bytes " + start + "-" + end + "/" + fileLength);
                }

                // Audiophile Dynamic Headers
                if (content.getTrack() != null) {
                    Track tag = content.getTrack();
                    if (tag.getAudioSampleRate() > 0) response.headers().set("X-Audio-Sample-Rate", tag.getAudioSampleRate() + " Hz");
                    if (tag.getAudioBitsDepth() > 0) response.headers().set("X-Audio-Bit-Depth", tag.getAudioBitsDepth() + " bit");
                    if (tag.getAudioBitRate() > 0) response.headers().set("X-Audio-Bitrate", tag.getAudioBitRate()/1000 + " kbps");
                    response.headers().set("X-Audio-Format", String.valueOf(tag.getFileType()));
                    response.headers().set("transferMode.dlna.org", "Streaming");
                }

                keepAlive = HttpUtil.isKeepAlive(request);
                if (keepAlive) {
                    response.headers().set(HttpHeaderNames.CONNECTION, HttpHeaderValues.KEEP_ALIVE);
                }

                ctx.write(response);

                // HEAD request (no body)
                if (request.method() == HttpMethod.HEAD) {
                    final RandomAccessFile finalRaf = raf;
                    ChannelFuture headFuture = ctx.writeAndFlush(LastHttpContent.EMPTY_LAST_CONTENT);
                    headFuture.addListener((ChannelFutureListener) future -> {
                        if (finalRaf != null) {
                            try { finalRaf.close(); } catch (Exception ignored) {}
                        }
                    });
                    return;
                }

                boolean ssl = ctx.pipeline().get("ssl") != null;
                final RandomAccessFile streamRaf = raf;

                if (!ssl) {
                    // 🚀 ZERO COPY with guaranteed file descriptor cleanup
                    ctx.write(new DefaultFileRegion(streamRaf.getChannel(), start, length));
                    ChannelFuture f = ctx.writeAndFlush(LastHttpContent.EMPTY_LAST_CONTENT);

                    f.addListener((ChannelFutureListener) future -> {
                        if (streamRaf != null) {
                            try { streamRaf.close(); } catch (Exception ignored) {}
                        }
                    });

                    if (!keepAlive) f.addListener(ChannelFutureListener.CLOSE);

                } else {
                    // fallback for SSL
                    ctx.write(new HttpChunkedInput(
                            new ChunkedFile(streamRaf, start, length, 8192)
                    ));
                    ChannelFuture f = ctx.writeAndFlush(LastHttpContent.EMPTY_LAST_CONTENT);

                    f.addListener((ChannelFutureListener) future -> {
                        if (streamRaf != null) {
                            try { streamRaf.close(); } catch (Exception ignored) {}
                        }
                    });

                    if (!keepAlive) f.addListener(ChannelFutureListener.CLOSE);
                }

            } catch (Exception e) {
                if (isClientDisconnect(e)) {
                    Log.d(TAG, "Client disconnected during streaming: " + e.getMessage());
                } else {
                    Log.e(TAG, "Streaming error", e);
                }
                if (raf != null) {
                    try { raf.close(); } catch (Exception ignore) {}
                }
                if (ctx.channel().isActive()) {
                    sendError(ctx, INTERNAL_SERVER_ERROR);
                }
            }
        }

        private void sendError(ChannelHandlerContext ctx, HttpResponseStatus status) {
            FullHttpResponse res = new DefaultFullHttpResponse(
                    HTTP_1_1,
                    status,
                    Unpooled.copiedBuffer("Error: " + status + "\r\n", CharsetUtil.UTF_8)
            );

            res.headers().set(HttpHeaderNames.CONTENT_TYPE, "text/plain; charset=UTF-8");
            res.headers().set(HttpHeaderNames.SERVER, getServerSignature());
            HttpUtil.setContentLength(res, res.content().readableBytes());
            ctx.writeAndFlush(res).addListener(ChannelFutureListener.CLOSE);
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
            if (isClientDisconnect(cause)) {
                Log.d(TAG, "Netty client disconnected: " + ctx.channel().remoteAddress() + " (" + cause.getMessage() + ")");
            } else {
                Log.e(TAG, "Netty pipeline error: " + ctx.channel().remoteAddress(), cause);
            }
            ctx.close();
        }
    }

    private class WebSocketFrameHandler extends WebSocketContent {
        private final ChannelGroup channels =
                new DefaultChannelGroup(GlobalEventExecutor.INSTANCE);

        @Override
        protected void broadcastMessage(String jsonResponse) {
            if (channels != null && !channels.isEmpty()) {
                channels.writeAndFlush(new TextWebSocketFrame(jsonResponse));
            }
        }

        public String getNamespace() {
            return CONTEXT_PATH_WEBSOCKET;
        }

        public ChannelHandler createInboundHandler() {
            return new SimpleChannelInboundHandler<TextWebSocketFrame>() {
                @Override
                public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
                    if (evt instanceof WebSocketServerProtocolHandler.HandshakeComplete) {
                        channels.add(ctx.channel());
                        Log.d(TAG, "Netty WS Handshake complete: " + ctx.channel().remoteAddress());
                        for (java.util.Map<String, Object> message : getWelcomeMessages()) {
                            if (message != null) {
                                try {
                                    String json = apincer.music.core.utils.JsonUtils.toJson(message);
                                    ctx.writeAndFlush(new TextWebSocketFrame(json));
                                } catch (Exception e) {
                                    Log.e(TAG, "Error sending welcome message", e);
                                }
                            }
                        }
                    } else {
                        super.userEventTriggered(ctx, evt);
                    }
                }

                @Override
                public void handlerRemoved(ChannelHandlerContext ctx) {
                    channels.remove(ctx.channel());
                }

                @Override
                protected void channelRead0(ChannelHandlerContext ctx, TextWebSocketFrame msg) {
                    try {
                        String text = msg.text();
                        Log.d(TAG, "Netty WS Received: " + text);
                        java.util.Map<String, Object> messageMap = (java.util.Map<String, Object>) (java.util.Map<?, ?>) apincer.music.core.utils.JsonUtils.toMap(text);
                        String command = String.valueOf(messageMap.getOrDefault("command", ""));
                        java.util.Map<String, Object> response = handleCommand(command, messageMap);
                        if (response != null) {
                            String json = apincer.music.core.utils.JsonUtils.toJson(response);
                            ctx.writeAndFlush(new TextWebSocketFrame(json));
                        }
                    } catch (Exception e) {
                        Log.e(TAG, "Error handling WebSocket message", e);
                    }
                }

                @Override
                public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
                    if (isClientDisconnect(cause)) {
                        Log.d(TAG, "Netty WS client disconnected: " + ctx.channel().remoteAddress() + " (" + cause.getMessage() + ")");
                    } else {
                        Log.e(TAG, "Netty WebSocket error: " + ctx.channel().remoteAddress(), cause);
                    }
                    ctx.close();
                }
            };
        }
    }
}