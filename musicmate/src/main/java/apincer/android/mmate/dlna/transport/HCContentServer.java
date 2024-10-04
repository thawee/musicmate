package apincer.android.mmate.dlna.transport;

import static apincer.android.mmate.utils.StringUtils.isEmpty;

import android.content.Context;
import android.net.Uri;
import android.util.Log;
import android.util.LruCache;

import androidx.core.content.ContextCompat;

import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.EntityDetails;
import org.apache.hc.core5.http.HttpException;
import org.apache.hc.core5.http.HttpRequest;
import org.apache.hc.core5.http.HttpStatus;
import org.apache.hc.core5.http.Message;
import org.apache.hc.core5.http.MethodNotSupportedException;
import org.apache.hc.core5.http.URIScheme;
import org.apache.hc.core5.http.impl.bootstrap.HttpAsyncServer;
import org.apache.hc.core5.http.nio.AsyncEntityProducer;
import org.apache.hc.core5.http.nio.AsyncRequestConsumer;
import org.apache.hc.core5.http.nio.AsyncServerRequestHandler;
import org.apache.hc.core5.http.nio.entity.AsyncEntityProducers;
import org.apache.hc.core5.http.nio.entity.BasicAsyncEntityConsumer;
import org.apache.hc.core5.http.nio.support.AsyncResponseBuilder;
import org.apache.hc.core5.http.nio.support.BasicRequestConsumer;
import org.apache.hc.core5.http.protocol.HttpContext;
import org.apache.hc.core5.http2.impl.nio.bootstrap.H2ServerBootstrap;
import org.apache.hc.core5.reactor.IOReactorConfig;
import org.apache.hc.core5.util.TimeValue;
import org.apache.hc.core5.util.Timeout;
import org.jupnp.transport.spi.InitializationException;

import java.io.File;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.List;
import java.util.Locale;

import apincer.android.mmate.MusixMateApp;
import apincer.android.mmate.R;
import apincer.android.mmate.dlna.MediaServerSession;
import apincer.android.mmate.player.PlayerInfo;
import apincer.android.mmate.provider.CoverArtProvider;
import apincer.android.mmate.repository.FFMpegHelper;
import apincer.android.mmate.repository.MusicTag;
import apincer.android.mmate.utils.MusicTagUtils;
import apincer.android.mmate.utils.StringUtils;
import okio.Buffer;

public class HCContentServer  {
    private final Context context;
    private static final String TAG = "HCContentServer";

    private HttpAsyncServer server;
    private static LruCache<String, Buffer> transCodeCached;
    public static final int SERVER_PORT = 8089;

