package apincer.android.mmate.server;

import android.content.Context;

import net.freeutils.httpserver.HTTPServer;

import java.io.File;
import java.io.IOException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Locale;
import java.util.TimeZone;

import apincer.android.mmate.MusixMateApp;
import apincer.android.mmate.R;
import apincer.android.mmate.broadcast.AudioTagPlayingEvent;
import apincer.android.mmate.broadcast.MusicPlayerInfo;
import apincer.android.mmate.repository.MusicTag;
import apincer.android.mmate.repository.MusicTagRepository;
import apincer.android.mmate.utils.StringUtils;

public class FileContextHandler implements HTTPServer.ContextHandler {

    private Context context;
    protected final DateFormat dateFormatter;
    public FileContextHandler(Context applicationContext) {
        this.context = applicationContext;
        dateFormatter = new SimpleDateFormat("E, d MMM yyyy HH:mm:ss 'GMT'", Locale.US);
        dateFormatter.setTimeZone(TimeZone.getTimeZone("GMT"));
        dateFormatter.setLenient(false);
    }

    @Override
    public int serve(HTTPServer.Request req, HTTPServer.Response resp) throws IOException {
        String uri = req.getPath();
        long id = StringUtils.toLong(uri.substring(uri.lastIndexOf("/")+1, uri.lastIndexOf(".")));
        serveFileContent(id, req, resp);
        return 0;
    }

    private void serveFileContent(long id, HTTPServer.Request req, HTTPServer.Response resp) throws IOException {
        MusicTag tag = MusicTagRepository.getMusicTag(id);
        if(tag == null) {
            resp.sendError(404,"Music not found");
        } else {
            MusicPlayerInfo info = new MusicPlayerInfo("http", "Streaming Player", context.getDrawable(R.drawable.ic_play_arrow_black_24dp));
            MusixMateApp.setPlaying(info, tag);
            AudioTagPlayingEvent.publishPlayingSong(tag);
            HTTPServer.serveFileContent(new File(tag.getPath()), req, resp);
        }
    }
}
