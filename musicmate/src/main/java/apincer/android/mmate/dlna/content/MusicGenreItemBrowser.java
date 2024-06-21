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
import apincer.android.mmate.dlna.MusicMateContentDirectory;


/**
 * Browser for a music album item.
 *
 * @author openbit (Tobias Schoene)
 */
public class MusicGenreItemBrowser extends ContentBrowser {

    public MusicGenreItemBrowser(Context context) {
        super(context);
    }


    @Override
    public DIDLObject browseMeta(MusicMateContentDirectory contentDirectory,
                                 String myId, long firstResult, long maxResults, SortCriterion[] orderby) {
        Item result = null;
        String[] projection;
        String genreId = "";
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
                    MediaStore.Audio.Media.GENRE_ID,
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
            String[] genreProjection = new String[]{MediaStore.Audio.Genres.Members.GENRE_ID};
            String genreSelection = MediaStore.Audio.Genres.Members.AUDIO_ID + "=?";
            String[] genreSelectionArgs = new String[]{myId
                    .substring(ContentDirectoryIDs.MUSIC_GENRE_PREFIX.getId()
                    .length())};
            List<String> audioIds = new ArrayList<>();
            try (Cursor genreCursor = contentDirectory
                    .getContext()
                    .getContentResolver()
                    .query(null, genreProjection,
                            genreSelection, genreSelectionArgs, "")) {
                if (genreCursor != null && genreCursor.getCount() == 1) {
                    genreCursor.moveToFirst();
                    @SuppressLint("Range") int idx = genreCursor
                            .getColumnIndex(MediaStore.Audio.Genres.Members.AUDIO_ID);
                    genreId = genreCursor.getString(idx);
                }
            }
        }
        String selection = MediaStore.Audio.Media._ID + "=?";
        String[] selectionArgs = new String[]{myId
                .substring(ContentDirectoryIDs.MUSIC_GENRE_ITEM_PREFIX.getId()
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

                @SuppressLint("Range") String name = mediaCursor
                        .getString(mediaCursor
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
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                    @SuppressLint("Range") int genreIdIdx = mediaCursor.getColumnIndex(MediaStore.Audio.Media.GENRE_ID);
                    genreId = mediaCursor.getString(genreIdIdx);
                }
                @SuppressLint("Range") String mimeTypeString = mediaCursor.getString(mediaCursor
                        .getColumnIndex(MediaStore.Audio.Genres.Members.MIME_TYPE));
                Log.d(getClass().getName(), "Mimetype: " + mimeTypeString);
                MimeType mimeType = MimeType.valueOf(mimeTypeString);
                // file parameter only needed for media players which decide
                // the
                // ability of playing a file by the file extension
                String uri = getUriString(contentDirectory, id, mimeType);
                URI albumArtUri = URI.create("http://"
                        + contentDirectory.getIpAddress() + ":"
                        + MediaServerService.SERVER_PORT + "/album/" + albumId);
                Res resource = new Res(mimeType, size, uri);
                resource.setDuration(duration);
                MusicTrack musicTrack = new MusicTrack(
                        ContentDirectoryIDs.MUSIC_GENRE_ITEM_PREFIX.getId() + id,
                        ContentDirectoryIDs.MUSIC_GENRE_PREFIX.getId() + genreId,
                        title + "-(" + name + ")", "", album, artist, resource);
                musicTrack.replaceFirstProperty(new DIDLObject.Property.UPNP.ALBUM_ART_URI(albumArtUri));
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
            MusicMateContentDirectory contentDirectory, String myId, long firstResult, long maxResults, SortCriterion[] orderby) {

        return new ArrayList<>();
    }

    @Override
    public List<Item> browseItem(MusicMateContentDirectory contentDirectory,
                                 String myId, long firstResult, long maxResults, SortCriterion[] orderby) {
        List<Item> result = new ArrayList<>();
        result.add((Item) browseMeta(contentDirectory, myId, firstResult, maxResults, orderby));

        return result;

    }

}