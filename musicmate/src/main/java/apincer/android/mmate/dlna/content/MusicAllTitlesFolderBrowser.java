package apincer.android.mmate.dlna.content;

import android.annotation.SuppressLint;
import android.content.Context;
import android.database.Cursor;
import android.provider.MediaStore;
import android.util.Log;

import org.jupnp.support.model.DIDLObject;
import org.jupnp.support.model.PersonWithRole;
import org.jupnp.support.model.Res;
import org.jupnp.support.model.SortCriterion;
import org.jupnp.support.model.container.Container;
import org.jupnp.support.model.container.StorageFolder;
import org.jupnp.support.model.item.MusicTrack;
import org.jupnp.util.MimeType;

import java.net.URI;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import apincer.android.mmate.R;
import apincer.android.mmate.dlna.MediaServerService;
import apincer.android.mmate.dlna.ContentDirectory;

/**
 * Browser for the music all titles folder.
 *
 * @author openbit (Tobias Schoene)
 */
public class MusicAllTitlesFolderBrowser extends ContentBrowser {
    public MusicAllTitlesFolderBrowser(Context context) {
        super(context);
    }

    @Override
    public DIDLObject browseMeta(ContentDirectory contentDirectory,
                                 String myId, long firstResult, long maxResults, SortCriterion[] orderby) {
        /*List<MusicTrack> items = browseItem(contentDirectory, myId, firstResult, maxResults, orderby);
        return new MusicAlbum(
                ContentDirectoryIDs.MUSIC_ALL_TITLES_FOLDER.getId(),
                ContentDirectoryIDs.MUSIC_FOLDER.getId(), getContext().getString(R.string.all), "yaacc",
                getSize(contentDirectory, myId), items);
*/


        return new StorageFolder(myId, ContentDirectoryIDs.MUSIC_ALL_TITLES_FOLDER.getId(), getContext().getString(R.string.all), "mmate", getSize(
                contentDirectory, myId), null);

    }

    private Integer getSize(ContentDirectory contentDirectory, String myId) {

        String[] projection = {MediaStore.Audio.Media._ID};
        String selection = "";
        String[] selectionArgs = null;
        try (Cursor cursor = contentDirectory
                .getContext()
                .getContentResolver()
                .query(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, projection,
                        selection, selectionArgs, null)) {
            return cursor.getCount();
        }

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
        String selection = "";
        String[] selectionArgs = null;
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
                                ContentDirectoryIDs.MUSIC_ALL_TITLES_ITEM_PREFIX.getId()
                                        + id, ContentDirectoryIDs.MUSIC_FOLDER.getId(),
                                title + "-(" + name + ")", "", album, artist, resource);
                        musicTrack.replaceFirstProperty(new DIDLObject.Property.UPNP.ALBUM_ART_URI(
                                albumArtUri));
                        musicTrack.setArtists(new PersonWithRole[]{new PersonWithRole(artist, "AlbumArtist")});
                        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                            @SuppressLint("Range") String genre = mediaCursor.getString(mediaCursor
                                    .getColumnIndex(MediaStore.Audio.Media.GENRE));
                            @SuppressLint("Range") String bitrate = mediaCursor.getString(mediaCursor
                                    .getColumnIndex(MediaStore.Audio.Media.BITRATE));
                            resource.setBitrate(Long.valueOf(bitrate));
                            musicTrack.setGenres(new String[]{genre});
                        }
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
        }
        result.sort(Comparator.comparing(DIDLObject::getTitle));
        return result;

    }

}