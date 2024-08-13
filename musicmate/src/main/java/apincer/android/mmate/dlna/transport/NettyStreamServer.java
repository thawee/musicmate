package apincer.android.mmate.dlna.transport;

import static io.netty.handler.codec.http.HttpResponseStatus.FORBIDDEN;
import static io.netty.handler.codec.http.HttpResponseStatus.NOT_FOUND;
import static io.netty.handler.codec.http.HttpResponseStatus.NOT_MODIFIED;
import static io.netty.handler.codec.http.HttpResponseStatus.OK;
import static io.netty.handler.codec.http.HttpVersion.HTTP_1_0;
import static io.netty.handler.codec.http.HttpVersion.HTTP_1_1;

import android.content.Context;
import android.util.Log;

import androidx.core.content.ContextCompat;

import org.apache.commons.io.IOUtils;
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
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.net.InetAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;

import apincer.android.mmate.MusixMateApp;
import apincer.android.mmate.R;
import apincer.android.mmate.broadcast.AudioTagPlayingEvent;
import apincer.android.mmate.dlna.MediaServerSession;
import apincer.android.mmate.dlna.MediaTypeDetector;
import apincer.android.mmate.player.PlayerInfo;
import apincer.android.mmate.provider.CoverArtProvider;
import apincer.android.mmate.repository.MusicTag;
import apincer.android.mmate.utils.ApplicationUtils;
import apincer.android.mmate.utils.StringUtils;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.ByteBuf;
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
import io.netty.channel.ChannelProgressiveFuture;
import io.netty.channel.ChannelProgressiveFutureListener;
import io.netty.channel.DefaultFileRegion;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.DefaultHttpResponse;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpHeaderValues;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpResponse;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpServerCodec;
import io.netty.handler.codec.http.HttpUtil;
import io.netty.handler.codec.http.LastHttpContent;
import io.netty.handler.logging.LogLevel;
import io.netty.handler.logging.LoggingHandler;
import io.netty.handler.stream.ChunkedWriteHandler;
import io.netty.util.AsciiString;
import io.netty.util.CharsetUtil;

public class NettyStreamServer implements StreamServer<StreamServerConfigurationImpl> {
    private final Context context;
    private static final String TAG = "NettyStreamServer";
    private final List<byte[]> cachedIconRAWs = new ArrayList<>();
    private int currentIconIndex = 0;
    final protected StreamServerConfigurationImpl configuration;
    protected int localPort;
    private final EventLoopGroup bossGroup = new NioEventLoopGroup(1);
    private final EventLoopGroup workerGroup = new NioEventLoopGroup();

    public static final String HTTP_DATE_FORMAT = "EEE, dd MMM yyyy HH:mm:ss zzz";
    public static final String HTTP_DATE_GMT_TIMEZONE = "GMT";
    public static final int HTTP_CACHE_SECONDS = 60;

    public void loadCachedIcons() {
        // defaultIconRAWs.add(readDefaultCover("no_cover1.jpg"));
        // defaultIconRAWs.add(readDefaultCover("no_cover2.jpg"));
        cachedIconRAWs.add(readDefaultCover("no_cover3.jpg"));
        cachedIconRAWs.add(readDefaultCover("no_cover4.jpg"));
        cachedIconRAWs.add(readDefaultCover("no_cover5.jpg"));
        cachedIconRAWs.add(readDefaultCover("no_cover6.jpg"));
        cachedIconRAWs.add(readDefaultCover("no_cover7.jpg"));
    }

    public NettyStreamServer(Context context, StreamServerConfigurationImpl configuration) {
        this.context = context;
        this.configuration = configuration;
        this.localPort = configuration.getListenPort();
        loadCachedIcons();
    }

    public StreamServerConfigurationImpl getConfiguration() {
        return configuration;
    }

    private Context getContext() {
        return context;
    }

