package apincer.android.mmate.musicbrainz;

import android.provider.MediaStore;

import java.io.UnsupportedEncodingException;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import apincer.android.mmate.musicbrainz.coverart.Coverart;
import apincer.android.mmate.musicbrainz.coverart.ImagesItem;
import apincer.android.mmate.musicbrainz.recording.Artist;
import apincer.android.mmate.musicbrainz.recording.ArtistCreditItem;
import apincer.android.mmate.musicbrainz.recording.Recording;
import apincer.android.mmate.musicbrainz.recording.RecordingsItem;
import apincer.android.mmate.musicbrainz.recording.ReleasesItem;
import apincer.android.mmate.objectbox.AudioTag;
import apincer.android.mmate.utils.StringUtils;
import retrofit2.Call;
import retrofit2.Response;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;
import timber.log.Timber;

/**
 * Created by Administrator on 12/20/17.
 */

public class MusicBrainz {
    private static final String MB_URL = "http://musicbrainz.org/";
    private static final String COVER_URL = "http://coverartarchive.org/";

    public static Retrofit createMBRetrofit() {
        return new Retrofit.Builder()
                .baseUrl(MB_URL)
                .addConverterFactory(GsonConverterFactory.create())
                .build();
    }
    public static Retrofit createCoverRetrofit() {
        return new Retrofit.Builder()
                .baseUrl(COVER_URL)
                .addConverterFactory(GsonConverterFactory.create())
                .build();
    }

    public static Retrofit createRetrofit() {
        return new Retrofit.Builder()
                .addConverterFactory(GsonConverterFactory.create())
                .build();
    }

    public static List<AudioTag> findSongInfo(String title, String artist, String album) {
        final List<AudioTag> songs = new ArrayList<>();
         try {
             Recording recording = fetchRecordings(title, artist, album);
            // if(!StringUtils.isEmpty(title)) {
           //      recording = fetchRecordings(title, artist, album);
            // }else {
            //     recording = fetchRecordings(title, artist, album);
            // }
            if(!hasRecordings(recording) && !StringUtils.isEmpty(title)) {
                // trying with title only
                recording = fetchRecordings(title,null,null);
            }

            //prevent NPE
            if(recording==null) return songs;

            for(RecordingsItem item  : recording.getRecordings()) {
                AudioTag song = new AudioTag();
                        song.setMusicBrainzId(item.getId());
                        song.setTitle(item.getTitle());
                        parseArtist(song, item);
                        parseAlbum(song, item);
                        songs.add(song);
            }
        }catch (Exception ex) {
             Timber.e(ex);
        }
        return songs;
    }

    private static boolean hasRecordings(Recording recording) {
       return !(recording==null || recording.getRecordings().size()==0);
    }

    private static Recording fetchRecordings(String title, String artist, String album) {
        String query = "";
        try {
            query = createQuery(title, artist, album);
            if(StringUtils.isEmpty(query))  {
                return null;
            }
            Retrofit retrofit = createMBRetrofit();
            EndpointInterface eIntf = retrofit.create(EndpointInterface.class);
            Map<String, String> data = new HashMap<>();
            data.put("fmt", "json");
            data.put("limit", String.valueOf(25));
            data.put("query", query);
           // LogHelper.logToFile("musicBrainz", "query by: "+query);
            Call call =  eIntf.findRecordings(data);
            Response response =  call.execute();
            return  (Recording) response.body();
            /*
            final List<Recording> recordings = new ArrayList<>();
            call.enqueue(new Callback() {
                @Override
                public void onResponse(Call call, Response response) {
                    recordings.add((Recording) response.body());
                }

                @Override
                public void onFailure(Call call, Throwable t) {

                }
            });
            return recordings.size()>0?recordings.get(0):null;
           // return  (Recording) response.body();*/
        }catch (Exception ex) {
            Timber.d(ex);
        }
        return null;
    }

