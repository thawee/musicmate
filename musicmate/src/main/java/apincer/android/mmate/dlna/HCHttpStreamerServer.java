package apincer.android.mmate.dlna;

import android.content.Context;
import android.net.Uri;
import android.util.Log;

import androidx.core.content.ContextCompat;

import org.apache.commons.io.IOUtils;
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
import org.apache.hc.core5.http.nio.StreamChannel;
import org.apache.hc.core5.http.nio.entity.AbstractBinAsyncEntityProducer;
import org.apache.hc.core5.http.nio.entity.AsyncEntityProducers;
import org.apache.hc.core5.http.nio.entity.BasicAsyncEntityConsumer;
import org.apache.hc.core5.http.nio.support.AsyncResponseBuilder;
import org.apache.hc.core5.http.nio.support.BasicRequestConsumer;
import org.apache.hc.core5.http.protocol.HttpContext;
import org.apache.hc.core5.http2.impl.nio.bootstrap.H2ServerBootstrap;
import org.apache.hc.core5.reactor.IOReactorConfig;
import org.apache.hc.core5.util.TimeValue;
import org.jupnp.util.MimeType;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.net.URL;
import java.net.URLConnection;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

import apincer.android.mmate.MusixMateApp;
import apincer.android.mmate.R;
import apincer.android.mmate.broadcast.AudioTagPlayingEvent;
import apincer.android.mmate.player.PlayerInfo;
import apincer.android.mmate.provider.CoverArtProvider;
import apincer.android.mmate.repository.MusicTag;
import apincer.android.mmate.utils.ApplicationUtils;
import apincer.android.mmate.utils.StringUtils;

public class HCHttpStreamerServer {
    private final String ipAddress;
    private final int port;
    private final Context context;
    private static final String TAG = "HCHttpStreamerServer";
    private final List<byte[]> defaultIconRAWs;
    private int currentIconIndex = 0;
    private HttpAsyncServer server;

    public HCHttpStreamerServer(Context context, String ipAddress, int port) {
        this.context = context;
        this.ipAddress = ipAddress;
        this.port = port;

        defaultIconRAWs = new ArrayList<>();
        // defaultIconRAWs.add(readDefaultCover("no_cover1.jpg"));
        // defaultIconRAWs.add(readDefaultCover("no_cover2.jpg"));
        defaultIconRAWs.add(readDefaultCover("no_cover3.jpg"));
        defaultIconRAWs.add(readDefaultCover("no_cover4.jpg"));
        defaultIconRAWs.add(readDefaultCover("no_cover5.jpg"));
        defaultIconRAWs.add(readDefaultCover("no_cover6.jpg"));
        defaultIconRAWs.add(readDefaultCover("no_cover7.jpg"));

        IOReactorConfig config = IOReactorConfig.custom()
                .setSoKeepAlive(true)
                .setTcpNoDelay(true)
                .setSoTimeout(60, TimeUnit.SECONDS)
                .build();

        server = H2ServerBootstrap.bootstrap()
                .setIOReactorConfig(config)
                .setCanonicalHostName(this.ipAddress)
                .register("*", new RequestHandler())
                .create();

        server.listen(new InetSocketAddress(this.port), URIScheme.HTTP);
    }

    private Context getContext() {
        return context;
    }
    public void start() throws IOException {
        server.start();
    }

    public void stop() {
        server.initiateShutdown();
        try {
            server.awaitShutdown(TimeValue.ofSeconds(3));
        } catch (InterruptedException e) {
            Log.w(TAG, "got exception on stream server stop ", e);
        }
    }
    private byte[] getDefaultIcon() {
        currentIconIndex++;
        if(currentIconIndex >= defaultIconRAWs.size()) currentIconIndex = 0;
        return defaultIconRAWs.get(currentIconIndex);
    }

