package apincer.android.mmate.dlna;

import android.content.Context;
import android.util.Log;

import androidx.core.content.ContextCompat;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.jupnp.util.MimeType;
import org.xnio.Options;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import apincer.android.mmate.MusixMateApp;
import apincer.android.mmate.R;
import apincer.android.mmate.broadcast.AudioTagPlayingEvent;
import apincer.android.mmate.broadcast.MusicPlayerInfo;
import apincer.android.mmate.provider.CoverArtProvider;
import apincer.android.mmate.repository.MusicTag;
import apincer.android.mmate.utils.ApplicationUtils;
import apincer.android.mmate.utils.StringUtils;
import io.undertow.Undertow;
import io.undertow.UndertowOptions;
import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.util.Headers;

public class HttpStreamerServer {
    Undertow server;
    private String ipAddress;
    private int port;
    private Context context;
    private static final String TAG = "HttpStreamerServer";
    private List<byte[]> defaultIconRAWs;
    private int currentIconIndex = 0;
    private final int ioTheads = Math.max(Runtime.getRuntime().availableProcessors(), 2);
    private final int maxWorkerThreads = 5000;
    private final int coreWorkerThreads = 200;
    public HttpStreamerServer(Context context, String serverAddress, int port) {
        this.context = context;
        this.ipAddress = serverAddress;
        this.port = port;
        defaultIconRAWs = new ArrayList<>();
        // defaultIconRAWs.add(readDefaultCover("no_cover1.jpg"));
        // defaultIconRAWs.add(readDefaultCover("no_cover2.jpg"));
        defaultIconRAWs.add(readDefaultCover("no_cover3.jpg"));
        defaultIconRAWs.add(readDefaultCover("no_cover4.jpg"));
        defaultIconRAWs.add(readDefaultCover("no_cover5.jpg"));
        defaultIconRAWs.add(readDefaultCover("no_cover6.jpg"));
        defaultIconRAWs.add(readDefaultCover("no_cover7.jpg"));


        server = Undertow.builder().setServerOption(UndertowOptions.ENABLE_HTTP2, true)
                .setServerOption(UndertowOptions.ALLOW_UNESCAPED_CHARACTERS_IN_URL,true)
                .setServerOption(UndertowOptions.ALWAYS_SET_KEEP_ALIVE,true)
                .setServerOption(UndertowOptions.ENABLE_STATISTICS,false)
                .setServerOption(UndertowOptions.ALLOW_UNKNOWN_PROTOCOLS,false)
                .setServerOption(UndertowOptions.HTTP2_SETTINGS_ENABLE_PUSH,false)
                .setServerOption(UndertowOptions.REQUIRE_HOST_HTTP11,false)
                .addHttpListener(port, ipAddress)
                .setWorkerOption(Options.WORKER_IO_THREADS, ioTheads)
                .setWorkerOption(Options.WORKER_TASK_CORE_THREADS, coreWorkerThreads)
                .setWorkerOption(Options.WORKER_TASK_MAX_THREADS, maxWorkerThreads)
                .setWorkerThreads(ioTheads)
                .setIoThreads(ioTheads)
                .setHandler(new StreamerHandler()).build();
       // server.start();

       // Undertow.Builder builder = Undertow.builder();
       /* server = builder
                .addHttpListener(port, ipAddress)
                .setHandler(new StreamerHandler())
                .build();
*/
    }
    private Context getContext() {
        return context;
    }
    public void start() {
        server.start();
    }

    public void stop() {
        server.stop();
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

    class StreamerHandler implements HttpHandler {

        @Override
        public void handleRequest(HttpServerExchange exchange) throws Exception {
          //  String method = exchange.getRequestMethod().toString();
          //  String path = exchange.getRequestPath();
            List<String> pathSegments = getPathSegments(exchange);
            if (pathSegments.size() < 2 || pathSegments.size() > 3) {
                exchange.setStatusCode(403);
                exchange.getResponseHeaders().put(Headers.CONTENT_TYPE, "text/html");
                exchange.getResponseSender()
                        .send("<html><body><h1>Access denied</h1></body></html>");
                return;
            }
            String type = pathSegments.get(0);
            if("album".equals(type)) {
                handleAlbumArt(exchange, pathSegments.get(1));
            }else if ("res".equals(type)) {
                handleRes( exchange, pathSegments.get(1));
            }
        }

        private void handleRes(HttpServerExchange exchange, String resId) {
            try {
                String agent = exchange.getRequestHeaders().getFirst(Headers.USER_AGENT_STRING);
                MusicTag tag = MusixMateApp.getInstance().getOrmLite().findById(StringUtils.toLong(resId));
                if(tag != null) {
                    MimeType mimeType = new MimeType("audio", tag.getAudioEncoding());
                    MusicPlayerInfo player = MusicPlayerInfo.buildStreamPlayer(agent, ContextCompat.getDrawable(getContext(), R.drawable.img_upnp));
                    MusixMateApp.setPlaying(player, tag);
                    AudioTagPlayingEvent.publishPlayingSong(tag);
                    exchange.getResponseSender().send(toByteBuffer(new File(tag.getPath())));
                }
            }catch (Exception ex) {
                Log.e(TAG, "lookupContent: - " + resId, ex);
            }
        }

        private void handleAlbumArt(HttpServerExchange exchange,String albumId) {
            try {
                String path = CoverArtProvider.COVER_ARTS + albumId;
                File dir = getContext().getExternalCacheDir();
                File pathFile = new File(dir, path);
                if (pathFile.exists()) {
                    exchange.getResponseSender().send(toByteBuffer(pathFile));
                    return;
                }
            }catch (Exception e) {
                Log.e(TAG, "lookupAlbumArt: - not found " + albumId, e);
            }

            // Log.d(TAG, "Send default albumArt for " + albumId);
           // exchange.getResponseSender().send(new ByteBuffer(getDefaultIcon()));
        }

        private ByteBuffer toByteBuffer(File pathFile) throws IOException {
            byte[] bytes = FileUtils.readFileToByteArray(pathFile);
            ByteBuffer buffer = ByteBuffer.allocate(bytes.length);

            buffer.clear();
            buffer.put(bytes);
            buffer.flip();

            return buffer;
        }

        public List<String> getPathSegments(HttpServerExchange exchange) {
            return Arrays.asList(exchange.getRequestPath().split("/", -1));
        }
    }
}
