package apincer.android.mmate.share;

import static apincer.android.mmate.utils.StringUtils.isEmpty;

import android.content.Context;

import net.freeutils.httpserver.HTTPServer;

import java.io.File;
import java.io.IOException;

import apincer.android.mmate.MusixMateApp;
import apincer.android.mmate.R;
import apincer.android.mmate.broadcast.AudioTagPlayingEvent;
import apincer.android.mmate.broadcast.MusicPlayerInfo;
import apincer.android.mmate.repository.MusicTag;
import apincer.android.mmate.repository.MusicTagRepository;
import apincer.android.mmate.utils.StringUtils;

public class MusicContextHandler implements HTTPServer.ContextHandler {
    Context context;
    public MusicContextHandler(Context context) {
        this.context = context;
    }

    @Override
    public int serve(HTTPServer.Request req, HTTPServer.Response resp) throws IOException {
        try {
            String contextPath = req.getContext().getPath(); //path:  /music/id - arist - title.flac
            String relativePath = req.getPath().substring(contextPath.length());
            if (relativePath.startsWith("/")) {
                relativePath = relativePath.substring(1);
            }
            if(relativePath.length()==0 || relativePath.endsWith("index.html")) {
                resp.send(200, "MusicMate HTTP Music Streaming Server, please generate playlist from MusicMate Application.");
            }
            String idStr = relativePath;
            if (relativePath.contains("/")) {
                // support; id/artist - title.flac
                idStr = relativePath.substring(0, relativePath.indexOf("/"));
            }
            if (relativePath.contains("-")) {
                // support; id - artist - title.flac
                idStr = relativePath.substring(0, relativePath.indexOf("-"));
            }
            if (relativePath.contains(".")) {
                // support; id..flac
                idStr = relativePath.substring(0, relativePath.indexOf("."));
            }
            long id = StringUtils.toLong(idStr);
            MusicTag tag = MusicTagRepository.getMusicTag(id);
            if(tag == null) {
                resp.sendError(404, "Invalid request: Music file not found");
            }else {
                // send playing event
                MusicPlayerInfo info = new MusicPlayerInfo("http", "HTTP Player", context.getDrawable(R.drawable.ic_play_arrow_black_24dp));
                MusixMateApp.setPlaying(info, tag);
                AudioTagPlayingEvent.publishPlayingSong(tag);
                HTTPServer.serveFileContent(new File(tag.getPath()), req, resp);
            }
        }catch (Exception ex) {
            resp.sendError(400, "Invalid request: " + ex.getMessage());
        }
        return 0;
    }
}