    private byte[] readDefaultCover(String file) {
        InputStream in = ApplicationUtils.getAssetsAsStream(getContext(), file);
        try {
            return IOUtils.toByteArray(in);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private class RequestHandler implements AsyncServerRequestHandler<Message<HttpRequest, byte[]>> {

        public RequestHandler() {

        }

        @Override
        public AsyncRequestConsumer<Message<HttpRequest, byte[]>> prepare(HttpRequest request, EntityDetails entityDetails, HttpContext context) {
            return new BasicRequestConsumer<>(entityDetails != null ? new BasicAsyncEntityConsumer() : null);
        }

        @Override
        public void handle(Message<HttpRequest, byte[]> request, ResponseTrigger responseTrigger, HttpContext context) throws IOException, HttpException {
            //Log.d(TAG, "Processing HTTP request: "
            //        + request.getHead().getRequestUri()+" - from: "
            //        + request.getHead().getLastHeader("User-Agent"));
            final AsyncResponseBuilder responseBuilder = AsyncResponseBuilder.create(HttpStatus.SC_OK);
            // Extract what we need from the HTTP httpRequest
            String requestMethod = request.getHead().getMethod()
                    .toUpperCase(Locale.ENGLISH);
            String userAgent = request.getHead().getLastHeader("User-Agent").getValue();

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
                // Log.d(getClass().getName(), "end doService: Access denied");
                return;
            }
            String type = pathSegments.get(0);
            String albumId = "";
            String thumbId = "";
            String contentId = "";
            if ("album".equals(type)) {
                albumId = pathSegments.get(1);
                if (StringUtils.isEmpty(albumId)) {
                    responseBuilder.setStatus(HttpStatus.SC_FORBIDDEN);
                    responseBuilder.setEntity(AsyncEntityProducers.create("<html><body><h1>Access denied</h1></body></html>", ContentType.TEXT_HTML));
                    responseTrigger.submitResponse(responseBuilder.build(), context);
                    Log.d(getClass().getName(), "end doService: Access denied");
                    return;
                }
            } else if ("res".equals(type)) {
                contentId = pathSegments.get(1);
                try {
                    Long.parseLong(contentId);
                } catch (NumberFormatException nex) {
                    responseBuilder.setStatus(HttpStatus.SC_FORBIDDEN);
                    responseBuilder.setEntity(AsyncEntityProducers.create("<html><body><h1>Access denied</h1></body></html>", ContentType.TEXT_HTML));
                    responseTrigger.submitResponse(responseBuilder.build(), context);
                    Log.d(TAG, "end doService: Access denied");
                    return;
                }
            }

            ContentHolder contentHolder = null;

            if (!contentId.isEmpty()) {
                contentHolder = lookupContent(contentId, userAgent);
            } else if (!albumId.isEmpty()) {
                contentHolder = lookupAlbumArt(albumId);
            }
            if (contentHolder == null) {
                // tricky but works
                Log.d(TAG, "Resource with id " + contentId
                        + albumId + thumbId + pathSegments.get(1) + " not found");
                responseBuilder.setStatus(HttpStatus.SC_NOT_FOUND);
                String response =
                        "<html><body><h1>Resource with id " + contentId + albumId
                                + thumbId + pathSegments.get(1) + " not found</h1></body></html>";
                responseBuilder.setEntity(AsyncEntityProducers.create(response, ContentType.TEXT_HTML));
            } else {
                responseBuilder.setStatus(HttpStatus.SC_OK);
                responseBuilder.setEntity(contentHolder.getEntityProducer());
            }
            responseTrigger.submitResponse(responseBuilder.build(), context);
            // Log.d(TAG, "end doService: ");
        }

        /**
         * Lookup content in the mediastore
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
                    MimeType mimeType = MimeDetector.getMimeType(tag);
                    PlayerInfo player = PlayerInfo.buildStreamPlayer(agent, ContextCompat.getDrawable(getContext(), R.drawable.img_upnp));
                    MusixMateApp.getPlayerControl().setPlayingSong(player, tag);
                    AudioTagPlayingEvent.publishPlayingSong(tag);
                    result = new ContentHolder(mimeType, tag.getPath());
                }
            } catch (Exception ex) {
                Log.e(TAG, "lookupContent: - " + contentId, ex);
            }
            return result;
        }

        /**
         * Lookup content in the mediastore
         *
         * @param albumId the id of the album
         * @return the content description
         */
        private ContentHolder lookupAlbumArt(String albumId) {
            // Log.d(TAG, "MusicMate lookup albumArt: " + albumId);

            try {
                String path = CoverArtProvider.COVER_ARTS + albumId;
                File dir = getContext().getExternalCacheDir();
                File pathFile = new File(dir, path);
                if (pathFile.exists()) {
                    return new ContentHolder(MimeType.valueOf("image/png"),
                            pathFile.getAbsolutePath());
                }
            } catch (Exception e) {
                Log.e(TAG, "lookupAlbumArt: - not found " + albumId, e);
            }

            // Log.d(TAG, "Send default albumArt for " + albumId);
            return new ContentHolder(MimeType.valueOf("image/jpg"),
                    getDefaultIcon());
        }
    }

   /**
   * ValueHolder for media content.
   */
   static class ContentHolder {
            private final MimeType mimeType;
            private String uri;
            private byte[] content;

            public ContentHolder(MimeType mimeType, String uri) {
                this.uri = uri;
                this.mimeType = mimeType;

            }

            public ContentHolder(MimeType mimeType, byte[] content) {
                this.content = content;
                this.mimeType = mimeType;

            }

            /**
             * @return the uri
             */
            public String getUri() {
                return uri;
            }

            /**
             * @return the mimeType
             */
            public MimeType getMimeType() {
                return mimeType;
            }

            public AsyncEntityProducer getEntityProducer() {
                AsyncEntityProducer result = null;
                if (getUri() != null && !getUri().isEmpty()) {
                    if (new File(getUri()).exists()) {

                        File file = new File(getUri());
                        result = AsyncEntityProducers.create(file, ContentType.parse(getMimeType().toString()));
                        // Log.d(TAG, "Return file-Uri: " + getUri()
                        //         + "Mimetype: " + getMimeType());
                    } else {
                        //file not found maybe external url
                        result = new AbstractBinAsyncEntityProducer(0, ContentType.parse(getMimeType().toString())) {
                            private InputStream input;
                            private long length = -1;

                            AbstractBinAsyncEntityProducer init() {
                                try {
                                    if (input == null) {
                                        URLConnection con = new URL(getUri()).openConnection();
                                        input = con.getInputStream();
                                        length = con.getContentLength();
                                    }
                                } catch (IOException e) {
                                    Log.e(TAG, "Error opening external content", e);
                                }
                                return this;
                            }

                            @Override
                            public long getContentLength() {
                                return length;
                            }

                            @Override
                            protected int availableData() {
                                return Integer.MAX_VALUE;
                            }

                            @Override
                            protected void produceData(final StreamChannel<ByteBuffer> channel) throws IOException {
                                try {
                                    if (input == null) {
                                        //retry opening external content if it hasn't been opened yet
                                        URLConnection con = new URL(getUri()).openConnection();
                                        input = con.getInputStream();
                                        length = con.getContentLength();
                                    }
                                    byte[] tempBuffer = new byte[1024];
                                    int bytesRead;
                                    if (-1 != (bytesRead = input.read(tempBuffer))) {
                                        channel.write(ByteBuffer.wrap(tempBuffer, 0, bytesRead));
                                    }
                                    if (bytesRead == -1) {
                                        channel.endStream();
                                    }

                                } catch (IOException e) {
                                    Log.e(TAG, "Error reading external content", e);
                                    throw e;
                                }
                            }


                            @Override
                            public boolean isRepeatable() {
                                return false;
                            }

                            @Override
                            public void failed(final Exception cause) {
                            }

                        }.init();

                        // Log.d(TAG, "Return external-Uri: " + getUri()
                        //         + "Mimetype: " + getMimeType());
                    }
                } else if (content != null) {
                    result = AsyncEntityProducers.create(content, ContentType.parse(getMimeType().toString()));
                }
                return result;

            }
        }
}
