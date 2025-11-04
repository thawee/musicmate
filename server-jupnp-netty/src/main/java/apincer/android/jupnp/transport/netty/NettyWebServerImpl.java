package apincer.android.jupnp.transport.netty;

import static apincer.music.core.utils.TagUtils.isAACFile;
import static apincer.music.core.utils.TagUtils.isAIFFile;
import static apincer.music.core.utils.TagUtils.isALACFile;
import static apincer.music.core.utils.TagUtils.isHiRes;
import static apincer.music.core.utils.TagUtils.isLossless;
import static apincer.music.core.utils.TagUtils.isLosslessFormat;
import static io.netty.handler.codec.http.HttpResponseStatus.INTERNAL_SERVER_ERROR;
import static io.netty.handler.codec.http.HttpResponseStatus.METHOD_NOT_ALLOWED;
import static io.netty.handler.codec.http.HttpResponseStatus.NOT_FOUND;
import static io.netty.handler.codec.http.HttpResponseStatus.OK;
import static io.netty.handler.codec.http.HttpResponseStatus.PARTIAL_CONTENT;
import static io.netty.handler.codec.http.HttpVersion.HTTP_1_1;

import android.annotation.SuppressLint;
import android.app.ActivityManager;
import android.content.Context;
import android.util.Log;

import com.google.gson.Gson;

import java.io.File;
import java.io.RandomAccessFile;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadFactory;

import apincer.music.core.database.MusicTag;
import apincer.music.core.playback.WebStreamingPlayer;
import apincer.music.core.playback.spi.PlaybackTarget;
import apincer.music.core.repository.FileRepository;
import apincer.music.core.repository.TagRepository;
import apincer.music.core.server.BaseServer;
import apincer.music.core.server.spi.WebServer;
import apincer.music.core.utils.MimeTypeUtils;
import apincer.music.core.utils.StringUtils;
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
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpRequest;
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
import io.netty.util.CharsetUtil;
import io.netty.util.concurrent.DefaultThreadFactory;
import io.netty.util.concurrent.GlobalEventExecutor;

public class NettyWebServerImpl extends BaseServer implements WebServer {
    private final Context context;
    private static final String TAG = "NettyWebServer";

    // Audio streaming optimizations - now with adaptive configuration
    private int AUDIO_CHUNK_SIZE = 16384; // 16KB chunks for audio streaming, will be adjusted
    private int INITIAL_BUFFER_SIZE = 65536; // 64KB initial buffer for quick start
    private int HIGH_WATERMARK = 262144; // 256KB high watermark for buffer control
    private int LOW_WATERMARK = 131072; // 128KB low watermark

    // Device-specific optimizations
    private static final Map<String, ClientProfile> CLIENT_PROFILES = new ConcurrentHashMap<>();

    private final TagRepository repos;
    private final int serverPort;
    private EventLoopGroup bossGroup;
    private EventLoopGroup workerGroup;
    private Channel serverChannel;
    private Thread serverThread;
    private volatile boolean isRunning = false;
    private final WebSocketFrameHandler wsHandler;

    public NettyWebServerImpl(Context context, FileRepository fileRepos, TagRepository tagRepos) {
        super(context, fileRepos, tagRepos);
        this.context = context;
        this.serverPort = WEB_SERVER_PORT;
        this.repos = tagRepos;

        addLibInfo("Netty", "4.2.6");
        // Initialize adaptive buffer settings based on device capabilities
        initializeBuffers();

        wsHandler = new WebSocketFrameHandler();
        // Initialize default client profiles
        initClientProfiles();
    }

