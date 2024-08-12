package apincer.android.mmate.dlna.transport;

import android.content.Context;
import android.net.Uri;
import android.util.Log;

import androidx.core.content.ContextCompat;

import org.apache.commons.io.IOUtils;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.EntityDetails;
import org.apache.hc.core5.http.Header;
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
import org.jupnp.util.MimeType;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URL;
import java.net.URLConnection;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import apincer.android.mmate.MusixMateApp;
import apincer.android.mmate.R;
import apincer.android.mmate.broadcast.AudioTagPlayingEvent;
import apincer.android.mmate.dlna.MediaServerSession;
import apincer.android.mmate.dlna.MimeDetector;
import apincer.android.mmate.player.PlayerInfo;
import apincer.android.mmate.provider.CoverArtProvider;
import apincer.android.mmate.repository.MusicTag;
import apincer.android.mmate.utils.ApplicationUtils;
import apincer.android.mmate.utils.StringUtils;

public class HttpCoreStreamServer implements StreamServer<StreamServerConfigurationImpl> {
    private final Context context;
    private static final String TAG = "HttpCoreStreamServer";
    private final List<byte[]> cachedIconRAWs = new ArrayList<>();
    private int currentIconIndex = 0;
    private HttpAsyncServer server;
    final protected StreamServerConfigurationImpl configuration;
    protected int localPort;

    public void loadCachedIcons() {
        // defaultIconRAWs.add(readDefaultCover("no_cover1.jpg"));
        // defaultIconRAWs.add(readDefaultCover("no_cover2.jpg"));
        cachedIconRAWs.add(readDefaultCover("no_cover3.jpg"));
        cachedIconRAWs.add(readDefaultCover("no_cover4.jpg"));
        cachedIconRAWs.add(readDefaultCover("no_cover5.jpg"));
        cachedIconRAWs.add(readDefaultCover("no_cover6.jpg"));
        cachedIconRAWs.add(readDefaultCover("no_cover7.jpg"));
    }

