package apincer.android.mmate.share;

import android.content.Context;

import net.freeutils.httpserver.HTTPServer;

import java.io.File;
import java.io.IOException;

import apincer.android.mmate.fs.MusicCoverArtProvider;
import apincer.android.mmate.repository.MusicTag;
import apincer.android.mmate.repository.MusicTagRepository;
import apincer.android.mmate.utils.StringUtils;

public class ImageContextHandler implements HTTPServer.ContextHandler {
    Context context;
    public ImageContextHandler(Context applicationContext) {
        this.context = applicationContext;
    }

    @Override
    public int serve(HTTPServer.Request req, HTTPServer.Response resp) throws IOException {
        try {
            String contextPath = req.getContext().getPath(); //path:  /playlists/id - arist - title.flac
            String relativePath = req.getPath().substring(contextPath.length());
            if (relativePath.startsWith("/")) {
                relativePath = relativePath.substring(1);
            }
            if(relativePath.length()==0 || relativePath.endsWith("index.html")) {
                resp.send(200, "MusicMate HTTP Server");
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
                resp.sendError(404, "Invalid request: image file not found");
            }else {
                // send playing event
                File dir =  context.getExternalCacheDir();
                String path = MusicCoverArtProvider.getCacheCover(new File(tag.getPath()));
                File pathFile = new File(dir, path);
                if(pathFile.exists()) {
                    HTTPServer.serveFileContent(pathFile, req, resp);
                }else {
                    resp.sendError(404, "No image");
                }
            }
        }catch (Exception ex) {
            resp.sendError(400, "Invalid request: " + ex.getMessage());
        }

        return 0;
    }
}