    /**
     * Initialize buffer sizes adaptively based on device capabilities
     */
    private void initializeBuffers() {
        ActivityManager am = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
        int memoryClass = am.getMemoryClass();

        // Scale buffer sizes based on available memory
        // Optimized buffers for high-RAM devices (12GB+)
        if (memoryClass > 512) {  // Devices with 8GB+ RAM
            AUDIO_CHUNK_SIZE = 65536;  // 64KB chunks
            INITIAL_BUFFER_SIZE = 262144;  // 256KB initial
            HIGH_WATERMARK = 1048576;  // 1MB high
            LOW_WATERMARK = 524288;   // 512KB low
        }else if (memoryClass > 192) {  // High-end device
            AUDIO_CHUNK_SIZE = 24576;  // 24KB chunks
            INITIAL_BUFFER_SIZE = 131072;  // 128KB initial
            HIGH_WATERMARK = 524288;  // 512KB high
            LOW_WATERMARK = 262144;   // 256KB low
        } else if (memoryClass < 64) {  // Low-end device
            AUDIO_CHUNK_SIZE = 8192;   // 8KB chunks
            INITIAL_BUFFER_SIZE = 32768;  // 32KB initial
            HIGH_WATERMARK = 131072;   // 128KB high
            LOW_WATERMARK = 65536;     // 64KB low
        }

        // Network type-based adjustments. always use wifi
        // On WiFi we can be more aggressive with buffer sizes
        AUDIO_CHUNK_SIZE = (int)(AUDIO_CHUNK_SIZE * 1.5);
    }

    private void initClientProfiles() {
        // Default profile
        CLIENT_PROFILES.put("default", new ClientProfile(
                AUDIO_CHUNK_SIZE,
                true,  // Keep-alive support
                5      // Max concurrent connections
        ));

        // Apple devices (more likely to play ALAC)
        CLIENT_PROFILES.put("apple", new ClientProfile(
                AUDIO_CHUNK_SIZE * 2,  // Larger chunks for lossless audio
                true,
                4
        ));

        // MPD profile - optimized for audiophile playback
        CLIENT_PROFILES.put("mpd", new ClientProfile(
                49152,       // 48KB chunks - larger chunks for high-resolution audio files
                true,        // Keep-alive supports continuous playback
                3,           // Limited connections for focused bandwidth
                true,        // Full support for gapless playback (critical for audiophile use)
                true,         // Support for high-res audio formats (24-bit/192kHz+)
                false,
                true,
                false
        ));

        // mConnectHD controller profile
        CLIENT_PROFILES.put("mconnect", new ClientProfile(
                AUDIO_CHUNK_SIZE * 2,  // Larger chunks for better performance
                true,                  // Keep-alive for continuous control
                3,                     // Standard connection limit
                true,                  // Support gapless
                true,                   // Support high-res
                true,
                false
        ));

        // jPlay controller profile - audiophile player with specific requirements
        CLIENT_PROFILES.put("jplay", new ClientProfile(
                49152,       // 48KB for optimal audio buffering
                true,        // Keep-alive
                2,           // Limited connections for focused bandwidth
                true,        // Support gapless playback
                true,         // Support high-res
                true,
                false
        ));
    }

    @Override
    public void initServer(InetAddress bindAddress) throws Exception {
        if (isRunning) {
            Log.w(TAG, "WebServer already running");
            return;
        }

       // Log.d(TAG, "Starting netty content server: " + bindAddress.getHostAddress() + ":" + serverPort);

        // Optimize thread allocation based on device capabilities
        // Name threads for better debugging
        ThreadFactory bossThreadFactory = new DefaultThreadFactory("content-boss");
        ThreadFactory workerThreadFactory = new DefaultThreadFactory("content-worker");
        int processorCount = Runtime.getRuntime().availableProcessors();
        this.bossGroup = new MultiThreadIoEventLoopGroup(1, bossThreadFactory, NioIoHandler.newFactory());
        this.workerGroup = new MultiThreadIoEventLoopGroup(Math.min(4,processorCount), workerThreadFactory, NioIoHandler.newFactory());

        serverThread = new Thread(() -> {
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
                       // .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 5000)
                        .childOption(ChannelOption.WRITE_BUFFER_WATER_MARK,
                                new WriteBufferWaterMark(LOW_WATERMARK, HIGH_WATERMARK));

                b.childHandler(new ContentServerInitializer());

                // Bind and start to accept incoming connections
                ChannelFuture f = b.bind(bindAddress.getHostAddress(), serverPort).sync();
                serverChannel = f.channel();
                isRunning = true;
                Log.i(TAG, "WebServer started on " +
                        bindAddress.getHostAddress() + ":" + serverPort +" successfully.");

                // Start metrics reporting in background
               // startMetricsReporting();
            } catch (Exception ex) {
                ex.printStackTrace();
                isRunning = false;
                Log.e(TAG, "WebServer initialization failed: " + ex.getMessage(), ex);
                // Clean up resources if startup fails
                try {
                    if (serverChannel != null) {
                        serverChannel.close();
                        serverChannel = null;
                    }
                } catch (Exception ignored) {}
            }
        }, "WebServer");

