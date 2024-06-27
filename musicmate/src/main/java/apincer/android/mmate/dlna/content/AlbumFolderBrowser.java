package apincer.android.mmate.dlna.content;

import android.annotation.SuppressLint;
import android.content.Context;

import org.jupnp.support.model.DIDLObject;
import org.jupnp.support.model.SortCriterion;
import org.jupnp.support.model.container.Container;
import org.jupnp.support.model.container.StorageFolder;
import org.jupnp.support.model.item.MusicTrack;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import apincer.android.mmate.Constants;
import apincer.android.mmate.MusixMateApp;
import apincer.android.mmate.dlna.ContentDirectory;
import apincer.android.mmate.repository.MusicTag;
import apincer.android.mmate.utils.StringUtils;

/**
 * Browser for a music genre folder.
 *
 * @author openbit (Tobias Schoene)
 */
public class AlbumFolderBrowser extends ContentBrowser {
    // album (by artist)
   // private Pattern pattern = Pattern.compile("(?i)(\\w+)\\s*\\(\\s*by\\s*(\\w+)\\s*\\)");
    //his is album (by this is artist)
    private Pattern pattern = Pattern.compile("(?i)(.*)\\s*\\(by\\s*(.*)\\)");
    public AlbumFolderBrowser(Context context) {
        super(context);
    }

    @Override
    public DIDLObject browseMeta(ContentDirectory contentDirectory,
                                 String myId, long firstResult, long maxResults, SortCriterion[] orderby) {
        return new StorageFolder(myId, ContentDirectoryIDs.MUSIC_ALBUMS_FOLDER.getId(), myId, "mmate", getSize(
                contentDirectory, myId), null);
        /* return new StorageFolder(myId,
                ContentDirectoryIDs.MUSIC_ALBUMS_FOLDER.getId(), getName(
                contentDirectory, myId), "mmate", getSize(
                contentDirectory, myId),
                null); */
    }
/*
    private String getName(ContentDirectory contentDirectory, String myId) {
        String result = "";
        String[] projection = {MediaStore.Audio.Albums.ALBUM};
        String selection = MediaStore.Audio.Albums._ID + "=?";
        String[] selectionArgs = new String[]{myId
                .substring(ContentDirectoryIDs.MUSIC_ALBUM_PREFIX.getId()
                .length())};
        try (Cursor cursor = contentDirectory
                .getContext()
                .getContentResolver()
                .query(MediaStore.Audio.Albums.EXTERNAL_CONTENT_URI,
                        projection, selection, selectionArgs, null)) {

            if (cursor != null && cursor.getCount() > 0) {
                cursor.moveToFirst();
                result = cursor.getString(0);

            }
        }
        return result;
    } */

    private Integer getSize(ContentDirectory contentDirectory, String myId) {
        String name = myId.substring(ContentDirectoryIDs.MUSIC_ALBUM_PREFIX.getId().length());
        String album = "";
        String albumArtist = "";
        if("_EMPTY".equalsIgnoreCase(name) ||
                "_NULL".equalsIgnoreCase(name) ||
                Constants.UNKNOWN.equalsIgnoreCase(name)) {
            album = "";
            albumArtist = "";
        }else {
            // split album and albumArtist - album (by artist)
            Matcher matcher = pattern.matcher(name);
            if (matcher.find()) {
                album = StringUtils.trimToEmpty(matcher.group(1));
                albumArtist = StringUtils.trimToEmpty(matcher.group(2));
            }else {
                album = name;
            }
        }
        return MusixMateApp.getInstance().getOrmLite().findByAlbumAndAlbumArtist(album, albumArtist).size();
        /*
        String[] projection = {MediaStore.Audio.Media.ALBUM_ID};
        String selection = MediaStore.Audio.Media.ALBUM_ID + "=?";
        String[] selectionArgs = new String[]{myId
                .substring(ContentDirectoryIDs.MUSIC_ALBUM_PREFIX.getId()
                .length())};
        try (Cursor cursor = contentDirectory
                .getContext()
                .getContentResolver()
                .query(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, projection,
                        selection, selectionArgs, null)) {
            return cursor.getCount();
        } */

    }

