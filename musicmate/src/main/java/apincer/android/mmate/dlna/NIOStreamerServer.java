package apincer.android.mmate.dlna;

import android.content.Context;
import android.util.Log;

import androidx.core.content.ContextCompat;

import com.koushikdutta.async.ByteBufferList;
import com.koushikdutta.async.http.server.AsyncHttpServer;
import com.koushikdutta.async.http.server.AsyncHttpServerRequest;
import com.koushikdutta.async.http.server.AsyncHttpServerResponse;
import com.koushikdutta.async.http.server.HttpServerRequestCallback;
import com.koushikdutta.async.util.StreamUtility;

import org.apache.commons.io.IOUtils;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import apincer.android.mmate.MusixMateApp;
import apincer.android.mmate.R;
import apincer.android.mmate.broadcast.AudioTagPlayingEvent;
import apincer.android.mmate.player.PlayerInfo;
import apincer.android.mmate.provider.CoverArtProvider;
import apincer.android.mmate.repository.MusicTag;
import apincer.android.mmate.utils.ApplicationUtils;
import apincer.android.mmate.utils.StringUtils;

public class NIOStreamerServer {
    private final String ipAddress;
    private final int port;
    private final Context context;
    private static final String TAG = "NIOStreamerServer";
    private final List<byte[]> defaultIconRAWs;
    private int currentIconIndex = 0;
    private AsyncHttpServer server;

    public NIOStreamerServer(Context context, String ipAddress, int port) {
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
        server = new AsyncHttpServer();
        HttpServerRequestCallback callback = new NIOServerRequestCallback();
        server.get("/.*", callback);

    }

    private Context getContext() {
        return context;
    }
    public void start() throws IOException {
        server.listen(this.port);
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

    private class NIOServerRequestCallback implements HttpServerRequestCallback {

        public List<String> getPathSegments(AsyncHttpServerRequest req) {
            String path = req.getPath();
            if(path.startsWith("/")) {
                path = path.substring(1);
            }
            return Arrays.asList(path.split("/", -1));
        }

        private void handleResource(AsyncHttpServerRequest req, AsyncHttpServerResponse resp, List<String> pathSegments) {
            try {
                String agent = req.getHeaders().get("User-Agent");
                String resId = pathSegments.get(1); //req.getParams().get("resId");
                MusicTag tag = MusixMateApp.getInstance().getOrmLite().findById(StringUtils.toLong(resId));
                if(tag != null) {
                  //  MimeType mimeType = new MimeType("audio", tag.getAudioEncoding());
                    PlayerInfo player = PlayerInfo.buildStreamPlayer(agent, ContextCompat.getDrawable(getContext(), R.drawable.img_upnp));
                    MusixMateApp.getPlayerControl().setPlayingSong(player, tag);
                    AudioTagPlayingEvent.publishPlayingSong(tag);
                    resp.code(200);
                    resp.setContentType(MimeDetector.getMimeTypeString(tag));
                    ByteBuffer buffer = ByteBuffer.wrap(StreamUtility.readToEndAsArray(new FileInputStream(tag.getPath())));
                    resp.write(new ByteBufferList(buffer));
                    return;
                }
            }catch (Exception ex) {
                Log.w(TAG, "handleResource:"+ ex.getMessage());
            }

          //  resp.getHeaders().add("Content-Type", "text/plain");
            resp.code(404);
            resp.send("File not found");
        }

        private void handleAlbumArt(AsyncHttpServerRequest req, AsyncHttpServerResponse resp, List<String> pathSegments) {
            try {
                String resId = pathSegments.get(1); //req.getParams().get("resId");
                String path = CoverArtProvider.COVER_ARTS + resId;
                File dir = getContext().getExternalCacheDir();
                File pathFile = new File(dir, path);
                if (pathFile.exists()) {
                    resp.code(200);
                    resp.setContentType("image/*");
                    ByteBuffer buffer = ByteBuffer.wrap(StreamUtility.readToEndAsArray(new FileInputStream(pathFile)));
                    resp.write(new ByteBufferList(buffer));
                    return;
                }
            }catch (Exception e) {
                Log.e(TAG, "handleAlbumArt: - not found ", e);
            }
           // resp.getHeaders().add("Content-Type", "image/jpg");
            resp.send("image/jpg", getDefaultIcon());
        }

        @Override
        public void onRequest(AsyncHttpServerRequest request, AsyncHttpServerResponse response) {
            List<String> pathSegments = getPathSegments(request);
            String type = pathSegments.get(0); //req.getParams().get("type");

            if("album".equals(type)) {
                handleAlbumArt(request, response, pathSegments);
            }else if ("res".equals(type)) {
                handleResource(request, response, pathSegments);
            }
        }
    }
}