        serverThread.start();

        // Wait a short time to verify server started properly
        try {
            Thread.sleep(500);
            if (!isRunning) {
                throw new Exception("Failed to start WebServer");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new Exception("WebServer start was interrupted", e);
        }
    }

    @Override
    public void stopServer() {
       // Log.d(TAG, "Shutting down netty content server");
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

        Log.i(TAG, "WebServer stopped successfully");
    }

    @Override
    public String getComponentName() {
        return "WebServer";
    }

    public Context getContext() {
        return context;
    }

    @Override
    public int getListenPort() {
        return WEB_SERVER_PORT;
    }

    public class ContentServerInitializer extends ChannelInitializer<SocketChannel> {
        @Override
        protected void initChannel(SocketChannel ch) {

            ChannelPipeline pipeline = ch.pipeline();

            // Handles basic HTTP protocol encoding and decoding.
            pipeline.addLast(new HttpServerCodec());

            // 2. Aggregates HTTP message chunks into a single FullHttpRequest.
            // This is now safe because there are no large file uploads.
            pipeline.addLast(new HttpObjectAggregator(65536)); // 64KB max request size

            // Handles writing large files in chunks without high memory use. Essential.
            pipeline.addLast(new ChunkedWriteHandler());

            // 5. Handles the WebSocket handshake and protocol details for any requests to "/ws".
            // It will pass all other requests down the pipeline. THIS MUST COME FIRST.
            pipeline.addLast(new WebSocketServerProtocolHandler(CONTEXT_PATH_WEBSOCKET, null, true));

            // 6. Your custom logic for handling WebSocket messages (frames).
            pipeline.addLast(wsHandler);

            // Your custom logic to find and send the music file.
            //pipeline.addLast(new ContentServerHandler());
            pipeline.addLast(new WebContentHandler());
        }
    }

    /**
     * Client profile for device-specific optimizations
     */
    static class ClientProfile {
        final boolean supportsBitPerfectStreaming;
        int chunkSize;
        final boolean keepAlive;
        final int maxConnections;
        final boolean supportsGapless;
        final boolean supportsHighRes;
        final boolean supportsDirectStreaming;
        final boolean supportsLosslessStreaming;

        ClientProfile(int chunkSize, boolean keepAlive, int maxConnections) {
            this(chunkSize, keepAlive, maxConnections, false, false,false,false);
        }

        ClientProfile(int chunkSize, boolean keepAlive, int maxConnections,
                      boolean supportsGapless, boolean supportsHighRes, boolean supportsDirectStreaming, boolean supportsLosslessStreaming) {
            this.chunkSize = chunkSize;
            this.keepAlive = keepAlive;
            this.maxConnections = maxConnections;
            this.supportsGapless = supportsGapless;
            this.supportsHighRes = supportsHighRes;
            this.supportsDirectStreaming = supportsDirectStreaming;
            this.supportsLosslessStreaming = supportsLosslessStreaming;
            this.supportsBitPerfectStreaming = false;
        }

