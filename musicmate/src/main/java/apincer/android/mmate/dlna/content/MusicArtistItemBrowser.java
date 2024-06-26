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
import org.jupnp.support.model.item.Item;
import org.jupnp.support.model.item.MusicTrack;
import org.jupnp.util.MimeType;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;

import apincer.android.mmate.dlna.MediaServerService;
import apincer.android.mmate.dlna.ContentDirectory;


/**
 * Browser for a music artist item.
 *
 * @author openbit (Tobias Schoene)
 */
public class MusicArtistItemBrowser extends ContentBrowser {

    public MusicArtistItemBrowser(Context context) {
        super(context);
    }

    @Override
    public DIDLObject browseMeta(ContentDirectory contentDirectory,
                                 String myId, long firstResult, long maxResults, SortCriterion[] orderby) {
        Item result = null;
        String[] projection;
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
            projection = new String[]{MediaStore.Audio.Media._ID,
                    MediaStore.Audio.Media.DISPLAY_NAME,
                    MediaStore.Audio.Media.MIME_TYPE,
                    MediaStore.Audio.Media.SIZE,
                    MediaStore.Audio.Media.ALBUM,
                    MediaStore.Audio.Media.ALBUM_ID,
                    MediaStore.Audio.Media.TITLE,
                    MediaStore.Audio.Media.ARTIST_ID,
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
                    MediaStore.Audio.Media.ARTIST_ID,
                    MediaStore.Audio.Media.ARTIST,
                    MediaStore.Audio.Media.DURATION};
        }
        String selection = MediaStore.Audio.Media._ID + "=?";
        String[] selectionArgs = new String[]{myId
                .substring(ContentDirectoryIDs.MUSIC_ARTIST_ITEM_PREFIX.getId()
                .length())};
        try (Cursor mediaCursor = contentDirectory
                .getContext()
                .getContentResolver()
                .query(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, projection,
                        selection, selectionArgs, null)) {

            if (mediaCursor != null && mediaCursor.getCount() > 0) {
                mediaCursor.moveToFirst();
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
                @SuppressLint("Range") String artistId = mediaCursor.getString(mediaCursor
                        .getColumnIndex(MediaStore.Audio.Media.ARTIST_ID));
                @SuppressLint("Range") String title = mediaCursor.getString(mediaCursor
                        .getColumnIndex(MediaStore.Audio.Media.TITLE));
                @SuppressLint("Range") String artist = mediaCursor.getString(mediaCursor
                        .getColumnIndex(MediaStore.Audio.Media.ARTIST));
                @SuppressLint("Range") String duration = mediaCursor.getString(mediaCursor
                        .getColumnIndex(MediaStore.Audio.Media.DURATION));
                duration = contentDirectory.formatDuration(duration);

                @SuppressLint("Range") String mimeTypeString = mediaCursor.getString(mediaCursor
                        .getColumnIndex(MediaStore.Audio.Media.MIME_TYPE));
                Log.d(getClass().getName(), "Mimetype: " + mimeTypeString);
                @SuppressLint("Range") MimeType mimeType = MimeType.valueOf(mimeTypeString);
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
                        ContentDirectoryIDs.MUSIC_ARTIST_ITEM_PREFIX.getId() + id,
                        ContentDirectoryIDs.MUSIC_ARTIST_PREFIX.getId() + artistId,
                        title + "-(" + name + ")", "", album, artist, resource);
                musicTrack
                        .replaceFirstProperty(new DIDLObject.Property.UPNP.ALBUM_ART_URI(albumArtUri));
                musicTrack.setArtists(new PersonWithRole[]{new PersonWithRole(artist, "AlbumArtist")});
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                    @SuppressLint("Range") String genre = mediaCursor.getString(mediaCursor
                            .getColumnIndex(MediaStore.Audio.Media.GENRE));
                    @SuppressLint("Range") String bitrate = mediaCursor.getString(mediaCursor
                            .getColumnIndex(MediaStore.Audio.Media.BITRATE));
                    resource.setBitrate(Long.valueOf(bitrate));
                    musicTrack.setGenres(new String[]{genre});
                }
                result = musicTrack;
                Log.d(getClass().getName(), "MusicTrack: " + id + " Name: " + name
                        + " uri: " + uri);


            } else {
                Log.d(getClass().getName(), "Item " + myId + "  not found.");
            }
        }
        return result;
    }

    @Override
    public List<Container> browseContainer(
            ContentDirectory contentDirectory, String myId, long firstResult, long maxResults, SortCriterion[] orderby) {

        return new ArrayList<>();
    }

    @Override
    public List<Item> browseItem(ContentDirectory contentDirectory,
                                 String myId, long firstResult, long maxResults, SortCriterion[] orderby) {
        List<Item> result = new ArrayList<>();
        result.add((Item) browseMeta(contentDirectory, myId, 0, 1, null));
        return result;

    }

}