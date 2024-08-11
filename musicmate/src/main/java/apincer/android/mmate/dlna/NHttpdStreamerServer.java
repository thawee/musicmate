package apincer.android.mmate.dlna;

import static fi.iki.elonen.NanoHTTPD.MIME_PLAINTEXT;

import android.content.Context;
import android.util.Log;

import androidx.core.content.ContextCompat;

import org.apache.commons.io.IOUtils;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
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
import fi.iki.elonen.NanoHTTPD;

public class NHttpdStreamerServer {
    private final String ipAddress;
    private final int port;
    private final Context context;
    private static final String TAG = "NHttpdStreamerServer";
    private final List<byte[]> defaultIconRAWs;
    private int currentIconIndex = 0;
    private NanoHTTPD server;

    public NHttpdStreamerServer(Context context, String ipAddress, int port) {
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
        server = new NanoHTTPD(this.port){
            @Override
            public Response serve(IHTTPSession session) {
                List<String> pathSegments = getPathSegments(session);
                String type = pathSegments.get(0); //req.getParams().get("type");

                try {
                    if ("album".equals(type)) {
                        return handleAlbumArt(session, pathSegments);
                    } else if ("res".equals(type)) {
                        return handleResource(session, pathSegments);
                    }
                }catch (IOException ex) {}
                return newFixedLengthResponse(Response.Status.NOT_FOUND, MIME_PLAINTEXT, "Not found");
            }
        };
    }
    public List<String> getPathSegments(NanoHTTPD.IHTTPSession req) {
        String path = req.getUri();
        if(path.startsWith("/")) {
            path = path.substring(1);
        }
        return Arrays.asList(path.split("/", -1));
    }

    private Context getContext() {
        return context;
    }
    public void start() throws IOException {
       // server.start();
        server.start(NanoHTTPD.SOCKET_READ_TIMEOUT, false);
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

        private NanoHTTPD.Response handleResource(NanoHTTPD.IHTTPSession session, List<String> pathSegments) throws IOException {
            try {
                String agent = session.getHeaders().get("User-Agent");
                String resId = pathSegments.get(1); //req.getParams().get("resId");
                MusicTag tag = MusixMateApp.getInstance().getOrmLite().findById(StringUtils.toLong(resId));
                if(tag != null) {
                  //  MimeType mimeType = new MimeType("audio", tag.getAudioEncoding());
                    PlayerInfo player = PlayerInfo.buildStreamPlayer(agent, ContextCompat.getDrawable(getContext(), R.drawable.img_upnp));
                    MusixMateApp.getPlayerControl().setPlayingSong(player, tag);
                    AudioTagPlayingEvent.publishPlayingSong(tag);
                    return NanoHTTPD.newChunkedResponse(NanoHTTPD.Response.Status.OK, MimeDetector.getMimeTypeString(tag), new FileInputStream(tag.getPath())) ;
                }
            }catch (Exception ex) {
                Log.w(TAG, "handleResource:"+ ex.getMessage());
            }

            return NanoHTTPD.newFixedLengthResponse(NanoHTTPD.Response.Status.NOT_FOUND, MIME_PLAINTEXT, "Not found");
        }

        private NanoHTTPD.Response handleAlbumArt(NanoHTTPD.IHTTPSession session, List<String> pathSegments) throws IOException {
            try {
                String resId = pathSegments.get(1); //req.getParams().get("resId");
                String path = CoverArtProvider.COVER_ARTS + resId;
                File dir = getContext().getExternalCacheDir();
                File pathFile = new File(dir, path);
                if (pathFile.exists()) {
                    return NanoHTTPD.newChunkedResponse(NanoHTTPD.Response.Status.OK, "image/*", new FileInputStream(pathFile)) ;
                }
            }catch (Exception e) {
                Log.e(TAG, "handleAlbumArt: - not found ", e);
            }
            return NanoHTTPD.newChunkedResponse(NanoHTTPD.Response.Status.OK, "image/*", new ByteArrayInputStream(getDefaultIcon())) ;
        }
}