    synchronized public void init(InetAddress bindAddress, final Router router) throws InitializationException {

        Thread thread = new Thread(new Runnable() {

            @Override
            public void run() {
                try {
                    Log.i(TAG, "Adding netty4 stream server: " + bindAddress.getHostAddress() + ":" + getConfiguration().getListenPort());

                    MediaServerSession.streamServerHost = bindAddress.getHostAddress();
                    ServerBootstrap b = new ServerBootstrap();
                    b.option(ChannelOption.SO_BACKLOG, 128);  //Set the size of the backlog of TCP connections.  The default and exact meaning of this parameter is JDK specific.
                    b.group(bossGroup, workerGroup)
                            .channel(NioServerSocketChannel.class)
                            .handler(new LoggingHandler(LogLevel.DEBUG))
                            .option(ChannelOption.SO_REUSEADDR, true)
                            .option(ChannelOption.TCP_NODELAY, false) //great for latency
                            .option(ChannelOption.SO_KEEPALIVE, true)
                            .option(ChannelOption.SO_BACKLOG, 128)
                            .option(ChannelOption.SO_RCVBUF, 8192)
                            .option(ChannelOption.SO_SNDBUF, 8192)
                            .option(ChannelOption.SO_TIMEOUT, 5000)
                            .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 5000)
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
        bossGroup.shutdownGracefully();
        workerGroup.shutdownGracefully();
    }

    private byte[] getDefaultIcon() {
        currentIconIndex++;
        if(currentIconIndex >= cachedIconRAWs.size()) currentIconIndex = 0;
        return cachedIconRAWs.get(currentIconIndex);
    }

    private byte[] readDefaultCover(String file) {
        InputStream in = ApplicationUtils.getAssetsAsStream(getContext(), file);
        try {
            return IOUtils.toByteArray(in);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void run() {

    }

    @Sharable
    private class HttpServerHandler extends ChannelInboundHandlerAdapter {
       final String upnpPath;
       final UpnpStream upnpStream;
       static final SimpleDateFormat dateFormatter = new SimpleDateFormat(HTTP_DATE_FORMAT, Locale.US);

        public HttpServerHandler(ProtocolFactory protocolFactory, String path) {
            super();

            dateFormatter.setTimeZone(TimeZone.getTimeZone(HTTP_DATE_GMT_TIMEZONE));
            upnpPath = path;
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
            Log.w(TAG, cause.getMessage());
        }

        private void sendError(ChannelHandlerContext ctx, FullHttpRequest request, HttpResponseStatus status) {
            FullHttpResponse response = new DefaultFullHttpResponse(
                    HTTP_1_1, status, Unpooled.copiedBuffer("Failure: " + status + "\r\n", CharsetUtil.UTF_8));
            response.headers().set(HttpHeaderNames.CONTENT_TYPE, "text/plain; charset=UTF-8");

            sendAndCleanupConnection(ctx, request, response);
        }

        /**
         * When file timestamp is the same as what the browser is sending up, send a "304 Not Modified"
         *
         * @param ctx
         *            Context
         */
        private void sendNotModified(ChannelHandlerContext ctx, FullHttpRequest request) {
            FullHttpResponse response = new DefaultFullHttpResponse(HTTP_1_1, NOT_MODIFIED, Unpooled.EMPTY_BUFFER);
            setDateHeader(response);

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

        /**
         * Sets the Date and Cache headers for the HTTP Response
         *
         * @param response
         *            HTTP response
         * @param fileToCache
         *            file to extract content type
         */
        private static void setDateAndCacheHeaders(HttpResponse response, File fileToCache) {

            // Date header
            Calendar time = new GregorianCalendar();
            response.headers().set(HttpHeaderNames.DATE, dateFormatter.format(time.getTime()));

            // Add cache headers
            time.add(Calendar.SECOND, HTTP_CACHE_SECONDS);
            response.headers().set(HttpHeaderNames.EXPIRES, dateFormatter.format(time.getTime()));
            response.headers().set(HttpHeaderNames.CACHE_CONTROL, "private, max-age=" + HTTP_CACHE_SECONDS);
            if(fileToCache != null) {
                response.headers().set(
                        HttpHeaderNames.LAST_MODIFIED, dateFormatter.format(new Date(fileToCache.lastModified())));
            }
        }

        /**
         * Sets the content type header for the HTTP Response
         *
         * @param response
         *            HTTP response
         * @param file
         *            file to extract content type
         */
        private static void setContentTypeHeader(HttpResponse response, File file) {
            response.headers().set(HttpHeaderNames.CONTENT_TYPE, MediaTypeDetector.getContentType(file.getPath()));
        }

        /**
         * If Keep-Alive is disabled, attaches "Connection: close" header to the response
         * and closes the connection after the response being sent.
         */
        private void sendAndCleanupConnection(ChannelHandlerContext ctx, FullHttpRequest request, FullHttpResponse response) {
            final boolean keepAlive = HttpUtil.isKeepAlive(request);
            HttpUtil.setContentLength(response, response.content().readableBytes());
            if (!keepAlive) {
                // We're going to close the connection as soon as the response is sent,
                // so we should also make it clear for the client.
                response.headers().set(HttpHeaderNames.CONNECTION, HttpHeaderValues.CLOSE);
            } else if (request.protocolVersion().equals(HTTP_1_0)) {
                response.headers().set(HttpHeaderNames.CONNECTION, HttpHeaderValues.KEEP_ALIVE);
            }

            ChannelFuture flushPromise = ctx.writeAndFlush(response);

            if (!keepAlive) {
                // Close the connection as soon as the response is sent.
                flushPromise.addListener(ChannelFutureListener.CLOSE);
            }
        }

        @Override
        public void channelRead( final ChannelHandlerContext ctx, Object msg) throws Exception {
            if (msg instanceof FullHttpRequest request) {
                final ByteBuf buf = request.content();
                byte[] data = new byte[buf.readableBytes()];
                buf.readBytes(data);
                buf.release();

                String uri = request.uri();
                ContentHolder contentHolder;
                if (uri.startsWith(upnpPath)) {
                    contentHolder = handleUPnpStream(request, data);
                } else {
                    contentHolder = handleResource(request);
                }
                boolean keepAlive = HttpUtil.isKeepAlive(request);
                if (contentHolder == null) {
                    sendError(ctx, request, FORBIDDEN);
                   /* DefaultFullHttpResponse response = new DefaultFullHttpResponse(request.protocolVersion(), HttpResponseStatus.FORBIDDEN);
                    response.headers().set(HttpHeaderNames.CONTENT_TYPE, HttpHeaderValues.TEXT_PLAIN);
                    response.headers().add(HttpHeaderNames.DATE, "" + System.currentTimeMillis());
                    response.headers().set(HttpHeaderNames.CONTENT_LENGTH, response.content().readableBytes());
                    ctx.write(response, ctx.voidPromise()).addListener(ChannelFutureListener.CLOSE);*/
                   // ctx.close();
                }else if (contentHolder.fileContent != null) {
                    handleFile(ctx, request, contentHolder);

                    /*
                    DefaultHttpResponse response = new DefaultHttpResponse(request.protocolVersion(), contentHolder.statusCode);
                    if (keepAlive) {
                        response.headers().set(HttpHeaderNames.CONNECTION, HttpHeaderValues.CLOSE);
                    } else {
                        response.headers().set(HttpHeaderNames.CONNECTION, HttpHeaderValues.KEEP_ALIVE);
                    }
                    // The Date header is recommended in UDA
                    response.headers().add(HttpHeaderNames.DATE, "" + System.currentTimeMillis());

                    response.headers().set(HttpHeaderNames.CONTENT_LENGTH, contentHolder.fileContent.length());
                   // response. headers().set(TRANSFER_ENCODING, CHUNKED);
                    ctx. write(response);

                    HttpChunkedInput httpChunkWriter = new HttpChunkedInput(
                            new ChunkedFile(contentHolder.fileContent));
                    final ChannelFuture f = ctx. writeAndFlush(httpChunkWriter);
                    f.addListener((ChannelFutureListener) future -> {
                        ctx.close();
                    }); */
                } else {
                    DefaultFullHttpResponse response = new DefaultFullHttpResponse(request.protocolVersion(), contentHolder.statusCode, Unpooled.wrappedBuffer(contentHolder.content));
                    response.headers().set(HttpHeaderNames.CONTENT_LENGTH, response.content().readableBytes());
                    response.headers().set(HttpHeaderNames.CONTENT_TYPE, contentHolder.contentType); // "text/plain; charset=UTF-8");

                    sendAndCleanupConnection(ctx, request, response);
                   /*
                    if (!keepAlive) {
                        // The Date header is recommended in UDA
                        response.headers().add(HttpHeaderNames.DATE, "" + System.currentTimeMillis());
                        response.headers().set(HttpHeaderNames.CONTENT_LENGTH, response.content().readableBytes());
                        ctx.write(response, ctx.voidPromise()).addListener(ChannelFutureListener.CLOSE);
                    } else {
                        // The Date header is recommended in UDA
                        response.headers().add(HttpHeaderNames.DATE, "" + System.currentTimeMillis());
                        response.headers().set(HttpHeaderNames.CONTENT_LENGTH, response.content().readableBytes());
                        response.headers().set(HttpHeaderNames.CONNECTION, HttpHeaderValues.KEEP_ALIVE);
                        ctx.write(response, ctx.voidPromise());
                    } */
                }
            }
        }

        private void handleFile(final ChannelHandlerContext ctx, final FullHttpRequest request, final ContentHolder contentHolder) throws Exception {
            final boolean keepAlive = HttpUtil.isKeepAlive(request);

            // Cache Validation
            String ifModifiedSince = request.headers().get(HttpHeaderNames.IF_MODIFIED_SINCE);
            if (ifModifiedSince != null && !ifModifiedSince.isEmpty()) {
                SimpleDateFormat dateFormatter = new SimpleDateFormat(HTTP_DATE_FORMAT, Locale.US);
                Date ifModifiedSinceDate = dateFormatter.parse(ifModifiedSince);

                // Only compare up to the second because the datetime format we send to the client
                // does not have milliseconds
                long ifModifiedSinceDateSeconds = ifModifiedSinceDate.getTime() / 1000;
                long fileLastModifiedSeconds = contentHolder.fileContent.lastModified() / 1000;
                if (ifModifiedSinceDateSeconds == fileLastModifiedSeconds) {
                    sendNotModified(ctx, request);
                    return;
                }
            }

            RandomAccessFile raf;
            try {
                raf = new RandomAccessFile(contentHolder.fileContent, "r");
            } catch (FileNotFoundException ignore) {
                sendError(ctx, request, NOT_FOUND);
                return;
            }
            long fileLength = raf.length();

            HttpResponse response = new DefaultHttpResponse(HTTP_1_1, OK);
            HttpUtil.setContentLength(response, fileLength);
            setContentTypeHeader(response, contentHolder.fileContent);
            setDateAndCacheHeaders(response, contentHolder.fileContent);

            if (!keepAlive) {
                response.headers().set(HttpHeaderNames.CONNECTION, HttpHeaderValues.CLOSE);
            } else if (request.protocolVersion().equals(HTTP_1_0)) {
                response.headers().set(HttpHeaderNames.CONNECTION, HttpHeaderValues.KEEP_ALIVE);
            }

            // Write the initial line and the header.
            ctx.write(response);

            // Write the content.
                ChannelFuture sendFileFuture =
                        ctx.write(new DefaultFileRegion(raf.getChannel(), 0, fileLength), ctx.newProgressivePromise());
                // Write the end marker.
                ChannelFuture lastContentFuture = ctx.writeAndFlush(LastHttpContent.EMPTY_LAST_CONTENT);

            sendFileFuture.addListener(new ChannelProgressiveFutureListener() {
                @Override
                public void operationProgressed(ChannelProgressiveFuture future, long progress, long total) {
                    if (total < 0) { // total unknown
                        System.err.println(future.channel() + " Transfer progress: " + progress);
                    } else {
                        System.err.println(future.channel() + " Transfer progress: " + progress + " / " + total);
                    }
                }

                @Override
                public void operationComplete(ChannelProgressiveFuture future) {
                    System.err.println(future.channel() + " Transfer complete.");
                }
            });

            // Decide whether to close the connection or not.
            if (!keepAlive) {
                // Close the connection when the whole content is written out.
                lastContentFuture.addListener(ChannelFutureListener.CLOSE);
            }
        }

        private ContentHolder handleResource(FullHttpRequest request) throws IOException {
            ContentHolder holder;
            if (!request.method().equals(HttpMethod.GET) && !request.method().equals(HttpMethod.HEAD)) {
                Log.d(TAG,
                        "HTTP request isn't GET or HEAD stop! Method was: "
                                + request.method());
                holder = new ContentHolder(HttpHeaderValues.TEXT_PLAIN, FORBIDDEN, "Access Denied");
                return holder;
            }

            String requestUri = request.uri();
            if(requestUri.startsWith("/")) {
                requestUri = requestUri.substring(1);
            }
            List<String> pathSegments = Arrays.asList(requestUri.split("/", -1));
            if (pathSegments.size() < 2 || pathSegments.size() > 3) {
                holder = new ContentHolder(HttpHeaderValues.TEXT_PLAIN, FORBIDDEN, "Access Denied");
                return holder;
            }
            String type = pathSegments.get(0);
            if ("album".equals(type)) {
                String albumId = pathSegments.get(1);
                return handleResAlbumArt(request, albumId);
            } else if ("res".equals(type)) {
                String contentId = pathSegments.get(1);
                return handleResSong(request, contentId);
            }else {
                Log.d(TAG, "Access uri '"+requestUri+"' denied");
                holder = new ContentHolder(HttpHeaderValues.TEXT_PLAIN, FORBIDDEN, "Access Denied");
                return holder;
            }
        }

        private ContentHolder handleResSong(FullHttpRequest request, String contentId) {
                MusicTag tag = MusixMateApp.getInstance().getOrmLite().findById(StringUtils.toLong(contentId));
                if (tag != null) {
                    String agent = request.headers().get(HttpHeaderNames.USER_AGENT);
                    PlayerInfo player = PlayerInfo.buildStreamPlayer(agent, ContextCompat.getDrawable(getContext(), R.drawable.img_upnp));
                    MusixMateApp.getPlayerControl().setPlayingSong(player, tag);
                    AudioTagPlayingEvent.publishPlayingSong(tag);

                    return new ContentHolder(new AsciiString(MediaTypeDetector.getContentType(tag.getPath())), OK, new File(tag.getPath()));
                }else {
                    return new ContentHolder(HttpHeaderValues.TEXT_PLAIN, NOT_FOUND, contentId+" Not found");
                }
        }

        private ContentHolder handleResAlbumArt(FullHttpRequest request, String albumId) throws IOException {
            ContentHolder holder;
                String path = CoverArtProvider.COVER_ARTS + albumId;
                File dir = getContext().getExternalCacheDir();
                File pathFile = new File(dir, path);
                if (pathFile.exists()) {
                    byte[] bodyBytes = IOUtils.toByteArray(new FileInputStream(pathFile));
                    holder = new ContentHolder(AsciiString.of(MediaTypeDetector.getContentType(pathFile.getName())), OK, bodyBytes);
                } else {
                    // Log.d(TAG, "Send default albumArt for " + albumId);
                    holder = new ContentHolder(new AsciiString("image/*"), OK, getDefaultIcon());
                }
                return holder;
        }

        private  ContentHolder handleUPnpStream(FullHttpRequest request, byte[] bodyBytes) {
            ContentHolder holder;
            try {
                StreamRequestMessage requestMessage = readRequestMessage(request, bodyBytes);
                // Log.v(TAG, "Processing new request message: " + requestMessage);

                String userAgent = getUserAgent(requestMessage);
                if("CyberGarage-HTTP/1.0".equals(userAgent)) { // ||
                    // "Panasonic iOS VR-CP UPnP/2.0".equals(userAgent)) {//     requestMessage.getHeaders().getFirstHeader("User-agent"))) {
                    Log.w(TAG, "Interim FIX for MConnect on IPadOS 18 beta, return all songs for MConnect(fix show only 20 songs)");
                    MediaServerSession.forceFullContent = true;
                }

                StreamResponseMessage responseMessage = upnpStream.process(requestMessage);

                if (responseMessage != null) {
                    // Log.v(TAG, "Preparing HTTP response message: " + responseMessage);
                    holder = buildResponseMessage(responseMessage);
                } else {
                    // If it's null, it's 404
                    holder = new ContentHolder(HttpHeaderValues.TEXT_PLAIN, NOT_FOUND, "Not Found");
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

            } else {
                Log.v(TAG, "Request did not contain entity body");
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
                Log.v(TAG, "Response message has body, writing bytes to stream...");
                // Log.d(TAG, "Response message has body, "+new String(responseBodyBytes));
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
        private final Router router;
        public HttpServerInitializer(Router router) {
            this.router = router;
        }

        @Override
        public void initChannel(SocketChannel ch) {
            ChannelPipeline p = ch.pipeline();
            p.addLast(new HttpServerCodec());
            p.addLast("aggregator", new HttpObjectAggregator(Integer.MAX_VALUE));
            p.addLast("streamer", new ChunkedWriteHandler());// added
            p.addLast(new HttpServerHandler(router.getProtocolFactory(), router.getConfiguration().getNamespace().getBasePath().getPath()));
        }
    }

    static class ContentHolder {
        private final AsciiString contentType;
        private final byte[] content;
        private final HttpResponseStatus statusCode;
        private File fileContent;
        private final Map<String, String> headers = new HashMap<>();

        public ContentHolder(AsciiString mimeType, HttpResponseStatus statusCode, File fileContent) {
            this.fileContent = fileContent;
            this.statusCode = statusCode;
            this.contentType = mimeType;
            this.content = new byte[]{};
        }

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
