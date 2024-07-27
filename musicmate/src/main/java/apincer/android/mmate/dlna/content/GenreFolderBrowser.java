package apincer.android.mmate.dlna.content;

import android.content.Context;

import org.jupnp.support.model.DIDLObject;
import org.jupnp.support.model.SortCriterion;
import org.jupnp.support.model.container.Container;
import org.jupnp.support.model.container.MusicGenre;
import org.jupnp.support.model.item.MusicTrack;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import apincer.android.mmate.MusixMateApp;
import apincer.android.mmate.repository.MusicTag;


/**
 * Browser for a music genre folder.
 *
 * @author openbit (Tobias Schoene)
 */
public class GenreFolderBrowser extends ContentBrowser {
    private static final String TAG = "GenreFolderBrowser";
    public GenreFolderBrowser(Context context) {
        super(context);
    }

    @Override
    public DIDLObject browseMeta(ContentDirectory contentDirectory,
                                 String myId, long firstResult, long maxResults, SortCriterion[] orderby) {
        return new MusicGenre(myId, ContentDirectoryIDs.MUSIC_GENRES_FOLDER.getId(), myId, creator, getSize(
                contentDirectory, myId));
    }

    private Integer getSize(ContentDirectory contentDirectory, String myId) {
        String name = myId.substring(ContentDirectoryIDs.MUSIC_GENRE_PREFIX.getId().length());
        if("_EMPTY".equalsIgnoreCase(name) ||
                "_NULL".equalsIgnoreCase(name) ) {
               // "None".equalsIgnoreCase(name)) {
            name = "";
        }
        return MusixMateApp.getInstance().getOrmLite().findByGenre(name).size();
        /*
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
            String[] projection = {MediaStore.Audio.Media._ID};
            String selection = MediaStore.Audio.Media.GENRE_ID + "=?";
            String[] selectionArgs = new String[]{myId
                    .substring(ContentDirectoryIDs.MUSIC_GENRE_PREFIX.getId()
                    .length())};
            try (Cursor cursor = contentDirectory
                    .getContext()
                    .getContentResolver()
                    .query(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, projection,
                            selection, selectionArgs, null)) {
                return cursor.getCount();
            }
        } else {
            String[] projection = {MediaStore.Audio.Genres.Members.AUDIO_ID};
            String selection = MediaStore.Audio.Genres.Members.GENRE_ID + "=?";
            String[] selectionArgs = new String[]{myId
                    .substring(ContentDirectoryIDs.MUSIC_GENRE_PREFIX.getId()
                    .length())};
            try (Cursor cursor = contentDirectory
                    .getContext()
                    .getContentResolver()
                    .query(null, projection,
                            selection, selectionArgs, null)) {
                return cursor.getCount();
            }
        }
*/
    }

    @Override
    public List<Container> browseContainer(
            ContentDirectory contentDirectory, String myId, long firstResult, long maxResults, SortCriterion[] orderby) {

        return new ArrayList<>();
    }

