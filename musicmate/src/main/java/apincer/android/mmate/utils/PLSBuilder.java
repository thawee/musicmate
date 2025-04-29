package apincer.android.mmate.utils;

import static apincer.android.mmate.dlna.content.CollectionsBrowser.DOWNLOADS_SONGS;
import static apincer.android.mmate.dlna.content.CollectionsBrowser.SMART_LIST_BAANTHUNG_SONGS;
import static apincer.android.mmate.dlna.content.CollectionsBrowser.SMART_LIST_CLASSIC_SONGS;
import static apincer.android.mmate.dlna.content.CollectionsBrowser.SMART_LIST_FINFIN_SONGS;
import static apincer.android.mmate.dlna.content.CollectionsBrowser.SMART_LIST_ISAAN_SONGS;
import static apincer.android.mmate.dlna.content.CollectionsBrowser.SMART_LIST_VOCAL_SONGS;

import android.content.Context;

import com.anggrayudi.storage.file.DocumentFileCompat;
import com.anggrayudi.storage.file.StorageId;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

import apincer.android.mmate.repository.MusicTag;
import apincer.android.mmate.repository.TagRepository;

public class PLSBuilder {
    public static String build(String baseUri, List<MusicTag> tags) {
        StringBuilder buff = new StringBuilder();
        buff.append("[playlist]").append("\n");
        int fileCnt =1;
        baseUri = buildUri(baseUri);
        for(MusicTag tag: tags) {
            try {
                String filename = getFilename(tag);
                String uri = String.format(Locale.US, "%s/%s", baseUri, filename);
                String link = new URI(null, uri, null).toASCIIString();

                buff.append(String.format(Locale.US, "File%d=%s", fileCnt, link)).append("\n");
                buff.append(String.format(Locale.US, "Title%d=%s", fileCnt, getTitle(tag))).append("\n");
                buff.append(String.format(Locale.US, "Length%d=%.0f", fileCnt, tag.getAudioDuration())).append("\n");
            } catch (URISyntaxException e) {
                throw new RuntimeException(e);
            }
            fileCnt++;
        }
        buff.append("NumberOfEntries=").append(tags.size()).append("\n");
        buff.append("Version=2").append("\n");
        return buff.toString();
    }

    private static Object getTitle(MusicTag tag) {
        return String.format(Locale.US, "%s - %s", tag.getArtist(), tag.getTitle());
    }

    private static String buildUri(String uri) {
        if(!uri.endsWith("/")) {
            uri += "/";
        }
        return uri;
    }

    private static String getFilename(MusicTag tag) {
      return tag.getId()+ "." + tag.getFileType();
    }

    public static void exportPlaylists(Context context) {
        List<MusicTag> songs = TagRepository.getAllMusicsForPlaylist();
        Map<String,List<MusicTag>> mapped = new HashMap<>();
        mapped.put(DOWNLOADS_SONGS, new ArrayList<>());
        mapped.put(SMART_LIST_ISAAN_SONGS, new ArrayList<>());
       // mapped.put(SMART_LIST_RELAXED_TH_SONGS, new ArrayList<>());
        mapped.put(SMART_LIST_VOCAL_SONGS, new ArrayList<>());
        mapped.put(SMART_LIST_FINFIN_SONGS, new ArrayList<>());
        mapped.put(SMART_LIST_BAANTHUNG_SONGS, new ArrayList<>());
        mapped.put(SMART_LIST_CLASSIC_SONGS, new ArrayList<>());
      //  mapped.put(SMART_LIST_INDIE_SONGS, new ArrayList<>());

        for(MusicTag tag: songs) {
            if(MusicTagUtils.isOnDownloadDir(tag)) {
                Objects.requireNonNull(mapped.get(DOWNLOADS_SONGS)).add(tag);
            }
            if(MusicTagUtils.isISaanPlaylist(tag)) {
                Objects.requireNonNull(mapped.get(SMART_LIST_ISAAN_SONGS)).add(tag);
            }
            if(MusicTagUtils.isVocalPlaylist(tag)) {
                Objects.requireNonNull(mapped.get(SMART_LIST_VOCAL_SONGS)).add(tag);
            }
            if(MusicTagUtils.isFinFinPlaylist(tag)) {
                Objects.requireNonNull(mapped.get(SMART_LIST_FINFIN_SONGS)).add(tag);
            }
            if(MusicTagUtils.isBaanThungPlaylist(tag)) {
                Objects.requireNonNull(mapped.get(SMART_LIST_BAANTHUNG_SONGS)).add(tag);
            }
            if(MusicTagUtils.isClassicPlaylist(tag)) {
                Objects.requireNonNull(mapped.get(SMART_LIST_CLASSIC_SONGS)).add(tag);
            }
        }

       // mapped.put(DUPLICATED_SONGS, MusixMateApp.getInstance().getOrmLite().findDuplicateSong());

        for(String name: mapped.keySet()) {
            exportPlaylist(context, name, mapped.get(name));
        }
    }
    private static void exportPlaylist(Context context, String name, List<MusicTag> currentSelections) {
        /*
        #EXTM3U
        #PLAYLIST: The title of the playlist

        #EXTINF:111, Sample artist name - Sample track title
        C:\Music\SampleMusic.mp3

        #EXTINF:222,Example Artist name - Example track title
        C:\Music\ExampleMusic.mp3
         */
        Writer out = null;
        try {
           // SimpleDateFormat simpleDateFormat = new SimpleDateFormat("yyyyMMdd", Locale.US);
            String path = "/Playlist/"+name+".m3u";
            path = DocumentFileCompat.buildAbsolutePath(context, StorageId.PRIMARY, path);
            File filepath = new File(path);
            File folder = filepath.getParentFile();
            if(folder !=null && !folder.exists()) {
                folder.mkdirs();
            }
            out = new BufferedWriter(new OutputStreamWriter(
                    new FileOutputStream(filepath,true), StandardCharsets.UTF_8));
            out.write("#EXRM3U\n");
            out.write("#PLAYLIST: "+name+"\n\n");

            for (MusicTag tag:currentSelections) {
                out.write("#EXTINF:"+tag.getAudioDuration()+","+tag.getArtist()+","+tag.getTitle()+"\n");
                out.write(tag.getPath()+"\n\n");
            }
        } catch (Exception ignored) {
        } finally {
            try {
                if(out != null) out.close();
            }catch (Exception ignored) {}
        }
    }

}
