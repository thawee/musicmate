package apincer.android.mmate.dlna.transport;

import static io.netty.handler.codec.http.HttpResponseStatus.FORBIDDEN;
import static io.netty.handler.codec.http.HttpVersion.HTTP_1_1;

import android.content.Context;
import android.util.Log;
import android.util.LruCache;

import androidx.core.content.ContextCompat;

import org.jupnp.transport.spi.InitializationException;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.net.InetAddress;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.List;

import apincer.android.mmate.MusixMateApp;
import apincer.android.mmate.R;
import apincer.android.mmate.dlna.MediaServerSession;
import apincer.android.mmate.player.PlayerInfo;
import apincer.android.mmate.provider.CoverArtProvider;
import apincer.android.mmate.repository.FFMpegHelper;
import apincer.android.mmate.repository.MusicTag;
import apincer.android.mmate.utils.MediaTypeDetector;
import apincer.android.mmate.utils.StringUtils;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.WriteBufferWaterMark;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.http.DefaultHttpContent;
import io.netty.handler.codec.http.DefaultHttpRequest;
import io.netty.handler.codec.http.DefaultHttpResponse;
import io.netty.handler.codec.http.HttpChunkedInput;
import io.netty.handler.codec.http.HttpContent;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpHeaderValues;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpResponse;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpServerCodec;
import io.netty.handler.codec.http.HttpUtil;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.handler.codec.http.LastHttpContent;
import io.netty.handler.stream.ChunkedFile;
import io.netty.handler.stream.ChunkedStream;
import io.netty.handler.stream.ChunkedWriteHandler;
import io.netty.handler.timeout.IdleStateEvent;
import io.netty.handler.timeout.IdleStateHandler;
import io.netty.util.CharsetUtil;
import okio.Buffer;

public class NettyContentServer {
    private final Context context;
    private static final String TAG = "NettyContentServer";
    private static LruCache<String, Buffer> transCodeCached;
    public static final int SERVER_PORT = 8089;
    public static final String TYPE_IMAGE_PNG = "image/png";
    public static final String TYPE_IMAGE_JPEG = "image/jpeg";

    private final EventLoopGroup bossGroup = new NioEventLoopGroup(1);
    private final EventLoopGroup workerGroup = new NioEventLoopGroup();
    private final File coverartDir;

    public NettyContentServer(Context context) {
        this.context = context;
        coverartDir = context.getExternalCacheDir();
        int cacheSize = 1024 * 1024 * 64; // 64 MB cache
        transCodeCached = new LruCache<>(cacheSize) {
            @Override
            protected int sizeOf(String key, Buffer data) {
                // The cache size will be measured in bytes
                return (int) data.size();
            }
        };
    }

    private Context getContext() {
        return context;
    }

    synchronized public void init(InetAddress bindAddress) throws InitializationException {
        Thread thread = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    Log.d(TAG, "Starting netty content server: " + bindAddress.getHostAddress() + ":" + SERVER_PORT);

                    ServerBootstrap b = new ServerBootstrap();
                    b.childOption(ChannelOption.WRITE_BUFFER_WATER_MARK, new WriteBufferWaterMark(32768, 65536)); // 32 KB low, 64 KB high
                    b.option(ChannelOption.SO_BACKLOG, 128);  //Set the size of the backlog of TCP connections.  The default and exact meaning of this parameter is JDK specific.
                    b.group(bossGroup, workerGroup)
                            .channel(NioServerSocketChannel.class)
                            .option(ChannelOption.SO_REUSEADDR, true)
                            .option(ChannelOption.TCP_NODELAY, true) // true - great for low latency
                            .option(ChannelOption.SO_KEEPALIVE, true)
                            .option(ChannelOption.SO_BACKLOG, 128)
                            .option(ChannelOption.SO_RCVBUF, 8192) // 8 KB
                            .option(ChannelOption.SO_SNDBUF, 65536) // 64 KB
                            .childOption(ChannelOption.WRITE_BUFFER_WATER_MARK, new WriteBufferWaterMark(32768, 65536)) // 32 KB low, 64 KB high
                            .option(ChannelOption.SO_TIMEOUT, 3000)
                            .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 3000)
                            .childHandler(new ContentServerInitializer());
                    Channel ch = b.bind(SERVER_PORT).sync().channel();
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

    synchronized public void stop() {
        Log.d(TAG, "Shutting down netty content server");
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
    }

    private byte[] getDefaultIcon(String albumId) {
        if(albumId.contains(".")) {
            albumId = albumId.substring(0, albumId.indexOf("."));
        }
        MusicTag tag = MusixMateApp.getInstance().getOrmLite().findByAlbumUniqueKey(albumId);
        return MusixMateApp.getInstance().getDefaultNoCoverart(tag);
    }

    public class ContentServerInitializer extends ChannelInitializer<SocketChannel> {
        @Override
        protected void initChannel(SocketChannel ch) {
            ChannelPipeline pipeline = ch.pipeline();
            pipeline.addLast(new HttpServerCodec());
            pipeline.addLast(new ChunkedWriteHandler());
            pipeline.addLast(new IdleStateHandler(60, 30, 0)); // 60 seconds read idle, 30 seconds write idle
            pipeline.addLast(new ContentServerHandler());
        }
    }

    /**
     * ValueHolder for media content.
     */
    static class ContentHolder {
        private final String contentType;
        private final String filePath;
        private final byte[] content;

        public ContentHolder(String contentType, String filePath) {
            this.filePath = filePath;
            this.contentType = contentType;
            this.content = null;
        }

        public ContentHolder(String contentType, byte[] content) {
            this.filePath = null;
            this.content = content;
            this.contentType = contentType;
        }
    }