        ClientProfile(int chunkSize, boolean keepAlive, int maxConnections,
                      boolean supportsGapless, boolean supportsHighRes, boolean supportsDirectStreaming, boolean supportsLosslessStreaming, boolean supportsBitPerfectStreaming) {
            this.chunkSize = chunkSize;
            this.keepAlive = keepAlive;
            this.maxConnections = maxConnections;
            this.supportsGapless = supportsGapless;
            this.supportsHighRes = supportsHighRes;
            this.supportsDirectStreaming = supportsDirectStreaming;
            this.supportsLosslessStreaming = supportsLosslessStreaming;
            this.supportsBitPerfectStreaming = supportsBitPerfectStreaming;
        }
    }

    /**
     * ValueHolder for media content.
     */
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

        public boolean exists() {
            if (filePath != null) {
                File file = new File(filePath);
                return file.exists() && file.canRead();
            }
            return false;
        }

        public MusicTag getMusicTag() {
            return musicTag;
        }
    }

    private class WebContentHandler extends SimpleChannelInboundHandler<FullHttpRequest> {

        private ClientProfile detectClientProfile(ChannelHandlerContext ctx, HttpHeaders headers) {
            String userAgent = headers.get(HttpHeaderNames.USER_AGENT, "unknown").toLowerCase();
            String clientIp = getRemoteAddress(ctx);

            // Check for specific controller/renderer identification in User-Agent
            // Check for MPD with more detailed detection
           // RendererDevice dev = null;
           // if(getPlaybackService() != null) {
           //     dev = getPlaybackService().getRendererByIpAddress(clientIp);
           // }
           // if(dev != null) {
           //     Log.i(TAG, "found dlna renderer " + dev.getFriendlyName() + ": " + userAgent);
           //     return CLIENT_PROFILES.getOrDefault("mpd", CLIENT_PROFILES.get("default"));
            //}else
            if (userAgent.contains("music player daemon") ||
                    userAgent.contains("mpd") ||
                    userAgent.contains("mpdclient")) {
                // Use RopieeeXL use MPD client
                Log.i(TAG, "Music Player Daemon detected from " + clientIp + ": " + userAgent);
                return CLIENT_PROFILES.getOrDefault("mpd", CLIENT_PROFILES.get("default"));
            } else if (userAgent.contains("mconnect") || userAgent.contains("mconnecthd")) {
                Log.i(TAG, "mConnectHD controller detected from " + clientIp);
                return CLIENT_PROFILES.getOrDefault("mconnect", CLIENT_PROFILES.get("default"));
            } else if (userAgent.contains("jplay") || userAgent.contains("j-play")) {
                Log.i(TAG, "jPlay controller detected from " + clientIp);
                return CLIENT_PROFILES.getOrDefault("jplay", CLIENT_PROFILES.get("default"));
            } else if (userAgent.contains("sonos")) {
                Log.i(TAG, "Sonos player detected from " + clientIp);
                return CLIENT_PROFILES.getOrDefault("sonos", CLIENT_PROFILES.get("default"));
            } else if (userAgent.contains("bubble") || userAgent.contains("bubbleupnp")) {
                Log.i(TAG, "BubbleUPnP detected from " + clientIp);
                return CLIENT_PROFILES.getOrDefault("bubble", CLIENT_PROFILES.get("default"));
            }

            // Standard device detection
            if (userAgent.contains("playstation") || userAgent.contains("sony")) {
                return CLIENT_PROFILES.getOrDefault("sony", CLIENT_PROFILES.get("default"));
            } else if (userAgent.contains("samsung")) {
                return CLIENT_PROFILES.getOrDefault("samsung", CLIENT_PROFILES.get("default"));
            } else if (userAgent.contains("apple") || userAgent.contains("iphone") ||
                    userAgent.contains("ipad") || userAgent.contains("ipod") ||
                    userAgent.contains("mac")) {
                return CLIENT_PROFILES.getOrDefault("apple", CLIENT_PROFILES.get("default"));
            }

            return CLIENT_PROFILES.get("default");
        }

        @Override
        protected void channelRead0(ChannelHandlerContext ctx, FullHttpRequest request) {

            // We only want to react to the beginning of the request.
         //   if (msg instanceof HttpRequest request) {
                // Check if we support the HTTP method
                if (!request.method().equals(HttpMethod.GET) && !request.method().equals(HttpMethod.HEAD)) {
                    //errorCounter.incrementAndGet();
                    sendError(ctx, METHOD_NOT_ALLOWED);
                    return;
                }

                // Get client profile for optimized delivery
                ClientProfile profile = detectClientProfile(ctx, request.headers());

                // Get requested content
                ContentHolder holder = getContent(ctx, request);

                // Check if content exists
                if (holder == null || !holder.exists()) {
                    // errorCounter.incrementAndGet();
                    sendError(ctx, NOT_FOUND);
                    return;
                }

                boolean keepAlive = HttpUtil.isKeepAlive(request) && profile.keepAlive;

                // Handle file content
                serveContent(ctx, request, holder, keepAlive, profile);
           // } else {
           //     sendError(ctx, BAD_REQUEST);
           // }
        }

        private void serveContent(ChannelHandlerContext ctx, HttpRequest request,
                                  ContentHolder holder, boolean keepAlive, ClientProfile profile) {
            try {
                File file = new File(holder.filePath);
                long fileLength = file.length();
                RandomAccessFile raf = new RandomAccessFile(file, "r");

                long start = 0;
                long length = fileLength;

                // --- RANGE REQUEST HANDLING LOGIC ---
                String rangeHeader = request.headers().get(HttpHeaderNames.RANGE);
                HttpResponseStatus status = OK; // Default to 200 OK

                if (rangeHeader != null && !rangeHeader.isEmpty()) {
                    // Example Range: "bytes=100-500" or "bytes=100-"
                    String[] ranges = rangeHeader.substring("bytes=".length()).split("-");
                    start = Long.parseLong(ranges[0]);

                    if (ranges.length > 1) {
                        long end = Long.parseLong(ranges[1]);
                        length = end - start + 1;
                    } else {
                        length = fileLength - start;
                    }

                    if (start > 0 || length < fileLength) {
                        status = PARTIAL_CONTENT; // It's a partial request, so status is 206
                    }
                }
                // --- END OF RANGE LOGIC ---

                HttpResponse response = new DefaultHttpResponse(HTTP_1_1, status);

                // Set the appropriate length based on whether it's a full or partial response
                HttpUtil.setContentLength(response, length);

                setContentTypeHeader(response, holder.contentType);
                setDateAndCacheHeaders(response, holder.fileName);
                setAudioHeaders(response, holder, profile);

                // If it's a partial request, we MUST add the Content-Range header
                if (status == PARTIAL_CONTENT) {
                    String contentRange = "bytes " + start + "-" + (start + length - 1) + "/" + fileLength;
                    response.headers().set(HttpHeaderNames.CONTENT_RANGE, contentRange);
                }

                if (keepAlive) {
                    response.headers().set(HttpHeaderNames.CONNECTION, HttpHeaderValues.KEEP_ALIVE);
                }

                // Write the initial line and headers
                ctx.write(response);

                // Write the content using a ChunkedFile that respects the start and length
                ctx.write(new HttpChunkedInput(new ChunkedFile(raf, start, length, profile.chunkSize)), ctx.newProgressivePromise());

                ChannelFuture lastContentFuture = ctx.writeAndFlush(LastHttpContent.EMPTY_LAST_CONTENT);

                if (!keepAlive) {
                    lastContentFuture.addListener(ChannelFutureListener.CLOSE);
                }

            } catch (Exception e) { // Catching broader exceptions for safety
                Log.e(TAG, "Failed to serve file: " + e.getMessage(), e);
                if (ctx.channel().isActive()) {
                    sendError(ctx, INTERNAL_SERVER_ERROR);
                }
            }
        }

        private void setContentTypeHeader(HttpResponse response, String contentType) {
            response.headers().set(HttpHeaderNames.CONTENT_TYPE, contentType);
        }

        private void setDateAndCacheHeaders(HttpResponse response, String fileName) {
            // Add server header
            response.headers().set(HttpHeaderNames.SERVER, getServerSignature(getComponentName()));

            response.headers().set(HttpHeaderNames.DATE, formatDate(System.currentTimeMillis()));

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

        /**
         * Set audio-specific headers to help clients with playback
         */
        private void setAudioHeaders(HttpResponse response, ContentHolder holder, ClientProfile profile) {
            response.headers().set(HttpHeaderNames.ACCEPT_RANGES, "bytes");

            // If we have music tag metadata, add it to help clients
            if (holder.getMusicTag() != null) {
                MusicTag tag = holder.getMusicTag();
                //String filePath = tag.getPath().toLowerCase();

                // Set more specific MIME types for certain formats if needed
                if (isALACFile(tag)) {
                    // ALAC uses audio/x-alac or audio/apple-lossless
                    response.headers().set(HttpHeaderNames.CONTENT_TYPE, "audio/apple-lossless");
                } else if(isAACFile(tag)) {
                    // AAC in M4A container
                    response.headers().set(HttpHeaderNames.CONTENT_TYPE, "audio/mp4");
                } else if (isAIFFile(tag)) {
                    // Ensure AIFF has the correct MIME type
                    response.headers().set(HttpHeaderNames.CONTENT_TYPE, "audio/aiff");
                }

                // Add custom X-headers for clients that can use metadata
                if (tag.getTitle() != null) {
                    response.headers().set("X-Audio-Title", tag.getTitle());
                }
                if (tag.getArtist() != null) {
                    response.headers().set("X-Audio-Artist", tag.getArtist());
                }
                if (tag.getAlbum() != null) {
                    response.headers().set("X-Audio-Album", tag.getAlbum());
                }

                // Add format/codec info if available
                if (tag.getFileType() != null) {
                    response.headers().set("X-Audio-Format", tag.getFileType());
                }
                if (tag.getAudioEncoding() != null) {
                    response.headers().set("X-Audio-Codec", tag.getAudioEncoding());
                }

                if(isLossless(tag) || isHiRes(tag)) {
                    // Add sample rate and bit depth info for high-res audio clients
                    if (profile.supportsHighRes) {
                        if (tag.getAudioSampleRate() > 0) {
                            response.headers().set("X-Audio-Sample-Rate", String.valueOf(tag.getAudioSampleRate()));
                        }
                        if (tag.getAudioBitsDepth() > 0) {
                            response.headers().set("X-Audio-Bit-Depth", String.valueOf(tag.getAudioBitsDepth()));
                        }
                    }

                    // Special header for mConnectHD and jPlay
                    if (profile.supportsDirectStreaming) {
                        // These controllers benefit from direct streaming hint
                        response.headers().set("X-Direct-Streaming", "true");
                    }

                    // For RopieeeXL and audiophile renderers, add lossless streaming hint
                    if (profile.supportsLosslessStreaming) {
                        response.headers().set("X-Lossless-Streaming", "true");
                    }

                    if (profile.supportsBitPerfectStreaming) {
                        // Signal bit-perfect streaming capabilities
                        response.headers().set("X-Bit-Perfect-Streaming", "true");

                        // Indicate original unmodified format for bit-perfect playback
                        response.headers().set("X-Original-Format", "true");
                    }
                }

                // Add duration metadata if available
                if (tag.getAudioDuration() > 0) {
                    response.headers().set("X-Audio-Duration", String.valueOf(tag.getAudioDuration()));
                }

                // DLNA headers with proper capitalization
                response.headers().set("TransferMode.DLNA.ORG", "Streaming");
                response.headers().set("ContentFeatures.DLNA.ORG", getDLNAContentFeatures(tag));

                // If client supports gapless playback
                if (profile.supportsGapless) {
                    response.headers().set("X-Gapless-Support", "true");
                }
            }
        }

        /**
         * Generate DLNA content features string based on audio format
         */
        private String getDLNAContentFeatures(MusicTag tag) {
            String mimeType = MimeTypeUtils.getMimeTypeFromPath(tag.getPath());

            if (mimeType.contains("mp3")) {
                return "DLNA.ORG_PN=MP3;DLNA.ORG_OP=01;DLNA.ORG_CI=0";
            } else if (mimeType.contains("flac")) {
                return "DLNA.ORG_PN=FLAC;DLNA.ORG_OP=01;DLNA.ORG_CI=0";
            } else if (mimeType.contains("wav")) {
                return "DLNA.ORG_PN=WAV;DLNA.ORG_OP=01;DLNA.ORG_CI=0";
            } else if (isAACFile(tag)) {
                return "DLNA.ORG_PN=AAC;DLNA.ORG_OP=01;DLNA.ORG_CI=0";
            } else if (mimeType.contains("ogg")) {
                return "DLNA.ORG_PN=OGG;DLNA.ORG_OP=01;DLNA.ORG_CI=0";
            } else if (isALACFile(tag)) {
                return "DLNA.ORG_PN=ALAC;DLNA.ORG_OP=01;DLNA.ORG_CI=0";
            } else if (isLosslessFormat(tag)) {
                return "DLNA.ORG_PN=LPCM;DLNA.ORG_OP=01;DLNA.ORG_CI=0";
            }

            // Default profile
            return "DLNA.ORG_OP=01;DLNA.ORG_CI=0";
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
            Log.w(TAG, "Exception caught in WebServer: " + cause.getMessage(), cause);
            if (ctx.channel().isActive()) {
                sendError(ctx, INTERNAL_SERVER_ERROR);
                ctx.close();
            }
        }

        private ContentHolder getContent(ChannelHandlerContext ctx, HttpRequest request) {

            String requestUri = request.uri();
            if (requestUri.equals("/")) {
                requestUri = "/index.html";
            }

            // Sanitize path to prevent directory traversal attacks
            requestUri = sanitizePath(requestUri);
            if (requestUri == null) {
                return null;
            }

            if (requestUri.startsWith(CONTEXT_PATH_COVERART)) {
                return getAlbumArt(request, requestUri);
            } else if (requestUri.startsWith(CONTEXT_PATH_MUSIC)) {
                    return getSong(ctx, request, requestUri);
            }else {
                return getFile(ctx, request, requestUri);
            }
        }

        private ContentHolder getSong(ChannelHandlerContext ctx, HttpRequest request, String requestUri) {
            List<String> pathSegments = Arrays.asList(requestUri.split("/", -1));
            if (pathSegments.size() < 2 || pathSegments.size() > 3) {
                Log.d(TAG, "HTTP request is invalid: " + requestUri);
                return null;
            }

            String contentId = pathSegments.get(1);
            if (contentId == null || contentId.isEmpty()) {
                return null;
            }

            try {
                long id = StringUtils.toLong(contentId);

                MusicTag tag = repos.findById(id);
                if (tag == null) {
                    Log.d(TAG, "Song not found for ID: " + contentId);
                    return null;
                }

                // Verify the file exists
                File file = new File(tag.getPath());
                if (!file.exists() || !file.canRead()) {
                    Log.w(TAG, "File not found or not readable: " + tag.getPath());
                    return null;
                }

                // Record streaming information
                String userAgent = request.headers().get(HttpHeaderNames.USER_AGENT);
                if(getPlaybackService() != null) {
                    String clientIp = getRemoteAddress(ctx);
                    PlaybackTarget player = WebStreamingPlayer.Factory.create(clientIp, userAgent, clientIp);
                    getPlaybackService().switchPlayer(player, false);
                    getPlaybackService().onMediaTrackChanged(tag);
                    // }
                }

                // Get MIME type for the file
                String mimeType = MimeTypeUtils.getMimeTypeFromPath(tag.getPath());

                // Log.i(TAG, "Serving media: " + tag.getTitle() + " [" + mimeType + "]");
                return new ContentHolder(mimeType, tag.getPath(), tag);
            } catch (Exception e) {
                Log.e(TAG, "Error retrieving song: " + e.getMessage(), e);
                return null;
            }
        }

        private ContentHolder getAlbumArt(HttpRequest request, String uri) {
                String albumUniqueKey = uri.substring(CONTEXT_PATH_COVERART.length());
                File albumArt = getFileRepos().getCoverArtByAlbumartFilename(albumUniqueKey);
                if(albumArt == null || albumArt.length() == 0) {
                    albumArt = getDefaultAlbumArt();
                }
            // Get MIME type for the file
            String mimeType = MimeTypeUtils.getMimeTypeFromPath(albumArt.getPath());

            // Log.i(TAG, "Serving media: " + tag.getTitle() + " [" + mimeType + "]");
            return new ContentHolder(mimeType, albumArt.getPath(), null);
        }

        private ContentHolder getFile(ChannelHandlerContext ctx, HttpRequest request, String path) {
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
                return new ContentHolder(mimeType, filePath.getPath(), null);
            } catch (Exception e) {
                Log.e(TAG, "Error retrieving song: " + e.getMessage(), e);
                return null;
            }
        }

        /**
         * Simple path sanitization to prevent directory traversal
         */
        private String sanitizePath(String path) {
            // Remove any parent directory references
            if (path.contains("../") || path.contains("..\\")) {
                Log.w(TAG, "Potential directory traversal attempt: " + path);
                return null;
            }

            // Remove any double slashes that could be used to confuse path traversal detection
            path = path.replace("//", "/");

            return path;
        }

        private String getRemoteAddress(ChannelHandlerContext ctx) {
            // Retrieve the remote address
            String remoteIp = "";
            SocketAddress remoteAddress = ctx.channel().remoteAddress();
            if (remoteAddress instanceof InetSocketAddress inetSocketAddress) {
                remoteIp = inetSocketAddress.getAddress().getHostAddress();
            }
            return remoteIp;
        }
    }

    private class WebSocketFrameHandler extends SimpleChannelInboundHandler<WebSocketFrame> {
        private final ChannelGroup channels = new DefaultChannelGroup(GlobalEventExecutor.INSTANCE);
        private final Gson gson = new Gson();

        private final WebSocketContent wsContent = new WebSocketContent() {
            @Override
            protected void broadcastMessage(String jsonResponse) {
                channels.writeAndFlush(jsonResponse);
            }
        };

        @SuppressLint("CheckResult")
        public WebSocketFrameHandler() {
            super();
        }

        @Override
        public void handlerAdded(ChannelHandlerContext ctx) {
            channels.add(ctx.channel());
        }

        @Override
        public void handlerRemoved(ChannelHandlerContext ctx) {
            channels.remove(ctx.channel());
        }

        @Override
        public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
            if (evt instanceof WebSocketServerProtocolHandler.HandshakeComplete) {
                // This event is fired when the WebSocket handshake is successful.
                Log.d(TAG, "WebSocket client connected: " + ctx.channel().remoteAddress());
                List<Map<String, Object>> messages = wsContent.getWelcomeMessages();
                for (Map<String, Object> message : messages) {
                    sendMessage(ctx, message);
                }
            } else {
                // Pass other events up the pipeline
                super.userEventTriggered(ctx, evt);
            }
        }

        private void sendMessage(ChannelHandlerContext ctx, Map<String, Object> response) {
            if(response != null) {
                ctx.channel().writeAndFlush(new TextWebSocketFrame(gson.toJson(response)));
                Log.d(TAG, "Response message: " + gson.toJson(response));
            }
        }

        @Override
        protected void channelRead0(ChannelHandlerContext ctx, WebSocketFrame frame) {
            if (frame instanceof TextWebSocketFrame) {
                String request = ((TextWebSocketFrame) frame).text();
                Log.d(TAG, "Received message: " + request);
                Map<String, Object> message = gson.fromJson(request, Map.class);
                String command = message.getOrDefault("command", "").toString();

                Map<String, Object> response = wsContent.handleCommand(command, message);
                sendMessage(ctx, response);
            } else {
                String message = "unsupported frame type: " + frame.getClass().getName();
                throw new UnsupportedOperationException(message);
            }
        }
    }
}