    @Override
    public List<MusicTrack> browseItem(ContentDirectory contentDirectory,
                                       String myId, long firstResult, long maxResults, SortCriterion[] orderby) {
        List<MusicTrack> result = new ArrayList<>();
        String name = myId.substring(ContentDirectoryIDs.MUSIC_GENRE_PREFIX.getId().length());
        if("_EMPTY".equalsIgnoreCase(name) ||
                "_NULL".equalsIgnoreCase(name) ) {
            name = "";
        }
        List<MusicTag> tags = MusixMateApp.getInstance().getOrmLite().findByGenre(name);
        int currentCount = 0;
        for(MusicTag tag: tags) {
            if ((currentCount >= firstResult) && currentCount < (firstResult+maxResults)){
                MusicTrack musicTrack = toMusicTrack(contentDirectory, tag, myId, ContentDirectoryIDs.MUSIC_GENRE_ITEM_PREFIX.getId());
                result.add(musicTrack);
            }
            if(!forceFullContent)  currentCount++;
        }
        /*
        String[] projection;
        String selection;
        String[] selectionArgs;
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
            selection = MediaStore.Audio.Media.GENRE_ID + "=?";
            selectionArgs = new String[]{myId
                    .substring(ContentDirectoryIDs.MUSIC_GENRE_PREFIX.getId()
                    .length())};
        } else {

            String[] genreProjection = new String[]{MediaStore.Audio.Genres.Members.AUDIO_ID};
            String genreSelection = MediaStore.Audio.Genres.Members.GENRE_ID + "=?";
            String[] genreSelectionArgs = new String[]{myId
                    .substring(ContentDirectoryIDs.MUSIC_GENRE_PREFIX.getId()
                    .length())};
            List<String> audioIds = new ArrayList<>();
            try (Cursor genreCursor = contentDirectory
                    .getContext()
                    .getContentResolver()
                    .query(null, genreProjection,
                            genreSelection, genreSelectionArgs, "")) {
                if (genreCursor == null || genreCursor.getCount() == 0) {
                    return result;
                }
                genreCursor.moveToFirst();
                int currentIndex = 0;
                int currentCount = 0;
                while (!genreCursor.isAfterLast() && currentCount < maxResults) {
                    if (firstResult <= currentIndex) {
                        @SuppressLint("Range") String id = genreCursor
                                .getString(genreCursor
                                        .getColumnIndex(MediaStore.Audio.Genres.Members.AUDIO_ID));
                        audioIds.add(id);
                        currentCount++;
                    }
                    currentIndex++;
                    genreCursor.moveToNext();
                }
            }
            projection = new String[]{MediaStore.Audio.Media._ID,
                    MediaStore.Audio.Media.DISPLAY_NAME,
                    MediaStore.Audio.Media.MIME_TYPE,
                    MediaStore.Audio.Media.SIZE,
                    MediaStore.Audio.Media.ALBUM,
                    MediaStore.Audio.Media.ALBUM_ID,
                    MediaStore.Audio.Media.TITLE,
                    MediaStore.Audio.Media.ARTIST,
                    MediaStore.Audio.Media.DURATION};
            selection = MediaStore.Audio.Media._ID + "=?";
            selectionArgs = audioIds.toArray(new String[0]);
        }


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
                        @SuppressLint("Range") String id = mediaCursor
                                .getString(mediaCursor
                                        .getColumnIndex(MediaStore.Audio.Media._ID));
                        String genreId = myId;
                        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                            @SuppressLint("Range") int genreIdIdx = mediaCursor.getColumnIndex(MediaStore.Audio.Media.GENRE_ID);
                            genreId = mediaCursor.getString(genreIdIdx);
                        }
                        @SuppressLint("Range") String name = mediaCursor
                                .getString(mediaCursor
                                        .getColumnIndex(MediaStore.Audio.Media.DISPLAY_NAME));
                        @SuppressLint("Range") Long size = Long.valueOf(mediaCursor.getString(mediaCursor
                                .getColumnIndex(MediaStore.Audio.Media.SIZE)));

                        @SuppressLint("Range") String album = mediaCursor.getString(mediaCursor
                                .getColumnIndex(MediaStore.Audio.Media.ALBUM));
                        @SuppressLint("Range") String albumId = mediaCursor
                                .getString(mediaCursor
                                        .getColumnIndex(MediaStore.Audio.Media.ALBUM_ID));
                        @SuppressLint("Range") String title = mediaCursor.getString(mediaCursor
                                .getColumnIndex(MediaStore.Audio.Media.TITLE));
                        @SuppressLint("Range") String artist = mediaCursor
                                .getString(mediaCursor
                                        .getColumnIndex(MediaStore.Audio.Media.ARTIST));
                        artist = artist.equals("<unknown>") ? "" : artist;
                        @SuppressLint("Range") String duration = mediaCursor
                                .getString(mediaCursor
                                        .getColumnIndex(MediaStore.Audio.Media.DURATION));
                        duration = contentDirectory.formatDuration(duration);
                        @SuppressLint("Range") String mimeTypeString = mediaCursor.getString(mediaCursor
                                .getColumnIndex(MediaStore.Audio.Media.MIME_TYPE));
                        Log.d(getClass().getName(),
                                "Mimetype: "
                                        + mimeTypeString);
                        MimeType mimeType = MimeType
                                .valueOf(mimeTypeString);
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
                                ContentDirectoryIDs.MUSIC_GENRE_ITEM_PREFIX.getId()
                                        + id,
                                ContentDirectoryIDs.MUSIC_GENRE_PREFIX.getId()
                                        + genreId, title + "-(" + name + ")", "",
                                album, artist, resource);
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
        }*/

        result.sort(Comparator.comparing(DIDLObject::getTitle));
        return result;

    }

}