    private class ContentServerHandler extends SimpleChannelInboundHandler<DefaultHttpRequest> {
        @Override
        protected void channelRead0(ChannelHandlerContext ctx, DefaultHttpRequest request) throws Exception {
            ContentHolder holder = getContent(request);
            final boolean keepAlive = HttpUtil.isKeepAlive(request);
            if(holder!=null && holder.content != null) {
                HttpResponse response = new DefaultHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK);
                response.headers().set(HttpHeaderNames.CONTENT_TYPE, holder.contentType);
                HttpUtil.setContentLength(response, holder.content.length);
                if (keepAlive) {
                    response.headers().set(HttpHeaderNames.CONNECTION, HttpHeaderValues.KEEP_ALIVE);
                }
                ctx.write(response);
                ctx.write(new HttpChunkedInput(new ChunkedStream(new ByteArrayInputStream(holder.content))));
                ctx.writeAndFlush(LastHttpContent.EMPTY_LAST_CONTENT).addListener(ChannelFutureListener.CLOSE_ON_FAILURE);
            }else if (holder!=null && holder.filePath != null) {
                File file = new File(holder.filePath);
                HttpResponse response = new DefaultHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK);
                response.headers().set(HttpHeaderNames.CONTENT_TYPE, holder.contentType);
                HttpUtil.setContentLength(response, file.length());
                if (keepAlive) {
                    response.headers().set(HttpHeaderNames.CONNECTION, HttpHeaderValues.KEEP_ALIVE);
                }
                ctx.write(response);
                ctx.write(new HttpChunkedInput(new ChunkedFile(file)));
                ctx.writeAndFlush(LastHttpContent.EMPTY_LAST_CONTENT).addListener(ChannelFutureListener.CLOSE_ON_FAILURE);
            }else {
                HttpResponse response = new DefaultHttpResponse(HTTP_1_1, FORBIDDEN);
                response.headers().set(HttpHeaderNames.CONTENT_TYPE, "text/plain; charset=UTF-8");
                ByteBuf content = Unpooled.copiedBuffer("Failure: " + FORBIDDEN + "\r\n", CharsetUtil.UTF_8);
                HttpUtil.setContentLength(response, content.readableBytes());
                HttpContent httpContent = new DefaultHttpContent(content);
                if (keepAlive) {
                    response.headers().set(HttpHeaderNames.CONNECTION, HttpHeaderValues.KEEP_ALIVE);
                }
                // Write the response headers
                ctx.write(response);
                // Write the response body
                ctx.write(httpContent);
                ctx.writeAndFlush(LastHttpContent.EMPTY_LAST_CONTENT).addListener(ChannelFutureListener.CLOSE_ON_FAILURE);
            }
        }

        @Override
        public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
            if (evt instanceof IdleStateEvent) {
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
            ctx.close();
            Log.w(TAG, "exceptionCaught: "+cause.getMessage());
        }

        private ContentHolder getContent(DefaultHttpRequest request) {
            if (!request.method().equals(HttpMethod.GET) && !request.method().equals(HttpMethod.HEAD)) {
                Log.d(TAG,
                        "HTTP request isn't GET or HEAD stop! Method was: "
                                + request.method());
                return null;
            }

            String requestUri = request.uri();
            if(requestUri.startsWith("/")) {
                requestUri = requestUri.substring(1);
            }
            List<String> pathSegments = Arrays.asList(requestUri.split("/", -1));
            if (pathSegments.size() < 2 || pathSegments.size() > 3) {
                Log.d(TAG,
                        "HTTP request is invalid: "+ requestUri);
                return null;
            }
            String type = pathSegments.get(0);
            if ("album".equals(type)) {
                String albumId = pathSegments.get(1);
                return getAlbumArt(albumId);
            } else if ("res".equals(type)) {
                String contentId = pathSegments.get(1);
                return getSong(request, contentId);
            }
            return null;
        }

        private ContentHolder getSong(DefaultHttpRequest request, String contentId) {
            MusicTag tag = MusixMateApp.getInstance().getOrmLite().findById(StringUtils.toLong(contentId));
            if (tag != null) {
                String agent = request.headers().get(HttpHeaderNames.USER_AGENT);
                PlayerInfo player = PlayerInfo.buildStreamPlayer(agent, ContextCompat.getDrawable(getContext(), R.drawable.img_upnp));
                MusixMateApp.getPlayerControl().publishPlayingSong(player, tag);
                if(MediaServerSession.isTransCoded(tag)) {
                    // transcode to lpcm before send to
                    Buffer buff = transCodeCached.get(contentId);
                    if(buff == null) {
                        Buffer data = FFMpegHelper.transcodeFile(context, tag.getPath());
                        transCodeCached.put(contentId, data);
                        return new ContentHolder(MediaTypeDetector.getContentType(tag), data.readByteArray());
                    }else {
                        return new ContentHolder(MediaTypeDetector.getContentType(tag), buff.readByteArray());
                    }
                }else {
                    return new ContentHolder(MediaTypeDetector.getContentType(tag), tag.getPath());
                }
            }
            return null;
        }

        private ContentHolder getAlbumArt(String albumId) {
            try {
                String path = CoverArtProvider.COVER_ARTS + albumId;
                File pathFile = new File(coverartDir, path);
                if (pathFile.exists()) {
                    return new ContentHolder(TYPE_IMAGE_PNG, pathFile.getAbsolutePath());
                }
            } catch (Exception e) {
                Log.e(TAG, "lookupAlbumArt: - not found " + albumId, e);
            }

            return new ContentHolder(TYPE_IMAGE_JPEG, getDefaultIcon(albumId));
        }
    }
}