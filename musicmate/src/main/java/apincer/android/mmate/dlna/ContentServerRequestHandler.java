package apincer.android.mmate.dlna;

import android.content.Context;
import android.graphics.Bitmap;
import android.net.Uri;
import android.provider.MediaStore;
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
import org.jupnp.util.MimeType;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLConnection;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.Locale;

import apincer.android.mmate.MusixMateApp;
import apincer.android.mmate.R;
import apincer.android.mmate.broadcast.AudioTagPlayingEvent;
import apincer.android.mmate.broadcast.MusicPlayerInfo;
import apincer.android.mmate.repository.MusicTag;
import apincer.android.mmate.utils.ApplicationUtils;
import apincer.android.mmate.utils.StringUtils;

public class ContentServerRequestHandler implements AsyncServerRequestHandler<Message<HttpRequest, byte[]>> {
    private static final String TAG = "ContentServerHandler";
    private final Context applicationContext;
    public ContentServerRequestHandler(Context applicationContext) {
        this.applicationContext = applicationContext;
    }

    @Override
    public AsyncRequestConsumer<Message<HttpRequest, byte[]>> prepare(HttpRequest request, EntityDetails entityDetails, HttpContext context) {
        return new BasicRequestConsumer<>(entityDetails != null ? new BasicAsyncEntityConsumer() : null);
    }

    @Override
    public void handle(Message<HttpRequest, byte[]> request, ResponseTrigger responseTrigger, HttpContext context) throws HttpException, IOException {
        Log.d(TAG, "Processing HTTP request: "
                + request.getHead().getRequestUri());
        Log.d(TAG, "Processing HTTP request: "
                + request.getHead().getLastHeader("User-Agent"));
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
            Log.d(getClass().getName(), "end doService: Access denied");
            return;
        }
        String type = pathSegments.get(0);
        String albumId = "";
        String thumbId = "";
        String contentId = "";
        if ("album".equals(type)) {
            albumId = pathSegments.get(1);
            if(StringUtils.isEmpty(albumId)) {
                responseBuilder.setStatus(HttpStatus.SC_FORBIDDEN);
                responseBuilder.setEntity(AsyncEntityProducers.create("<html><body><h1>Access denied</h1></body></html>", ContentType.TEXT_HTML));
                responseTrigger.submitResponse(responseBuilder.build(), context);
                Log.d(getClass().getName(), "end doService: Access denied");
                return;
            }
           /* try {
                Long.parseLong(albumId);
            } catch (NumberFormatException nex) {
                responseBuilder.setStatus(HttpStatus.SC_FORBIDDEN);
                responseBuilder.setEntity(AsyncEntityProducers.create("<html><body><h1>Access denied</h1></body></html>", ContentType.TEXT_HTML));
                responseTrigger.submitResponse(responseBuilder.build(), context);
                Log.d(getClass().getName(), "end doService: Access denied");
                return;
            } */
        } else if ("thumb".equals(type)) {
            thumbId = pathSegments.get(1);
            try {
                Long.parseLong(thumbId);
            } catch (NumberFormatException nex) {
                responseBuilder.setStatus(HttpStatus.SC_FORBIDDEN);
                responseBuilder.setEntity(AsyncEntityProducers.create("<html><body><h1>Access denied</h1></body></html>", ContentType.TEXT_HTML));
                responseTrigger.submitResponse(responseBuilder.build(), context);
                Log.d(TAG, "end doService: Access denied");
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
        } else if (!thumbId.isEmpty()) {
            contentHolder = lookupThumbnail(thumbId);
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
            Log.d(TAG, "end doService: ");
        }

        private Context getContext() {
            return applicationContext;
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
            Log.d(TAG, "MusicMate lookup content: " + contentId);
            try {
                MusicTag tag = MusixMateApp.getInstance().getOrmLite().findById(StringUtils.toLong(contentId));
                MimeType mimeType = new MimeType("audio", tag.getAudioEncoding());
                MusicPlayerInfo player = MusicPlayerInfo.buildStreamPlayer(agent, ContextCompat.getDrawable(getContext(), R.drawable.img_upnp));
                MusixMateApp.setPlaying(player, tag);
                AudioTagPlayingEvent.publishPlayingSong(tag);
                result = new ContentHolder(mimeType, tag.getPath());
            }catch (Exception ex) {
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
            Log.d(TAG, "MusicMate lookup albumArt: " + albumId);

            try {
                String path = "/CoverArts/" + albumId;
                File dir = getContext().getExternalCacheDir();
                File pathFile = new File(dir, path);
                if (pathFile.exists()) {
                    byte[] image = IOUtils.toByteArray(new FileInputStream(pathFile));

                    return new ContentHolder(MimeType.valueOf("image/png"),
                            image);
                }
            }catch (Exception e) {
                Log.e(TAG, "lookupAlbumArt: - " + albumId, e);
            }

            Log.d(TAG, "Send default albumArt for " + albumId);
            return new ContentHolder(MimeType.valueOf("image/png"),
                        getDefaultIcon());
        }

        /**
         * Lookup a thumbnail content in the mediastore
         *
         * @param idStr the id of the thumbnail
         * @return the content description
         */
        private ContentHolder lookupThumbnail(String idStr) {

            ContentHolder result = new ContentHolder(MimeType.valueOf("image/png"),
                    getDefaultIcon());
            if (idStr == null) {
                return result;
            }
            long id;
            try {
                id = Long.parseLong(idStr);
            } catch (NumberFormatException nfe) {
                Log.d(TAG, "ParsingError of id: " + idStr, nfe);
                return result;
            }

            Log.d(TAG, "System media store lookup thumbnail: "
                    + idStr);
            Bitmap bitmap = MediaStore.Images.Thumbnails.getThumbnail(getContext()
                            .getContentResolver(), id,
                    MediaStore.Images.Thumbnails.MINI_KIND, null);
            if (bitmap != null) {
                ByteArrayOutputStream stream = new ByteArrayOutputStream();
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream);
                byte[] byteArray = stream.toByteArray();

                MimeType mimeType = MimeType.valueOf("image/png");

                result = new ContentHolder(mimeType, byteArray);

            } else {
                Log.d(TAG, "System media store is empty.");
            }
            return result;
        }

        private byte[] getDefaultIcon() {
          //  InputStream in = ApplicationUtils.getAssetsAsStream(getContext(), "iconpng192.png");
            InputStream in = ApplicationUtils.getAssetsAsStream(getContext(), "mstile_310_310.png");
            byte[] result = new byte[0];
            try {
                result = IOUtils.toByteArray(in);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
            return result;
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
                        Log.d(TAG, "Return file-Uri: " + getUri()
                                + "Mimetype: " + getMimeType());
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

                        Log.d(TAG, "Return external-Uri: " + getUri()
                                + "Mimetype: " + getMimeType());
                    }
                } else if (content != null) {
                    result = AsyncEntityProducers.create(content, ContentType.parse(getMimeType().toString()));
                }
                return result;

            }
        }
}