    public static URL getCoverart(AudioTag album) {
        try {
            Retrofit retrofit = createCoverRetrofit();
            EndpointInterface eIntf = retrofit.create(EndpointInterface.class);
            Call call = eIntf.getCoverart(album.getAlbumId());
            Response response = call.execute();
            Coverart coverart = (Coverart) response.body();
            List<ImagesItem> images = coverart.getImages();
            for(ImagesItem image: images) {
                if(image.isFront()) {
                    if(image.getThumbnails()!=null) {
                        if(image.getThumbnails().getLarge()!=null) {
                            return new URL(image.getThumbnails().getLarge());
                        }else if(image.getThumbnails().getSmall()!=null) {
                            return new URL(image.getThumbnails().getSmall());
                        }
                        //album.smallCoverUrl = image.getThumbnails().getSmall();
                       // album.largeCoverUrl = image.getThumbnails().getLarge();
                       // return true;
                    }
                }
            }
        }catch (Exception ex) {
            Timber.e(ex);
            //LogHelper.logToFile("GetCoverArt", "No coverart for "+album.name);
            //ex.printStackTrace();
        }
        return null;
    }

    private static void parseArtist(AudioTag song, RecordingsItem item) {
        List<ArtistCreditItem> artistCreditList = item.getArtistCredit();
        if(artistCreditList!=null && artistCreditList.size()>0) {
            for(ArtistCreditItem artistCreditItem:artistCreditList) {
                 Artist artist = artistCreditItem.getArtist();
                 if(artist!=null && !StringUtils.isEmpty(artist.getName())) {
                     song.setArtist(artist.getName());
                     song.setArtistId(artist.getId());
                     break;
                 }
            }
        }
    }

    private static void parseAlbum(AudioTag song, RecordingsItem item) {
        List<ReleasesItem> releaseList = item.getReleases();
        if(releaseList!=null && releaseList.size()>0) {
            ReleasesItem release =  releaseList.get(0);
            song.setAlbumId(release.getId());
            song.setAlbum(release.getTitle());
            song.setYear(release.getDate());
        }
    }

    private static String createQuery(String title, String artist, String album) throws UnsupportedEncodingException {
        String query = "";
        if (!StringUtils.isEmpty(title)) {
            //query += URLEncoder.encode(  surroundWithQuotes(title), "UTF-8");
            query += surroundWithQuotes(title);
            //query += surroundWithQuotes(URLEncoder.encode(title, "UTF-8"));
        }
        if (!StringUtils.isEmpty(artist)) {
            //query += URLEncoder.encode(" AND artist:" + surroundWithQuotes(artist) , "UTF-8");
            query += " AND artist:" + surroundWithQuotes(artist);
            //query += " AND artist:" + surroundWithQuotes(URLEncoder.encode(artist, "UTF-8"));
        }
        if (!StringUtils.isEmpty(album)) {
            //query += URLEncoder.encode(" AND release:" + surroundWithQuotes(album), "UTF-8");
            query += " AND release:" + surroundWithQuotes(album);
            //query += " AND release:" + surroundWithQuotes(URLEncoder.encode(album, "UTF-8"));
        }
        return query;
    }

    private static String surroundWithQuotes(String s) {
        String s2 = s.replaceAll("\"", ""); // remove all quotes in between
        return "\"" + s2 + "\"";
    }
/*
    public static AlbumInfo getAlbumArt(AlbumInfo album) {
        if(fetchCoverart(album)) {
            return album;
        }
        return album;
    }

    public static InputStream getInputStream(String url) {
        try {
            OkHttpClient client = new OkHttpClient();
            okhttp3.Call call = client.newCall(new Request.Builder().url(url).get().build());
            okhttp3.Response response = call.execute();
            return new BufferedInputStream((response.body()).byteStream(), 1024 * 8);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    } */
/*
    public static Collection<? extends AlbumInfo> populateAlbumInfo(List<RecordingItem> songs) {
        List<AlbumInfo> albums = new ArrayList<>();
        Map<String, String> albumMap = new HashMap<>();
        for(RecordingItem song: songs) {
            if(!albumMap.containsKey(song.albumId)) {
                albumMap.put(song.albumId, song.album);
                AlbumInfo album = new AlbumInfo();
                album.id = song.albumId;
                album.name = song.album;
                albums.add(album);
            }
        }
        return albums;
    } */
}
