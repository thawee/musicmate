package apincer.android.mmate.dlna;

import android.content.Context;
import android.util.Log;

import androidx.core.content.ContextCompat;

import net.freeutils.httpserver.HTTPServer;

import org.apache.commons.io.IOUtils;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
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

public class JLHttpStreamerServer {
    private final String ipAddress;
    private final int port;
    private final Context context;
    private static final String TAG = "JLHttpStreamerServer";
    private final List<byte[]> defaultIconRAWs;
    private int currentIconIndex = 0;
    private HTTPServer server;

    public JLHttpStreamerServer(Context context, String ipAddress, int port) {
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
        server = new HTTPServer(this.port);
        server.setSocketTimeout(300);
        HTTPServer.VirtualHost host = server.getVirtualHost(null);  // default virtual host
        host.setAllowGeneratedIndex(false);
        host.addContext("/{*}", new ResHandler());
    }

    private Context getContext() {
        return context;
    }
    public void start() throws IOException {
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

    private class ResHandler implements HTTPServer.ContextHandler {
        @Override
        public int serve(HTTPServer.Request req, HTTPServer.Response resp) throws IOException {
            List<String> pathSegments = getPathSegments(req);
            String type = pathSegments.get(0); //req.getParams().get("type");

            if("album".equals(type)) {
                handleAlbumArt(req, resp, pathSegments);
            }else if ("res".equals(type)) {
                handleResource(req, resp, pathSegments);
            }

            return 0;
        }

        public List<String> getPathSegments(HTTPServer.Request req) {
            String path = req.getURI().getPath();
            if(path.startsWith("/")) {
                path = path.substring(1);
            }
            return Arrays.asList(path.split("/", -1));
        }

        private void handleResource(HTTPServer.Request req, HTTPServer.Response resp, List<String> pathSegments) throws IOException {
            try {
                String agent = req.getHeaders().get("User-Agent");
                String resId = pathSegments.get(1); //req.getParams().get("resId");
                MusicTag tag = MusixMateApp.getInstance().getOrmLite().findById(StringUtils.toLong(resId));
                if(tag != null) {
                  //  MimeType mimeType = new MimeType("audio", tag.getAudioEncoding());
                    MusicPlayerInfo player = MusicPlayerInfo.buildStreamPlayer(agent, ContextCompat.getDrawable(getContext(), R.drawable.img_upnp));
                    MusixMateApp.setPlaying(player, tag);
                    AudioTagPlayingEvent.publishPlayingSong(tag);
                    HTTPServer.serveFileContent(new File(tag.getPath()), req, resp);
                    return;
                }
            }catch (Exception ex) {
                Log.w(TAG, "handleResource:"+ ex.getMessage());
            }

          //  resp.getHeaders().add("Content-Type", "text/plain");
            resp.sendError(404, "File not found");
        }

        private void handleAlbumArt(HTTPServer.Request req, HTTPServer.Response resp, List<String> pathSegments) throws IOException {
            try {
                String resId = pathSegments.get(1); //req.getParams().get("resId");
                String path = CoverArtProvider.COVER_ARTS + resId;
                File dir = getContext().getExternalCacheDir();
                File pathFile = new File(dir, path);
                if (pathFile.exists()) {
                    HTTPServer.serveFileContent(pathFile, req, resp);
                    return;
                }
            }catch (Exception e) {
                Log.e(TAG, "handleAlbumArt: - not found ", e);
            }
            resp.getHeaders().add("Content-Type", "image/jpg");
            resp.sendHeaders(200);
            resp.getOutputStream().write(getDefaultIcon());
            resp.getOutputStream().close();
        }
    }
}