    @Override
    public List<Container> browseContainer(
            ContentDirectory contentDirectory, String myId, long firstResult, long maxResults, SortCriterion[] orderby) {

        return new ArrayList<>();
    }

    @SuppressLint("Range")
    @Override
    public List<MusicTrack> browseItem(ContentDirectory contentDirectory,
                                       String myId, long firstResult, long maxResults, SortCriterion[] orderby) {

        List<MusicTrack> result = new ArrayList<>();
        String name = myId.substring(ContentDirectoryIDs.MUSIC_ALBUM_PREFIX.getId().length());
        String album = "";
        String albumArtist = "";
        if("_EMPTY".equalsIgnoreCase(name) ||
                "_NULL".equalsIgnoreCase(name) ||
                Constants.UNKNOWN.equalsIgnoreCase(name)) {
            album = "";
            albumArtist = "";
        }else {
            // split album and albumArtist - album (by artist)
            Matcher matcher = pattern.matcher(name);
            if (matcher.find()) {
                album = StringUtils.trimToEmpty(matcher.group(1));
                albumArtist = StringUtils.trimToEmpty(matcher.group(2));
            }else {
                album = name;
            }
        }

        List<MusicTag> tags = MusixMateApp.getInstance().getOrmLite().findByAlbumAndAlbumArtist(album, albumArtist);
        int currentCount = 0;
        for(MusicTag tag: tags) {
            if ((currentCount >= firstResult) && currentCount < (firstResult+maxResults)){
                MusicTrack musicTrack = toMusicTrack(contentDirectory, tag, myId, ContentDirectoryIDs.MUSIC_ALBUM_ITEM_PREFIX.getId());
                result.add(musicTrack);
            }
            currentCount++;
        }
        /*
        String[] projection;
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
            projection = new String[]{MediaStore.Audio.Media._ID,
                    MediaStore.Audio.Media.DISPLAY_NAME,
                    MediaStore.Audio.Media.MIME_TYPE,
                    MediaStore.Audio.Media.SIZE,
                    MediaStore.Audio.Media.ALBUM,
                    MediaStore.Audio.Media.ALBUM_ID,
                    MediaStore.Audio.Media.TITLE,
                    MediaStore.Audio.Media.ARTIST,
                    MediaStore.Audio.Media.DURATION,
                    MediaStore.Audio.Media.BITRATE,
                    MediaStore.Audio.Media.GENRE};
        } else {
            projection = new String[]{MediaStore.Audio.Media._ID,
                    MediaStore.Audio.Media.DISPLAY_NAME,
                    MediaStore.Audio.Media.MIME_TYPE,
                    MediaStore.Audio.Media.SIZE,
                    MediaStore.Audio.Media.ALBUM,
                    MediaStore.Audio.Media.ALBUM_ID,
                    MediaStore.Audio.Media.TITLE,
                    MediaStore.Audio.Media.ARTIST,
                    MediaStore.Audio.Media.DURATION};
        }
        String selection = MediaStore.Audio.Media.ALBUM_ID + "=?";
        String[] selectionArgs = new String[]{myId
                .substring(ContentDirectoryIDs.MUSIC_ALBUM_PREFIX.getId()
                .length())};
        try (Cursor mediaCursor = contentDirectory
                .getContext()
                .getContentResolver()
                .query(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, projection,
                        selection, selectionArgs, MediaStore.Audio.Media.DISPLAY_NAME + " ASC")) {

            if (mediaCursor != null && mediaCursor.getCount() > 0) {
                mediaCursor.moveToFirst();
                int currentIndex = 0;
                int currentCount = 0;
                while (!mediaCursor.isAfterLast() && currentCount < maxResults) {
                    if (firstResult <= currentIndex) {
                        @SuppressLint("Range") String id = mediaCursor.getString(mediaCursor
                                .getColumnIndex(MediaStore.Audio.Media._ID));
                        @SuppressLint("Range") String name = mediaCursor.getString(mediaCursor
                                .getColumnIndex(MediaStore.Audio.Media.DISPLAY_NAME));
                        @SuppressLint("Range") Long size = Long.valueOf(mediaCursor.getString(mediaCursor
                                .getColumnIndex(MediaStore.Audio.Media.SIZE)));

                        @SuppressLint("Range") String album = mediaCursor.getString(mediaCursor
                                .getColumnIndex(MediaStore.Audio.Media.ALBUM));
                        @SuppressLint("Range") String albumId = mediaCursor.getString(mediaCursor
                                .getColumnIndex(MediaStore.Audio.Media.ALBUM_ID));
                        @SuppressLint("Range") String title = mediaCursor.getString(mediaCursor
                                .getColumnIndex(MediaStore.Audio.Media.TITLE));
                        @SuppressLint("Range") String artist = mediaCursor.getString(mediaCursor
                                .getColumnIndex(MediaStore.Audio.Media.ARTIST));
                        @SuppressLint("Range") String duration = mediaCursor.getString(mediaCursor
                                .getColumnIndex(MediaStore.Audio.Media.DURATION));
                        duration = contentDirectory.formatDuration(duration);
                        Log.d(getClass().getName(),
                                "Mimetype: "
                                        + mediaCursor.getString(mediaCursor
                                        .getColumnIndex(MediaStore.Audio.Media.MIME_TYPE)));

                        MimeType mimeType = MimeType
                                .valueOf(mediaCursor.getString(mediaCursor
                                        .getColumnIndex(MediaStore.Audio.Media.MIME_TYPE)));
                        // file parameter only needed for media players which decide
                        // the
                        // ability of playing a file by the file extension
                        String uri = getUriString(contentDirectory, id, mimeType);
                        URI albumArtUri = URI.create("http://"
                                + contentDirectory.getIpAddress() + ":"
                                + MediaServerService.CONTENT_SERVER_PORT + "/album/" + albumId);
                        Res resource = new Res(mimeType, size, uri);
                        resource.setDuration(duration);

                        MusicTrack musicTrack = new MusicTrack(
                                ContentDirectoryIDs.MUSIC_ALBUM_ITEM_PREFIX.getId()
                                        + id,
                                ContentDirectoryIDs.MUSIC_ALBUM_PREFIX.getId()
                                        + albumId, title, "",
                                album, artist, resource);
                        musicTrack.replaceFirstProperty(new DIDLObject.Property.UPNP.ALBUM_ART_URI(
                                albumArtUri));
                        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                            @SuppressLint("Range") String genre = mediaCursor.getString(mediaCursor
                                    .getColumnIndex(MediaStore.Audio.Media.GENRE));
                            @SuppressLint("Range") String bitrate = mediaCursor.getString(mediaCursor
                                    .getColumnIndex(MediaStore.Audio.Media.BITRATE));
                            resource.setBitrate(Long.valueOf(bitrate));
                            musicTrack.setGenres(new String[]{genre});
                        }
                        musicTrack.setArtists(new PersonWithRole[]{new PersonWithRole(artist, "AlbumArtist")});

                        result.add(musicTrack);
                        Log.d(getClass().getName(), "MusicTrack: " + id + " Name: "
                                + name + " uri: " + uri);
                        currentCount++;
                    }
                    currentIndex++;
                    mediaCursor.moveToNext();
                }

            } else {
                Log.d(getClass().getName(), "System media store is empty.");
            }
        } */

        result.sort(Comparator.comparing(DIDLObject::getTitle));

        return result;

    }

}