    public HCContentServer(Context context) {
        this.context = context;
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
                    Log.d(TAG, "Starting HttpCore5 content server: " + bindAddress.getHostAddress() + ":" + SERVER_PORT);

                    MediaServerSession.streamServerHost = bindAddress.getHostAddress();
                    IOReactorConfig config = IOReactorConfig.custom()
                            //.setIoThreadCount(Runtime.getRuntime().availableProcessors())
                            .setSoTimeout(Timeout.ofSeconds(30))
                            .setTcpNoDelay(true) //to reduce latency
                            .setSoKeepAlive(true)
                            .setSelectInterval(TimeValue.ofMilliseconds(1000))
                           // .setSndBufSize(8388608) // 8388608; // 8 MB for file 10 - 100 MB
                            .setSndBufSize(16384) // 16 KB receive buffer
                            .setRcvBufSize(8192)
                            .setSoReuseAddress(true)
                            .build();

                    server = H2ServerBootstrap.bootstrap()
                           /* .setExceptionCallback(e -> Log.w(TAG, "Exception: "+e.getMessage()))
                            .setStreamListener(new Http1StreamListener() {

                                @Override
                                public void onRequestHead(final HttpConnection connection, final HttpRequest request) {
                                    System.out.println(connection.getRemoteAddress() + " " + new RequestLine(request) +" "+ request.getLastHeader("User-Agent"));
                                }

                                @Override
                                public void onResponseHead(final HttpConnection connection, final HttpResponse response) {
                                    System.out.println(connection.getRemoteAddress() + " " + new StatusLine(response) +" Content-Type: "+ response.getLastHeader("Content-Type").getValue());
                                }

                                @Override
                                public void onExchangeComplete(final HttpConnection connection, final boolean keepAlive) {
                                    if (keepAlive) {
                                        System.out.println(connection.getRemoteAddress() + " exchange completed (connection kept alive)");
                                    } else {
                                        System.out.println(connection.getRemoteAddress() + " exchange completed (connection closed)");
                                    }
                                }

                            }) */
                            .setCanonicalHostName(bindAddress.getHostAddress())
                            .setIOReactorConfig(config)
                            .register("/*", new ResourceHandler())
                            .create();
                    server.listen(new InetSocketAddress(SERVER_PORT), URIScheme.HTTP);
                    server.start();
                } catch (Exception ex) {
                    throw new InitializationException("Could not initialize " + getClass().getSimpleName() + ": " + ex, ex);
                }
            }
        });

        thread.start();

    }

    synchronized public void stop() {
        if(server != null) {
            Log.d(TAG, "Shutting down httpcore5 content server");
            server.initiateShutdown();
            try {
                server.awaitShutdown(TimeValue.ofSeconds(3));
            } catch (InterruptedException e) {
                Log.d(TAG, "got exception on content server stop ", e);
            }
        }
    }

    private byte[] getDefaultIcon(String albumId) {
        if(albumId.contains(".")) {
            albumId = albumId.substring(0, albumId.indexOf("."));
        }
        MusicTag tag = MusixMateApp.getInstance().getOrmLite().findByAlbumUniqueKey(albumId);
        return MusixMateApp.getInstance().getDefaultNoCoverart(tag);
    }

    private class ResourceHandler implements AsyncServerRequestHandler<Message<HttpRequest, byte[]>> {
        private final File coverartDir;
        public ResourceHandler() {
            coverartDir = getContext().getExternalCacheDir();
        }

        @Override
        public AsyncRequestConsumer<Message<HttpRequest, byte[]>> prepare(HttpRequest request, EntityDetails entityDetails, HttpContext context) {
            return new BasicRequestConsumer<>(entityDetails != null ? new BasicAsyncEntityConsumer() : null);
        }

        @Override
        public void handle(Message<HttpRequest, byte[]> request, ResponseTrigger responseTrigger, HttpContext context) throws IOException, HttpException {
            final AsyncResponseBuilder responseBuilder = AsyncResponseBuilder.create(HttpStatus.SC_OK);
            // Extract what we need from the HTTP httpRequest
            String requestMethod = request.getHead().getMethod()
                    .toUpperCase(Locale.ENGLISH);
            String userAgent = request.getHead().getLastHeader("User-Agent").getValue();
           // Log.d(TAG, "HTTP Request: "
            //        + request.getHead().getRequestUri()+" - "
            //        + userAgent);
            // Only accept HTTP-GET
            if (!requestMethod.equals("GET") && !requestMethod.equals("HEAD")) {
                Log.d(TAG,
                        "HTTP request isn't GET or HEAD stop! Method was: "
                                + requestMethod);
                throw new MethodNotSupportedException(requestMethod
                        + " method not supported");
            }

            Uri requestUri = Uri.parse(request.getHead().getRequestUri());
            List<String> pathSegments = requestUri.getPathSegments();
            if (pathSegments.size() < 2 || pathSegments.size() > 3) {
                responseBuilder.setStatus(HttpStatus.SC_FORBIDDEN);
                responseBuilder.setEntity(AsyncEntityProducers.create("<html><body><h1>Access denied</h1></body></html>", ContentType.TEXT_HTML));
                responseTrigger.submitResponse(responseBuilder.build(), context);
                return;
            }
            String type = pathSegments.get(0);
            String albumId = "";
            String contentId = "";
            if ("album".equals(type)) {
                albumId = pathSegments.get(1);
                if (isEmpty(albumId)) {
                    responseBuilder.setStatus(HttpStatus.SC_FORBIDDEN);
                    responseBuilder.setEntity(AsyncEntityProducers.create("<html><body><h1>Access denied</h1></body></html>", ContentType.TEXT_HTML));
                    responseTrigger.submitResponse(responseBuilder.build(), context);
                   // Log.d(getClass().getName(), "end doService: Access denied");
                }else {
                    ContentHolder contentHolder = lookupAlbumArt(albumId);
                    responseBuilder.setStatus(HttpStatus.SC_OK);
                    responseBuilder.setEntity(contentHolder.getEntityProducer(getContext()));
                    responseTrigger.submitResponse(responseBuilder.build(), context);
                }
            } else if ("res".equals(type)) {
                contentId = pathSegments.get(1);
                try {
                   // Long.parseLong(contentId);
                    ContentHolder contentHolder = lookupContent(contentId, userAgent);
                    responseBuilder.setStatus(HttpStatus.SC_OK);
                    responseBuilder.setEntity(contentHolder.getEntityProducer(getContext()));
                    responseTrigger.submitResponse(responseBuilder.build(), context);
                } catch (Exception nex) {
                    responseBuilder.setStatus(HttpStatus.SC_NOT_FOUND);
                    responseBuilder.setEntity(AsyncEntityProducers.create("<html><body><h1>File not found</h1></body></html>", ContentType.TEXT_HTML));
                    responseTrigger.submitResponse(responseBuilder.build(), context);
                }
            }else {
                // tricky but works
                Log.d(TAG, "Resource with id " + contentId
                        + albumId  + pathSegments.get(1) + " not found");
                responseBuilder.setStatus(HttpStatus.SC_NOT_FOUND);
                String response =
                        "<html><body><h1>File with id " + contentId + albumId
                                 + pathSegments.get(1) + " not found</h1></body></html>";
                responseBuilder.setEntity(AsyncEntityProducers.create(response, ContentType.TEXT_HTML));
                responseTrigger.submitResponse(responseBuilder.build(), context);
            }
        }

        /**
         * Lookup content in the media library
         *
         * @param contentId the id of the content
         * @return the content description
         */
        private ContentHolder lookupContent(String contentId, String agent) {
            ContentHolder result = null;

            if (contentId == null) {
                return null;
            }
            // Log.d(TAG, "MusicMate lookup content: " + contentId);
            try {
                MusicTag tag = MusixMateApp.getInstance().getOrmLite().findById(StringUtils.toLong(contentId));
                if (tag != null) {
                    ContentType contentType = getContentType(tag);
                    PlayerInfo player = PlayerInfo.buildStreamPlayer(agent, ContextCompat.getDrawable(getContext(), R.drawable.img_upnp));
                    MusixMateApp.getPlayerControl().publishPlayingSong(player, tag);
                    result = new ContentHolder(contentType, String.valueOf(tag.getId()), tag.getPath());
                    result.transcode = MediaServerSession.isTransCoded(tag);
                }
            } catch (Exception ex) {
                Log.e(TAG, "lookupContent: - " + contentId, ex);
            }
            return result;
        }

        /**
         * Lookup content in the media library
         *
         * @param albumId the id of the album
         * @return the content description
         */
        private ContentHolder lookupAlbumArt(String albumId) {
            try {
                String path = CoverArtProvider.COVER_ARTS + albumId;
                File pathFile = new File(coverartDir, path);
                if (pathFile.exists()) {
                    return new ContentHolder(ContentType.IMAGE_PNG, albumId,
                            pathFile.getAbsolutePath());
                }
            } catch (Exception e) {
                Log.e(TAG, "lookupAlbumArt: - not found " + albumId, e);
            }

            // Log.d(TAG, "Send default albumArt for " + albumId);
            return new ContentHolder(ContentType.IMAGE_JPEG, albumId,
                    getDefaultIcon(albumId));
        }
    }

    private ContentType getContentType(MusicTag tag) {
        if(MediaServerSession.isTransCoded(tag)) {
            return ContentType.parse( "audio/mpeg");
        }else if(MusicTagUtils.isAIFFile(tag)) {
            return ContentType.parse( "audio/x-aiff");
       // }else  if(MusicTagUtils.isMPegFile(tag)) {
        }else if(MusicTagUtils.isMPegFile(tag) || MusicTagUtils.isAACFile(tag)) {
            return ContentType.parse( "audio/mpeg");
        }else if(MusicTagUtils.isFLACFile(tag)) {
            return ContentType.parse( "audio/x-flac");
        }else if(MusicTagUtils.isALACFile(tag)) {
            return ContentType.parse( "audio/x-mp4");
        }else if(MusicTagUtils.isMp4File(tag)) {
            return ContentType.parse( "audio/x-m4a");
        }else  if(MusicTagUtils.isWavFile(tag)) {
            return ContentType.parse( "audio/x-wav");
        }else {
            return ContentType.parse( "audio/*");
        }
    }

    /**
     * ValueHolder for media content.
     */
    static class ContentHolder {
        private final ContentType contentType;
        private final String resId;
        private String filePath;
        private byte[] content;
        private boolean transcode;

        public ContentHolder(ContentType contentType, String resId, String filePath) {
            this.resId = resId;
            this.filePath = filePath;
            this.contentType = contentType;
        }

        public ContentHolder(ContentType contentType, String resId, byte[] content) {
            this.resId = resId;
            this.content = content;
            this.contentType = contentType;
        }

        /**
         * @return the uri
         */
        public String getFilePath() {
            return filePath;
        }

        public AsyncEntityProducer getEntityProducer(Context context) {
            AsyncEntityProducer result = null;
            if (!isEmpty(getFilePath())) {
                if (new File(getFilePath()).exists()) {
                    if(transcode) {
                        // transcode to lpcm before send to
                        Buffer buff = transCodeCached.get(resId);
                        if(buff == null) {
                            Buffer data = FFMpegHelper.transcodeFile(context, getFilePath());
                            if(data != null) {
                                transCodeCached.put(resId, data);
                                result = AsyncEntityProducers.create(data.readByteArray(), contentType);
                            }
                        }else {
                            result = AsyncEntityProducers.create(buff.readByteArray(), contentType);
                        }
                    }else {
                        // if not found return null
                        File file = new File(getFilePath());
                        result = AsyncEntityProducers.create(file, contentType);
                      //  Buffer buff = FileUtils.getBytes(new File(getFilePath()));
                       // result = AsyncEntityProducers.create(buff.readByteArray(), contentType);
                    }
                }
            } else if (content != null) {
                result = AsyncEntityProducers.create(content, contentType);
            }
            return result;
        }
    }
}