    public HttpCoreStreamServer(Context context, StreamServerConfigurationImpl configuration) {
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
                    Log.i(TAG, "Adding httpcore5 stream server connector: " + bindAddress.getHostAddress() + ":" + getConfiguration().getListenPort());

                    MediaServerSession.streamServerHost = bindAddress.getHostAddress();
                    IOReactorConfig config = IOReactorConfig.custom()
                            .setSoTimeout(getConfiguration().getAsyncTimeoutSeconds(), TimeUnit.SECONDS)
                            .setTcpNoDelay(true)
                            .build();
                    server = H2ServerBootstrap.bootstrap()
                            .setCanonicalHostName(bindAddress.getHostAddress())
                            .setIOReactorConfig(config)
                            .register(router.getConfiguration().getNamespace().getBasePath().getPath() + "/*", new UPnpStreamHandler(router.getProtocolFactory()))
                            .register("/*", new ResourceHandler())
                            .create();
                    server.listen(new InetSocketAddress(getConfiguration().getListenPort()), URIScheme.HTTP);
                    server.start();
                } catch (Exception ex) {
                    throw new InitializationException("Could not initialize " + getClass().getSimpleName() + ": " + ex, ex);
                }
            }
        });

        thread.start();

    }

    synchronized public int getPort() {
        return this.localPort;
    }

    synchronized public void stop() {
        if(server != null) {
            server.initiateShutdown();
            try {
                server.awaitShutdown(TimeValue.ofSeconds(3));
            } catch (InterruptedException e) {
                Log.w(TAG, "got exception on stream server stop ", e);
            }
        }
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

    private class ResourceHandler implements AsyncServerRequestHandler<Message<HttpRequest, byte[]>> {

        public ResourceHandler() {

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

   private static class UPnpStreamHandler extends UpnpStream implements AsyncServerRequestHandler<Message<HttpRequest, byte[]>> {
        private static final String TAG = "StreamServerHandler";
    protected UPnpStreamHandler(ProtocolFactory protocolFactory ) {
            super(protocolFactory);
        }

        @Override
        public AsyncRequestConsumer<Message<HttpRequest, byte[]>> prepare(
        final HttpRequest request,
        final EntityDetails entityDetails,
        final HttpContext context) {
            return new BasicRequestConsumer<>(entityDetails != null ? new BasicAsyncEntityConsumer() : null);
        }

        @Override
        public void handle(
        final Message<HttpRequest, byte[]> message,
        final ResponseTrigger responseTrigger,
        final HttpContext context) throws HttpException, IOException {
            final AsyncResponseBuilder responseBuilder = AsyncResponseBuilder.create(HttpStatus.SC_OK);

            try {
                StreamRequestMessage requestMessage = readRequestMessage(message);
                // Log.v(TAG, "Processing new request message: " + requestMessage);

                String userAgent = getUserAgent(requestMessage);
                if("CyberGarage-HTTP/1.0".equals(userAgent)) { // ||
                    // "Panasonic iOS VR-CP UPnP/2.0".equals(userAgent)) {//     requestMessage.getHeaders().getFirstHeader("User-agent"))) {
                    Log.v(TAG, "Interim FIX for MConnect on IPadOS 18 beta, return all songs for MConnect(fix show only 20 songs)");
                    MediaServerSession.forceFullContent = true;
                }

                StreamResponseMessage responseMessage = process(requestMessage);

                if (responseMessage != null) {
                    // Log.v(TAG, "Preparing HTTP response message: " + responseMessage);
                    writeResponseMessage(responseMessage, responseBuilder);
                } else {
                    // If it's null, it's 404
                    Log.v(TAG, "Sending HTTP response status: " + HttpStatus.SC_NOT_FOUND);
                    responseBuilder.setStatus(HttpStatus.SC_NOT_FOUND);
                }
                responseTrigger.submitResponse(responseBuilder.build(), context);

            } catch (Throwable t) {
                // StreamRequestMessage requestMessage = readRequestMessage(message);
                Log.e(TAG, "Exception occurred during UPnP stream processing: ", t);
                // Log.d(TAG, "Cause: " + Exceptions.unwrap(t), Exceptions.unwrap(t));
                // Log.v(TAG, "returning INTERNAL SERVER ERROR to client");
                responseBuilder.setStatus(HttpStatus.SC_INTERNAL_SERVER_ERROR);
                responseTrigger.submitResponse(responseBuilder.build(), context);

                responseException(t);
            }
        }

        private String getUserAgent(StreamRequestMessage requestMessage) {
            try {
                return requestMessage.getHeaders().getFirstHeader("User-agent");
            }catch (Exception ignore) {
            }
            return "";
        }

        protected StreamRequestMessage readRequestMessage(Message<HttpRequest, byte[]> message) {
            // Extract what we need from the HTTP httpRequest
            HttpRequest request = message.getHead();
            String requestMethod = request.getMethod();
            String requestURI = request.getRequestUri();

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
            Header[] requestHeaders = request.getHeaders();
            for (Header header : requestHeaders) {
                headers.add(header.getName(), header.getValue());
            }
            requestMessage.setHeaders(headers);

            // Body
            byte[] bodyBytes = message.getBody();
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

        protected void writeResponseMessage(StreamResponseMessage responseMessage, AsyncResponseBuilder responseBuilder) {
            //   Log.v(TAG, "Sending HTTP response status: " + responseMessage.getOperation().getStatusCode());

            responseBuilder.setStatus(responseMessage.getOperation().getStatusCode());

            // Headers
            for (Map.Entry<String, List<String>> entry : responseMessage.getHeaders().entrySet()) {
                for (String value : entry.getValue()) {
                    responseBuilder.addHeader(entry.getKey(), value);
                }
            }
            // The Date header is recommended in UDA
            responseBuilder.addHeader("Date", "" + System.currentTimeMillis());

            // Body
            byte[] responseBodyBytes = responseMessage.hasBody() ? responseMessage.getBodyBytes() : null;
            int contentLength = responseBodyBytes != null ? responseBodyBytes.length : -1;

            if (contentLength > 0) {
                Log.v(TAG, "Response message has body, writing bytes to stream...");
                // Log.d(TAG, "Response message has body, "+new String(responseBodyBytes));
                ContentType ct = ContentType.APPLICATION_XML;
                if (responseMessage.getContentTypeHeader() != null) {
                    ct = ContentType.parse(responseMessage.getContentTypeHeader().getValue().toString());
                }
                responseBuilder.setEntity(AsyncEntityProducers.create(responseBodyBytes, ct));
            }
        }

        @Override
        public void run() {

        }
    }
}
