package apincer.android.mmate.dlna.transport;

import static apincer.android.mmate.utils.MusicTagUtils.isAACFile;
import static apincer.android.mmate.utils.MusicTagUtils.isAIFFile;
import static apincer.android.mmate.utils.MusicTagUtils.isALACFile;
import static apincer.android.mmate.utils.MusicTagUtils.isHiRes;
import static apincer.android.mmate.utils.MusicTagUtils.isLossless;
import static apincer.android.mmate.utils.MusicTagUtils.isLosslessFormat;
import static io.netty.handler.codec.http.HttpResponseStatus.*;
import static io.netty.handler.codec.http.HttpVersion.HTTP_1_1;

import android.app.ActivityManager;
import android.content.Context;
import android.util.Log;
import android.util.LruCache;

import org.jupnp.model.meta.RemoteDevice;
import org.jupnp.transport.Router;
import org.jupnp.transport.spi.InitializationException;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import apincer.android.mmate.MusixMateApp;
import apincer.android.mmate.player.PlayerInfo;
import apincer.android.mmate.repository.MusicTag;
import apincer.android.mmate.utils.MimeTypeUtils;
import apincer.android.mmate.utils.StringUtils;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.ByteBuf;
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
import io.netty.channel.nio.NioIoHandler;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.http.*;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.stream.ChunkedFile;
import io.netty.handler.stream.ChunkedStream;
import io.netty.handler.stream.ChunkedWriteHandler;
import io.netty.handler.timeout.IdleStateEvent;
import io.netty.handler.timeout.IdleStateHandler;
import io.netty.util.CharsetUtil;
import io.netty.util.HashedWheelTimer;
import io.netty.util.Timer;

public class NettyContentServerImpl extends StreamServerImpl.StreamServer {
    private final Context context;
    private static final String TAG = "NettyContentServer";
    public static final int DEFAULT_SERVER_PORT = 8089;

    // Audio streaming optimizations - now with adaptive configuration
    private int AUDIO_CHUNK_SIZE = 16384; // 16KB chunks for audio streaming, will be adjusted
    private int INITIAL_BUFFER_SIZE = 65536; // 64KB initial buffer for quick start
    private int HIGH_WATERMARK = 262144; // 256KB high watermark for buffer control
    private int LOW_WATERMARK = 131072; // 128KB low watermark

    // Device-specific optimizations
    private static final Map<String, ClientProfile> CLIENT_PROFILES = new ConcurrentHashMap<>();

    // Content caching
    private final LruCache<String, ByteBuf> audioChunkCache;
    private final Map<String, Long> popularFiles = new ConcurrentHashMap<>();

    // Pre-buffering and scheduling
    private final Timer scheduler = new HashedWheelTimer(100, TimeUnit.MILLISECONDS);

    private final int serverPort;
    private final EventLoopGroup bossGroup;
    private final EventLoopGroup workerGroup;
    private Channel serverChannel;
    private Thread serverThread;
    private volatile boolean isRunning = false;

    //RFC 1123 format required by HTTP/DLNA // EEE, dd MMM yyyy HH:mm:ss z
    DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern("EEE, dd MMM yyyy HH:mm:ss z");

    // For HTTP Range requests
    private static final Pattern RANGE_PATTERN = Pattern.compile("bytes=(\\d+)-(\\d*)");

