package apincer.android.mmate.dlna.transport;

import static apincer.android.mmate.utils.MusicTagUtils.isALACFile;
import static apincer.android.mmate.utils.MusicTagUtils.isLosslessFormat;
import static io.netty.handler.codec.http.HttpResponseStatus.*;
import static io.netty.handler.codec.http.HttpVersion.HTTP_1_1;

import android.app.ActivityManager;
import android.content.Context;
import android.util.Log;
import android.util.LruCache;

import androidx.core.content.ContextCompat;

import org.jupnp.transport.Router;
import org.jupnp.transport.spi.InitializationException;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import apincer.android.mmate.MusixMateApp;
import apincer.android.mmate.R;
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

    // Connection and performance tracking
    private final AtomicLong requestCounter = new AtomicLong(0);
    private final AtomicLong errorCounter = new AtomicLong(0);
    private final AtomicLong bytesTransferred = new AtomicLong(0);
    private final Map<String, StreamMetrics> clientMetrics = new ConcurrentHashMap<>();

    // Content caching
    private final LruCache<String, ByteBuf> audioChunkCache;
    private final Map<String, Long> popularFiles = new ConcurrentHashMap<>();

    // Rate limiting
    private final Map<String, TokenBucket> clientRateLimiters = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, AtomicInteger> connectionCounter = new ConcurrentHashMap<>();

    // Pre-buffering and scheduling
    private final Timer scheduler = new HashedWheelTimer(100, TimeUnit.MILLISECONDS);

    private final int serverPort;
    private final EventLoopGroup bossGroup;
    private final EventLoopGroup workerGroup;
    private Channel serverChannel;
    private Thread serverThread;
    private volatile boolean isRunning = false;

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
        this.bossGroup = new MultiThreadIoEventLoopGroup(Math.min(2, cpuCores/4), NioIoHandler.newFactory());
        this.workerGroup = new MultiThreadIoEventLoopGroup(cpuCores * 2, NioIoHandler.newFactory());

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
        if (memoryClass > 192) {  // High-end device
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

        // Sony devices (PlayStation, etc)
        CLIENT_PROFILES.put("sony", new ClientProfile(
                AUDIO_CHUNK_SIZE * 2,
                false, // Some Sony devices have issues with keep-alive
                2      // Limit connections to avoid buffer issues
        ));

        // Samsung TVs/devices
        CLIENT_PROFILES.put("samsung", new ClientProfile(
                AUDIO_CHUNK_SIZE,
                true,
                3
        ));

        // Apple devices (more likely to play ALAC)
        CLIENT_PROFILES.put("apple", new ClientProfile(
                AUDIO_CHUNK_SIZE * 2,  // Larger chunks for lossless audio
                true,
                4
        ));

        // RopieeeXL - Audiophile Raspberry Pi renderer
        // Optimized for high-quality audio streaming
        CLIENT_PROFILES.put("ropieee", new ClientProfile(
                32768,       // 32KB chunks for better throughput with high-res audio
                true,        // Keep-alive support for continuous playback
                2,           // Limited connections for more focused bandwidth
                true,        // Support gapless playback
                true         // Support for high-res audio
        ));

        // mConnectHD controller profile
        CLIENT_PROFILES.put("mconnect", new ClientProfile(
                AUDIO_CHUNK_SIZE * 2,  // Larger chunks for better performance
                true,                  // Keep-alive for continuous control
                3,                     // Standard connection limit
                true,                  // Support gapless
                true                   // Support high-res
        ));

        // jPlay controller profile - audiophile player with specific requirements
        CLIENT_PROFILES.put("jplay", new ClientProfile(
                49152,       // 48KB for optimal audio buffering
                true,        // Keep-alive
                2,           // Limited connections for focused bandwidth
                true,        // Support gapless playback
                true         // Support high-res
        ));

        // Sonos players - known for reliable streaming
        CLIENT_PROFILES.put("sonos", new ClientProfile(
                24576,       // 24KB chunks
                true,        // Keep-alive
                3,           // Standard connections
                true,        // Gapless support
                true         // High-res support
        ));

        // Bubble UPnP - popular controller app
        CLIENT_PROFILES.put("bubble", new ClientProfile(
                AUDIO_CHUNK_SIZE * 2,  // Larger chunks
                true,                  // Keep-alive
                4,                     // More connections for browsing
                true,                  // Gapless support
                true                   // High-res support
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
                Log.i(TAG, "Content server started successfully on " +
                        bindAddress.getHostAddress() + ":" + serverPort +
                        " with buffer sizes: chunk=" + AUDIO_CHUNK_SIZE +
                        ", high=" + HIGH_WATERMARK + ", low=" + LOW_WATERMARK);

                // Start metrics reporting in background
                startMetricsReporting();
            } catch (Exception ex) {
                isRunning = false;
                Log.e(TAG, "Server initialization failed: " + ex.getMessage(), ex);
                // Clean up resources if startup fails
                try {
                    if (serverChannel != null) {
                        serverChannel.close();
                        serverChannel = null;
                    }
                } catch (Exception ignored) {}
            }
        }, "MusicStreamServer");

        serverThread.setPriority(Thread.MAX_PRIORITY - 1); // Higher priority for audio streaming
        serverThread.start();

        // Wait a short time to verify server started properly
        try {
            Thread.sleep(500);
            if (!isRunning) {
                throw new InitializationException("Failed to start content server");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new InitializationException("Server start was interrupted", e);
        }
    }

    private void startMetricsReporting() {
        // Schedule periodic metrics logging
        scheduler.newTimeout(timeout -> {
            if (isRunning) {
                long totalRequests = requestCounter.get();
                long totalErrors = errorCounter.get();
                long totalBytes = bytesTransferred.get();

                if (totalRequests > 0) {
                    Log.i(TAG, String.format(Locale.US,
                            "Streaming metrics: %d requests, %d errors (%.2f%%), %.2f MB transferred",
                            totalRequests, totalErrors,
                            (totalErrors * 100.0f / totalRequests),
                            totalBytes / (1024.0f * 1024.0f)));

                    // Log client-specific metrics
                    for (Map.Entry<String, StreamMetrics> entry : clientMetrics.entrySet()) {
                        if (entry.getValue().requestCount > 10) {  // Only log active clients
                            Log.d(TAG, String.format(Locale.US,
                                    "Client %s: %d requests, %.2f MB, Avg throughput: %.2f KB/s",
                                    entry.getKey(), entry.getValue().requestCount,
                                    entry.getValue().bytesServed / (1024.0f * 1024.0f),
                                    entry.getValue().getEffectiveThroughput() / 1024.0f));
                        }
                    }
                }

                // Schedule next report
                startMetricsReporting();
            }
        }, 5, TimeUnit.MINUTES);
    }

    @Override
    public void stopServer() {
       // Log.d(TAG, "Shutting down netty content server");
        isRunning = false;

        // Stop the scheduler
        scheduler.stop();

        // Clear caches
        audioChunkCache.evictAll();
        clientMetrics.clear();
        popularFiles.clear();
        clientRateLimiters.clear();
        connectionCounter.clear();

        // Close the server channel
        if (serverChannel != null) {
            try {
                serverChannel.close().sync(); // Wait for channel to close
               // Log.d(TAG, "Server channel closed");
            } catch (InterruptedException e) {
                Log.w(TAG, "Server channel closing interrupted", e);
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
            Log.w(TAG, "Boss group shutdown interrupted", e);
            Thread.currentThread().interrupt();
        }

        try {
            if (workerGroup != null) {
                workerGroup.shutdownGracefully(0, 5, TimeUnit.SECONDS).await(5, TimeUnit.SECONDS);
               // Log.d(TAG, "Worker group shutdown");
            }
        } catch (InterruptedException e) {
            Log.w(TAG, "Worker group shutdown interrupted", e);
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

        Log.i(TAG, "Content server stopped successfully");
    }

    @Override
    protected String getServerVersion() {
        return "Netty/4.2.0";
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

    /**
     * Rate limiter implementation using token bucket algorithm
     */
    private static class TokenBucket {
        private final long capacity;
        private final double refillRate;
        private double tokens;
        private long lastRefillTime;

        public TokenBucket(long capacity, double tokensPerSecond) {
            this.capacity = capacity;
            this.refillRate = tokensPerSecond;
            this.tokens = capacity;
            this.lastRefillTime = System.currentTimeMillis();
        }

        public synchronized boolean tryConsume(long tokensToConsume) {
            refill();

            if (tokens < tokensToConsume) {
                return false;
            }

            tokens -= tokensToConsume;
            return true;
        }

        private void refill() {
            long now = System.currentTimeMillis();
            double secondsSinceLastRefill = (now - lastRefillTime) / 1000.0;
            double tokensToAdd = secondsSinceLastRefill * refillRate;

            tokens = Math.min(capacity, tokens + tokensToAdd);
            lastRefillTime = now;
        }
    }

    public class ContentServerInitializer extends ChannelInitializer<SocketChannel> {
        @Override
        protected void initChannel(SocketChannel ch) {
            // Get client IP for tracking
            String clientIp = ch.remoteAddress().getAddress().getHostAddress();

            // Check connection limit for this client
            AtomicInteger connectionCount = connectionCounter.computeIfAbsent(clientIp,
                    k -> new AtomicInteger(0));

            int count = connectionCount.incrementAndGet();

            // If client has too many connections, reject
            if (count > 10) { // Hard limit for any client
                Log.w(TAG, "Too many connections from " + clientIp + " (" + count + "), rejecting");
                ch.close();
                return;
            }

            // Register connection close callback to decrement counter
            ch.closeFuture().addListener((ChannelFutureListener) future ->
                    connectionCounter.computeIfPresent(clientIp, (k, v) -> {
                        v.decrementAndGet();
                        return v;
                    }));

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
        int chunkSize;
        final boolean keepAlive;
        final int maxConnections;
        final boolean supportsGapless;
        final boolean supportsHighRes;

        ClientProfile(int chunkSize, boolean keepAlive, int maxConnections) {
            this(chunkSize, keepAlive, maxConnections, false, false);
        }

        ClientProfile(int chunkSize, boolean keepAlive, int maxConnections,
                      boolean supportsGapless, boolean supportsHighRes) {
            this.chunkSize = chunkSize;
            this.keepAlive = keepAlive;
            this.maxConnections = maxConnections;
            this.supportsGapless = supportsGapless;
            this.supportsHighRes = supportsHighRes;
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

        private ClientProfile detectClientProfile(HttpHeaders headers) {
            String userAgent = headers.get(HttpHeaderNames.USER_AGENT, "unknown").toLowerCase();
            String clientIp = "";

            // Get more detailed client information
            try {
                if (headers.contains("X-Forwarded-For")) {
                    clientIp = headers.get("X-Forwarded-For");
                } else if (headers.contains("X-Real-IP")) {
                    clientIp = headers.get("X-Real-IP");
                }
            } catch (Exception e) {
                // If we can't get the IP, just continue
            }

            // Check for specific controller/renderer identification in User-Agent
            if (userAgent.contains("ropieee") || userAgent.contains("ropieexl")) {
                Log.i(TAG, "RopieeeXL renderer detected from " + clientIp);
                return CLIENT_PROFILES.getOrDefault("ropieee", CLIENT_PROFILES.get("default"));
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

            // Check for known client IPs
            if (!clientIp.isEmpty()) {
                // Get existing metrics for this client if available
                StreamMetrics metrics = clientMetrics.get(clientIp);
                if (metrics != null && metrics.requestCount > 10) {
                    // Adjust chunk size based on observed performance
                    ClientProfile profile = new ClientProfile(
                            AUDIO_CHUNK_SIZE,
                            true,
                            5,
                            false,
                            false
                    );

                    // If client has had rebuffer events, reduce chunk size
                    if (metrics.rebufferEvents > 2) {
                        profile.chunkSize = Math.max(8192, profile.chunkSize / 2);
                    } else if (metrics.getEffectiveThroughput() > 1024 * 1024) {
                        // If client has shown good throughput, increase chunk size
                        profile.chunkSize = Math.min(32768, profile.chunkSize * 2);
                    }

                    Log.d(TAG, "Using adaptive profile for " + clientIp +
                            ", chunk size: " + profile.chunkSize +
                            " based on " + metrics.requestCount + " requests");
                    return profile;
                }
            }

            return CLIENT_PROFILES.get("default");
        }

        @Override
        protected void channelRead0(ChannelHandlerContext ctx, FullHttpRequest request) {
            // Track request metrics
            requestCounter.incrementAndGet();

            // Get client information
            String clientIp = ((InetSocketAddress)ctx.channel().remoteAddress()).getAddress().getHostAddress();

            // Update client metrics
            StreamMetrics metrics = clientMetrics.computeIfAbsent(clientIp,
                    k -> new StreamMetrics());

            // Validate request
            if (!request.decoderResult().isSuccess()) {
                errorCounter.incrementAndGet();
                sendError(ctx, BAD_REQUEST);
                return;
            }

            // Check if we support the HTTP method
            if (!request.method().equals(HttpMethod.GET) && !request.method().equals(HttpMethod.HEAD)) {
                errorCounter.incrementAndGet();
                sendError(ctx, METHOD_NOT_ALLOWED);
                return;
            }

            // Apply rate limiting for the client
            TokenBucket rateLimiter = clientRateLimiters.computeIfAbsent(clientIp,
                    k -> new TokenBucket(1024 * 1024, 512 * 1024)); // 1MB/s with 512KB/s refill

            if (!rateLimiter.tryConsume(1024)) { // Take minimal token to check rate
                errorCounter.incrementAndGet();
                Log.w(TAG, "Rate limit exceeded for client: " + clientIp);
                sendError(ctx, TOO_MANY_REQUESTS);
                return;
            }

            // Get client profile for optimized delivery
            ClientProfile profile = detectClientProfile(request.headers());

            // Get requested content
            ContentHolder holder = getContent(request);

            // Check if content exists
            if (holder == null || !holder.exists()) {
                errorCounter.incrementAndGet();
                sendError(ctx, NOT_FOUND);
                return;
            }

            boolean keepAlive = HttpUtil.isKeepAlive(request) && profile.keepAlive;

            // Handle in-memory content
            if (holder.content != null) {
                serveByteContent(ctx, request, holder, keepAlive, profile);

                // Update metrics
                metrics.recordRequest(holder.content.length);
                bytesTransferred.addAndGet(holder.content.length);
                return;
            }

            // Handle file content
            if (holder.filePath != null) {
                serveFileContent(ctx, request, holder, keepAlive, profile, metrics);
                return;
            }

            // Should never reach here if proper validation is done
            errorCounter.incrementAndGet();
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
                                      ContentHolder holder, boolean keepAlive, ClientProfile profile,
                                      StreamMetrics metrics) {
            try {
                File file = new File(holder.filePath);
                long fileLength = file.length();

                // Update metrics - count file access for popularity tracking
                popularFiles.compute(holder.filePath, (k, v) -> v == null ? 1L : v + 1L);

                // Parse Range header if present
                String rangeHeader = request.headers().get(HttpHeaderNames.RANGE);
                if (rangeHeader != null) {
                    Matcher matcher = RANGE_PATTERN.matcher(rangeHeader);
                    if (matcher.matches()) {
                        // Track seek operation in metrics
                        metrics.recordSeek();
                    }

                    serveRangeContent(ctx, request, file, fileLength, rangeHeader, holder, keepAlive, profile, metrics);
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

                    // Track file serving - record the whole file size
                    metrics.recordRequest(fileLength);
                    bytesTransferred.addAndGet(fileLength);

                    ctx.write(new HttpChunkedInput(chunkedFile));

                    // Log streaming start
                    if (holder.getMusicTag() != null) {
                        Log.i(TAG, "Started streaming: " + holder.getMusicTag().getTitle());
                    }
                }

                // Write the end marker
                ChannelFuture lastContentFuture = ctx.writeAndFlush(LastHttpContent.EMPTY_LAST_CONTENT);

                // Close the connection if not keep-alive
                if (!keepAlive) {
                    lastContentFuture.addListener(ChannelFutureListener.CLOSE);
                }

            } catch (IOException e) {
                Log.e(TAG, "Failed to serve file: " + e.getMessage(), e);
                errorCounter.incrementAndGet();
                sendError(ctx, INTERNAL_SERVER_ERROR);
            }
        }

        private void serveRangeContent(ChannelHandlerContext ctx, FullHttpRequest request,
                                       File file, long fileLength, String rangeHeader,
                                       ContentHolder holder, boolean keepAlive, ClientProfile profile,
                                       StreamMetrics metrics) throws IOException {
            // Parse range header
            Matcher matcher = RANGE_PATTERN.matcher(rangeHeader);
            if (!matcher.matches()) {
                // Invalid range format
                errorCounter.incrementAndGet();
                sendError(ctx, REQUESTED_RANGE_NOT_SATISFIABLE);
                return;
            }

            // Parse range values
            long rangeStart = Long.parseLong(matcher.group(1));

            // Handle end range
            long rangeEnd;
            if (matcher.group(2).isEmpty()) {
                rangeEnd = fileLength - 1;
            } else {
                rangeEnd = Long.parseLong(matcher.group(2));
            }

            // Validate ranges
            if (rangeStart > rangeEnd || rangeStart >= fileLength || rangeEnd >= fileLength) {
                errorCounter.incrementAndGet();
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
            metrics.recordRequest(contentLength);
            bytesTransferred.addAndGet(contentLength);

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

                // Log range streaming with seeking position (useful for debugging streaming issues)
                if (holder.getMusicTag() != null) {
                    double seekPositionSec = rangeStart / (double)(fileLength) *
                            (holder.getMusicTag().getAudioDuration());

                    // Check if this is a rebuffer event (small seeks near current position)
                    if (Math.abs(seekPositionSec - metrics.lastSeekPosition) < 2.0 &&
                            seekPositionSec > metrics.lastSeekPosition) {
                        // This looks like a rebuffer (client requesting next chunk)
                        metrics.recordRebuffer();
                    }

                    metrics.lastSeekPosition = seekPositionSec;

                    Log.i(TAG, String.format("Range streaming: %s - Seek to %.1f sec, size: %.2f KB",
                            holder.getMusicTag().getTitle(),
                            seekPositionSec,
                            contentLength / 1024.0));
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
            SimpleDateFormat dateFormatter = new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss z", Locale.US);
            dateFormatter.setTimeZone(TimeZone.getTimeZone("GMT"));
            response.headers().set(HttpHeaderNames.DATE, dateFormatter.format(new Date()));

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
                if (filePath.endsWith(".m4a")) {
                    // Differentiate between AAC and ALAC
                    if (isALACFile(tag)) {
                        // ALAC uses audio/x-alac or audio/apple-lossless
                        response.headers().set(HttpHeaderNames.CONTENT_TYPE, "audio/apple-lossless");
                    } else {
                        // AAC in M4A container
                        response.headers().set(HttpHeaderNames.CONTENT_TYPE, "audio/aac");
                    }
                } else if (filePath.endsWith(".aiff") || filePath.endsWith(".aif")) {
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
                if (tag.getFileFormat() != null) {
                    response.headers().set("X-Audio-Format", tag.getFileFormat());
                }
                if (tag.getAudioEncoding() != null) {
                    response.headers().set("X-Audio-Codec", tag.getAudioEncoding());
                }

                // Add sample rate and bit depth info for high-res audio clients
                if (profile.supportsHighRes) {
                    if (tag.getAudioSampleRate() > 0) {
                        response.headers().set("X-Audio-Sample-Rate", String.valueOf(tag.getAudioSampleRate()));
                    }
                    if (tag.getAudioBitsDepth() > 0) {
                        response.headers().set("X-Audio-Bit-Depth", String.valueOf(tag.getAudioBitsDepth()));
                    }
                }

                // Add duration metadata if available
                if (tag.getAudioDuration() > 0) {
                    response.headers().set("X-Audio-Duration", String.valueOf(tag.getAudioDuration()));
                }

                // DLNA headers with proper capitalization
                response.headers().set("TransferMode.DLNA.ORG", "Streaming");
                response.headers().set("ContentFeatures.DLNA.ORG", getDLNAContentFeatures(tag));

                // Special header for mConnectHD and jPlay
                if (profile == CLIENT_PROFILES.get("mconnect") || profile == CLIENT_PROFILES.get("jplay")) {
                    // These controllers benefit from direct streaming hint
                    response.headers().set("X-Direct-Streaming", "true");
                }

                // For RopieeeXL and audiophile renderers, add lossless streaming hint
                if (profile == CLIENT_PROFILES.get("ropieee") && isLosslessFormat(tag)) {
                    response.headers().set("X-Lossless-Streaming", "true");
                }

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
            } else if (mimeType.contains("aac") || tag.getPath().endsWith(".aac")) {
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
            errorCounter.incrementAndGet();
            if (ctx.channel().isActive()) {
                sendError(ctx, INTERNAL_SERVER_ERROR);
            }
        }

        private ContentHolder getContent(FullHttpRequest request) {
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
            return getSong(request, contentId);
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

        private ContentHolder getSong(FullHttpRequest request, String contentId) {
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
                PlayerInfo player = PlayerInfo.buildStreamPlayer(agent,
                        ContextCompat.getDrawable(getContext(), R.drawable.img_upnp_white));
                MusixMateApp.getPlayerControl().publishPlayingSong(player, tag);

                // Warm up cache for this file
                warmupCache(tag.getPath());

                // Get MIME type for the file
                String mimeType = MimeTypeUtils.getMimeTypeFromPath(tag.getPath());

                Log.i(TAG, "Serving media: " + tag.getTitle() + " [" + mimeType + "]");
                return new ContentHolder(mimeType, tag.getPath(), tag);
            } catch (Exception e) {
                Log.e(TAG, "Error retrieving song: " + e.getMessage(), e);
                return null;
            }
        }
    }

    /**
     * Adds lastSeekPosition field to track seeking behavior
     */
    private static class StreamMetrics {
        long bytesServed;
        long streamingTimeMs;
        int rebufferEvents;
        int seekEvents;
        long requestCount;
        long lastRequestTime;
        double lastSeekPosition;

        public StreamMetrics() {
            this.lastRequestTime = System.currentTimeMillis();
            this.lastSeekPosition = 0;
        }

        public void recordRequest(long bytes) {
            requestCount++;
            bytesServed += bytes;
            lastRequestTime = System.currentTimeMillis();
        }

        public void recordSeek() {
            seekEvents++;
        }

        public void recordRebuffer() {
            rebufferEvents++;
        }

        // Calculate effective throughput in bytes/sec
        public float getEffectiveThroughput() {
            if (streamingTimeMs == 0) return 0;
            return bytesServed / (streamingTimeMs / 1000.0f);
        }

    }
}
