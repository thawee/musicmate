package apincer.android.mmate.dlna;

import static apincer.android.mmate.utils.StringUtils.isEmpty;

import android.content.Context;

import net.freeutils.httpserver.HTTPServer;

import java.io.File;
import java.io.IOException;

import apincer.android.mmate.MusixMateApp;
import apincer.android.mmate.R;
import apincer.android.mmate.broadcast.AudioTagPlayingEvent;
import apincer.android.mmate.broadcast.MusicPlayerInfo;
import apincer.android.mmate.dlna.model.ItemNode;
import apincer.android.mmate.dlna.model.NodesMap;
import apincer.android.mmate.fs.MusicCoverArtProvider;
import apincer.android.mmate.repository.MusicTag;
import apincer.android.mmate.repository.MusicTagRepository;
import apincer.android.mmate.utils.StringUtils;

public class HttpContextHandler implements HTTPServer.ContextHandler {
    public static String ERROR_ACCESS_DENIED = "Access denied";
    public static String ERROR_RESOURCE_NOT_FOUND="Error 404, file <id> not found";
    Context context;
    public HttpContextHandler(Context context) {
        this.context = context;
    }

    @Override
    public int serve(HTTPServer.Request req, HTTPServer.Response resp) throws IOException {
        try {
            String contextPath = req.getContext().getPath(); //path:  /music/id - arist - title.flac
            String relativePath = req.getPath().substring(contextPath.length());

           /* if (relativePath.startsWith("/")) {
                // remove leading '/'
                relativePath = relativePath.substring(1);
            } */
            if(isEmpty(relativePath) || "/".equals(relativePath) || "/index.html".equals(relativePath)) {
                resp.sendError(404, ERROR_RESOURCE_NOT_FOUND.replace("<id>", relativePath));
            }

            final ItemNode node = (ItemNode) NodesMap.get(relativePath.replaceFirst("/", ""));
            if (node == null) {
                resp.sendError(404, ERROR_RESOURCE_NOT_FOUND.replace("<id>", relativePath));
            }

            int resType = 0; // 1= music, 2=coverart, 3= playlist
            if(relativePath.startsWith("/music/")) {
                relativePath = relativePath.substring("/music/".length());
                resType = 1;
            }else if(relativePath.startsWith("/coverart/")) {
                relativePath = relativePath.substring("/coverart/".length());
                resType = 2;
            }else if(relativePath.startsWith("/playlist/")) {
                String [] paths = relativePath.split("/", -1);
                String playlistType = paths[2]; // i.e. genre, grouping, mymusic
            }else {
                resp.send(400, ERROR_ACCESS_DENIED);
            }

            String idStr = relativePath;
            //   /music/id/file.flac
            //   /coverart/id/file.png
            if (relativePath.contains("/")) {
                // support; id/artist - title.flac
                idStr = relativePath.substring(0, relativePath.indexOf("/"));
            }
            if (relativePath.contains("-")) {
                // support; id - artist - title.flac
                idStr = relativePath.substring(0, relativePath.indexOf("-"));
            }
            if (relativePath.contains(".")) {
                // support; id.flac
                idStr = relativePath.substring(0, relativePath.indexOf("."));
            }
            long id = StringUtils.toLong(idStr);
            MusicTag tag = MusicTagRepository.getMusicTag(id);
            if(tag == null) {
                resp.sendError(404, ERROR_RESOURCE_NOT_FOUND.replace("<id>", "music "+id));
            }else if(resType ==1){
                // send playing event
                MusicPlayerInfo info = new MusicPlayerInfo("http", "Streaming Player", context.getDrawable(R.drawable.ic_play_arrow_black_24dp));
                MusixMateApp.setPlaying(info, tag);
                AudioTagPlayingEvent.publishPlayingSong(tag);
                HTTPServer.serveFileContent(new File(tag.getPath()), req, resp);
            }else if(resType ==2){
                File dir = context.getExternalCacheDir();
                String path = MusicCoverArtProvider.getCacheCover(new File(tag.getPath()));
                File pathFile = new File(dir, path);
                if (pathFile.exists()) {
                    HTTPServer.serveFileContent(pathFile, req, resp);
                } else {
                    resp.sendError(404, ERROR_RESOURCE_NOT_FOUND.replace("<id>", "coverart "+id));
                }
            }
        }catch (Exception ex) {
            resp.sendError(400, "Invalid request: " + ex.getMessage());
        }
        return 0;
    }
}