    public NettyContentServerImpl(Context context, Router router, StreamServerConfigurationImpl configuration) {
        super(context, router, configuration);
        this.context = context;
        this.serverPort = DEFAULT_SERVER_PORT;

        // Initialize adaptive buffer settings based on device capabilities
        initializeBuffers();

        // Optimize thread allocation based on device capabilities
        int cpuCores = Runtime.getRuntime().availableProcessors();
        this.bossGroup = new MultiThreadIoEventLoopGroup(2, NioIoHandler.newFactory());
        this.workerGroup = new MultiThreadIoEventLoopGroup(Math.min(4,cpuCores), NioIoHandler.newFactory());

        // Initialize content cache with appropriate size based on available memory
        ActivityManager am = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
        int memoryClass = am.getMemoryClass();
        int cacheSize = Math.max(10, memoryClass / 4);  // 1/4 of available memory up to 64MB
        audioChunkCache = new LruCache<>(cacheSize * 1024 * 1024) {
            @Override
            protected int sizeOf(String key, ByteBuf value) {
                return value.capacity();
            }

            @Override
            protected void entryRemoved(boolean evicted, String key, ByteBuf oldValue, ByteBuf newValue) {
                if (oldValue != null && oldValue.refCnt() > 0) {
                    oldValue.release();
                }
            }
        };

        // Initialize default client profiles
        initClientProfiles();

        Log.i(TAG, "NettyContentServer initialized with " + cpuCores +
                " cores, cache size: " + cacheSize + "MB, audio chunk size: " + AUDIO_CHUNK_SIZE);
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
    public void initServer(InetAddress bindAddress) throws InitializationException {
        if (isRunning) {
            Log.w(TAG, "Server already running");
            return;
        }

       // Log.d(TAG, "Starting netty content server: " + bindAddress.getHostAddress() + ":" + serverPort);

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
                        .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 5000)
                        .childOption(ChannelOption.WRITE_BUFFER_WATER_MARK,
                                new WriteBufferWaterMark(LOW_WATERMARK, HIGH_WATERMARK));

                b.childHandler(new ContentServerInitializer());

                // Bind and start to accept incoming connections
                ChannelFuture f = b.bind(bindAddress.getHostAddress(), serverPort).sync();
                serverChannel = f.channel();
                isRunning = true;
                Log.i(TAG, "Http server started successfully on " +
                        bindAddress.getHostAddress() + ":" + serverPort +
                        " with buffer sizes: chunk=" + AUDIO_CHUNK_SIZE +
                        ", high=" + HIGH_WATERMARK + ", low=" + LOW_WATERMARK);

                // Start metrics reporting in background
               // startMetricsReporting();
            } catch (Exception ex) {
                isRunning = false;
                Log.e(TAG, "Http server initialization failed: " + ex.getMessage(), ex);
                // Clean up resources if startup fails
                try {
                    if (serverChannel != null) {
                        serverChannel.close();
                        serverChannel = null;
                    }
                } catch (Exception ignored) {}
            }
        }, "HTTPServer");

        serverThread.setPriority(Thread.MAX_PRIORITY - 1); // Higher priority for audio streaming
        serverThread.start();

        // Wait a short time to verify server started properly
        try {
            Thread.sleep(500);
            if (!isRunning) {
                throw new InitializationException("Failed to start http server");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new InitializationException("Http server start was interrupted", e);
        }
    }

    @Override
    public void stopServer() {
       // Log.d(TAG, "Shutting down netty content server");
        isRunning = false;

        // Stop the scheduler
        scheduler.stop();

        // Clear caches
        audioChunkCache.evictAll();
        //clientMetrics.clear();
        popularFiles.clear();
       // clientRateLimiters.clear();
      //  connectionCounter.clear();

        // Close the server channel
        if (serverChannel != null) {
            try {
                serverChannel.close().sync(); // Wait for channel to close
               // Log.d(TAG, "Server channel closed");
            } catch (InterruptedException e) {
                Log.w(TAG, "Http server channel closing interrupted", e);
                Thread.currentThread().interrupt();
            } finally {
                serverChannel = null;
            }
        }

        // Shutdown the event loop groups
        try {
            if (bossGroup != null) {
                bossGroup.shutdownGracefully(0, 3, TimeUnit.SECONDS).await(3, TimeUnit.SECONDS);
               // Log.d(TAG, "Boss group shutdown");
            }
        } catch (InterruptedException e) {
            Log.w(TAG, "Http server boss group shutdown interrupted", e);
            Thread.currentThread().interrupt();
        }

        try {
            if (workerGroup != null) {
                workerGroup.shutdownGracefully(0, 5, TimeUnit.SECONDS).await(5, TimeUnit.SECONDS);
               // Log.d(TAG, "Worker group shutdown");
            }
        } catch (InterruptedException e) {
            Log.w(TAG, "Http server worker group shutdown interrupted", e);
            Thread.currentThread().interrupt();
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

        Log.i(TAG, "Http server stopped successfully");
    }

    @Override
    protected String getServerVersion() {
        return "Netty/4.2.1";
    }

    public Context getContext() {
        return context;
    }

    /**
     * Warm up cache for a file that might be needed soon
     */
    private void warmupCache(String filePath) {
        if (filePath == null || !isRunning) return;

        File file = new File(filePath);
        if (!file.exists() || !file.canRead()) return;

        // Schedule cache warmup as a background task
        scheduler.newTimeout(timeout -> {
            try {
                // Read beginning of file into cache
                String cacheKey = filePath + ":0:" + AUDIO_CHUNK_SIZE;
                if (audioChunkCache.get(cacheKey) == null) {
                    RandomAccessFile raf = new RandomAccessFile(file, "r");

                    // Only read if file is large enough
                    if (raf.length() > AUDIO_CHUNK_SIZE) {
                        byte[] data = new byte[AUDIO_CHUNK_SIZE];
                        raf.read(data);
                        ByteBuf buf = Unpooled.wrappedBuffer(data);
                        audioChunkCache.put(cacheKey, buf);
                       // Log.d(TAG, "Pre-cached initial chunk of: " + filePath);
                    }
                    raf.close();
                }
            } catch (Exception e) {
                Log.w(TAG, "Failed to warmup cache for " + filePath, e);
            }
        }, 100, TimeUnit.MILLISECONDS);
    }

    public class ContentServerInitializer extends ChannelInitializer<SocketChannel> {
        @Override
        protected void initChannel(SocketChannel ch) {

            ChannelPipeline pipeline = ch.pipeline();
            pipeline.addLast(new HttpServerCodec());
            pipeline.addLast(new HttpObjectAggregator(65536)); // Aggregate HTTP messages
            pipeline.addLast(new ChunkedWriteHandler());
            pipeline.addLast(new IdleStateHandler(120, 60, 0)); // Extended timeouts for music streaming
            pipeline.addLast(new ContentServerHandler());
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
        private final byte[] content;
        private final String fileName;
        private final MusicTag musicTag;

        public ContentHolder(String contentType, String filePath, MusicTag tag) {
            this(contentType, filePath, null, new File(filePath).getName(), tag);
        }

        public ContentHolder(String contentType, String filePath, byte[] content, String fileName, MusicTag tag) {
            this.filePath = filePath;
            this.contentType = contentType;
            this.content = content;
            this.fileName = fileName;
            this.musicTag = tag;
        }

        public boolean exists() {
            if (content != null) {
                return true;
            }
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

    private class ContentServerHandler extends SimpleChannelInboundHandler<FullHttpRequest> {

        private ClientProfile detectClientProfile(ChannelHandlerContext ctx, HttpHeaders headers) {
            String userAgent = headers.get(HttpHeaderNames.USER_AGENT, "unknown").toLowerCase();
            String clientIp = getRemoteAddress(ctx);

            // Check for specific controller/renderer identification in User-Agent
            // Check for MPD with more detailed detection
            RemoteDevice dev = MusixMateApp.getInstance().getRenderer(clientIp);
            if(dev != null) {
                Log.i(TAG, "found dlna renderer " + dev.getDetails().getFriendlyName() + ": " + userAgent);
                return CLIENT_PROFILES.getOrDefault("mpd", CLIENT_PROFILES.get("default"));
            }else if (userAgent.contains("music player daemon") ||
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

            // Validate request
            if (!request.decoderResult().isSuccess()) {
                //errorCounter.incrementAndGet();
                sendError(ctx, BAD_REQUEST);
                return;
            }

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

            // Handle in-memory content
            if (holder.content != null) {
                serveByteContent(ctx, request, holder, keepAlive, profile);

                return;
            }

            // Handle file content
            if (holder.filePath != null) {
                serveFileContent(ctx, request, holder, keepAlive, profile);
                return;
            }

            // Should never reach here if proper validation is done
            //errorCounter.incrementAndGet();
            sendError(ctx, INTERNAL_SERVER_ERROR);
        }

        private void serveByteContent(ChannelHandlerContext ctx, FullHttpRequest request,
                                      ContentHolder holder, boolean keepAlive, ClientProfile profile) {
            HttpResponse response = new DefaultHttpResponse(HTTP_1_1, OK);
            HttpUtil.setContentLength(response, holder.content.length);
            setContentTypeHeader(response, holder.contentType);
            setDateAndCacheHeaders(response, holder.fileName);
            setAudioHeaders(response, holder, profile);

            if (keepAlive) {
                response.headers().set(HttpHeaderNames.CONNECTION, HttpHeaderValues.KEEP_ALIVE);
            }

            // Write the initial line and headers
            ctx.write(response);

            // Write the content
            if (request.method() != HttpMethod.HEAD) {
                ctx.write(new HttpChunkedInput(new ChunkedStream(new ByteArrayInputStream(holder.content))));
            }

            // Write the end marker
            ChannelFuture lastContentFuture = ctx.writeAndFlush(LastHttpContent.EMPTY_LAST_CONTENT);

            // Close the connection if not keep-alive
            if (!keepAlive) {
                lastContentFuture.addListener(ChannelFutureListener.CLOSE);
            }
        }

        private void serveFileContent(ChannelHandlerContext ctx, FullHttpRequest request,
                                      ContentHolder holder, boolean keepAlive, ClientProfile profile) {
            try {
                File file = new File(holder.filePath);
                long fileLength = file.length();

                // Update metrics - count file access for popularity tracking
                popularFiles.compute(holder.filePath, (k, v) -> v == null ? 1L : v + 1L);

                // Parse Range header if present
                String rangeHeader = request.headers().get(HttpHeaderNames.RANGE);
                if (rangeHeader != null) {

                    serveRangeContent(ctx, request, file, fileLength, rangeHeader, holder, keepAlive, profile);
                    return;
                }

                // Handle normal file serving
                HttpResponse response = new DefaultHttpResponse(HTTP_1_1, OK);
                HttpUtil.setContentLength(response, fileLength);
                setContentTypeHeader(response, holder.contentType);
                setDateAndCacheHeaders(response, holder.fileName);
                setAudioHeaders(response, holder, profile);

                if (keepAlive) {
                    response.headers().set(HttpHeaderNames.CONNECTION, HttpHeaderValues.KEEP_ALIVE);
                }

                // Write the initial line and headers
                ctx.write(response);

                // Write the content
                if (request.method() != HttpMethod.HEAD) {
                    // Use optimized chunk size for audio streaming
                    RandomAccessFile rfile = new RandomAccessFile(file, "r");
                    ChunkedFile chunkedFile = new ChunkedFile(rfile, 0, fileLength, profile.chunkSize);

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
                //errorCounter.incrementAndGet();
                sendError(ctx, INTERNAL_SERVER_ERROR);
            }
        }

        private void serveRangeContent(ChannelHandlerContext ctx, FullHttpRequest request,
                                       File file, long fileLength, String rangeHeader,
                                       ContentHolder holder, boolean keepAlive, ClientProfile profile) throws IOException {
            // Parse range header
            Matcher matcher = RANGE_PATTERN.matcher(rangeHeader);
            if (!matcher.matches()) {
                // Invalid range format
                //errorCounter.incrementAndGet();
                sendError(ctx, REQUESTED_RANGE_NOT_SATISFIABLE);
                return;
            }

            // Parse range values
            long rangeStart = Long.parseLong(matcher.group(1));

            // Handle end range
            long rangeEnd;
            if (matcher.group(2) != null && matcher.group(2).isEmpty()) {
                rangeEnd = fileLength - 1;
            } else {
                rangeEnd = Long.parseLong(matcher.group(2));
            }

            // Validate ranges
            if (rangeStart > rangeEnd || rangeStart >= fileLength || rangeEnd >= fileLength) {
                //errorCounter.incrementAndGet();
                HttpResponse response = new DefaultHttpResponse(HTTP_1_1, REQUESTED_RANGE_NOT_SATISFIABLE);
                response.headers().set(
                        HttpHeaderNames.CONTENT_RANGE,
                        "bytes */" + fileLength
                );
                sendError(ctx, REQUESTED_RANGE_NOT_SATISFIABLE);
                return;
            }

            // Calculate content length
            long contentLength = rangeEnd - rangeStart + 1;

            // Update metrics - record only the requested range size
            //metrics.recordRequest(contentLength);
            //bytesTransferred.addAndGet(contentLength);

            // Create response
            HttpResponse response = new DefaultHttpResponse(HTTP_1_1, PARTIAL_CONTENT);
            HttpUtil.setContentLength(response, contentLength);

            // Set Content-Range header
            response.headers().set(
                    HttpHeaderNames.CONTENT_RANGE,
                    "bytes " + rangeStart + "-" + rangeEnd + "/" + fileLength
            );

            setContentTypeHeader(response, holder.contentType);
            setDateAndCacheHeaders(response, holder.fileName);
            setAudioHeaders(response, holder, profile);

            // Set Accept-Ranges header to inform client we support range requests
            response.headers().set(HttpHeaderNames.ACCEPT_RANGES, "bytes");

            if (keepAlive) {
                response.headers().set(HttpHeaderNames.CONNECTION, HttpHeaderValues.KEEP_ALIVE);
            }

            // Write the initial line and headers
            ctx.write(response);

            // Write the content
            if (request.method() != HttpMethod.HEAD) {
                // Check cache first for this chunk
                String cacheKey = holder.filePath + ":" + rangeStart + ":" + contentLength;
                ByteBuf cachedChunk = audioChunkCache.get(cacheKey);

                if (cachedChunk != null && cachedChunk.readableBytes() == contentLength) {
                    // Use cached chunk if available
                    Log.d(TAG, "Using cached chunk for " + holder.fileName);

                    // Retain for this read operation
                    ByteBuf duplicateBuffer = cachedChunk.retainedDuplicate();
                    ctx.write(new DefaultHttpContent(duplicateBuffer));
                } else {
                    // Read from file
                    RandomAccessFile raf = new RandomAccessFile(file, "r");
                    try {
                        raf.seek(rangeStart);

                        // Check if chunk size is small enough to cache
                        if (contentLength <= AUDIO_CHUNK_SIZE * 2L) {
                            // For small ranges that might be repeatedly requested (e.g., file headers)
                            // read into memory and cache
                            byte[] data = new byte[(int)contentLength];
                            raf.readFully(data);

                            ByteBuf buffer = Unpooled.wrappedBuffer(data);
                            // Store in cache (will release old if needed)
                            audioChunkCache.put(cacheKey, buffer.retain());

                            // Write to client
                            ctx.write(new DefaultHttpContent(buffer.retain()));
                        } else {
                            // For larger ranges, use chunked streaming
                            ctx.write(new HttpChunkedInput(
                                    new ChunkedFile(raf, rangeStart, contentLength, profile.chunkSize)));

                            // Don't close RAF here as ChunkedFile will handle it
                            raf = null;
                        }
                    } finally {
                        if (raf != null) {
                            raf.close();
                        }
                    }
                }
            }

            // Write the end marker
            ChannelFuture lastContentFuture = ctx.writeAndFlush(LastHttpContent.EMPTY_LAST_CONTENT);

            // Close the connection if not keep-alive
            if (!keepAlive) {
                lastContentFuture.addListener(ChannelFutureListener.CLOSE);
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
            //
            //now.atZone(zoneId);

           // response.headers().set(HttpHeaderNames.DATE, dateFormatter.format(new Date()));
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

        /**
         * Set audio-specific headers to help clients with playback
         */
        private void setAudioHeaders(HttpResponse response, ContentHolder holder, ClientProfile profile) {
            response.headers().set(HttpHeaderNames.ACCEPT_RANGES, "bytes");

            // If we have music tag metadata, add it to help clients
            if (holder.getMusicTag() != null) {
                MusicTag tag = holder.getMusicTag();
                String filePath = tag.getPath().toLowerCase();

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
                        // if (profile == CLIENT_PROFILES.get("mconnect") || profile == CLIENT_PROFILES.get("jplay")) {
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
            Log.w(TAG, "Exception caught in content server: " + cause.getMessage(), cause);
            //errorCounter.incrementAndGet();
            if (ctx.channel().isActive()) {
                sendError(ctx, INTERNAL_SERVER_ERROR);
            }
        }

        private ContentHolder getContent(ChannelHandlerContext ctx, FullHttpRequest request) {
            String requestUri = request.uri();
            if (requestUri.startsWith("/")) {
                requestUri = requestUri.substring(1);
            }

            // Sanitize path to prevent directory traversal attacks
            requestUri = sanitizePath(requestUri);
            if (requestUri == null) {
                return null;
            }

            List<String> pathSegments = Arrays.asList(requestUri.split("/", -1));
            if (pathSegments.size() < 2 || pathSegments.size() > 3) {
                Log.d(TAG, "HTTP request is invalid: " + requestUri);
                return null;
            }

            String contentId = pathSegments.get(1);
            return getSong(ctx, request, contentId);
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
            if (remoteAddress instanceof InetSocketAddress) {
                InetSocketAddress inetSocketAddress = (InetSocketAddress) remoteAddress;
                remoteIp = inetSocketAddress.getAddress().getHostAddress();
                //System.out.println("Remote IP address: " + remoteIp);
            }
            return remoteIp;
        }

        private ContentHolder getSong(ChannelHandlerContext ctx, FullHttpRequest request, String contentId) {
            if (contentId == null || contentId.isEmpty()) {
                return null;
            }

            try {
                long id = StringUtils.toLong(contentId);

                MusicTag tag = MusixMateApp.getInstance().getOrmLite().findById(id);
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
                String agent = request.headers().get(HttpHeaderNames.USER_AGENT);
                String clientIp = getRemoteAddress(ctx);
                PlayerInfo player = PlayerInfo.buildStreamPlayer(getContext(), agent, clientIp);
                MusixMateApp.getPlayerControl().publishPlayingSong(player, tag);

                // Warm up cache for this file
                warmupCache(tag.getPath());

                // Get MIME type for the file
                String mimeType = MimeTypeUtils.getMimeTypeFromPath(tag.getPath());

               // Log.i(TAG, "Serving media: " + tag.getTitle() + " [" + mimeType + "]");
                return new ContentHolder(mimeType, tag.getPath(), tag);
            } catch (Exception e) {
                Log.e(TAG, "Error retrieving song: " + e.getMessage(), e);
                return null;
            }
        }
    }
}
