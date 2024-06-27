package apincer.android.mmate.dlna.content;

import android.annotation.SuppressLint;
import android.content.Context;

import org.jupnp.support.model.DIDLObject;
import org.jupnp.support.model.SortCriterion;
import org.jupnp.support.model.container.Container;
import org.jupnp.support.model.item.Item;

import java.util.ArrayList;
import java.util.List;

import apincer.android.mmate.MusixMateApp;
import apincer.android.mmate.dlna.ContentDirectory;
import apincer.android.mmate.repository.MusicTag;
import apincer.android.mmate.utils.StringUtils;

/**
 * Browser for a music item, for 1st level music folder, i.e. All Title, Downloads
 *
 * @author openbit (Tobias Schoene)
 */
public class MusicItemBrowser extends ContentBrowser {
    private static final String TAG = "MusicItemBrowser";
    private final String folderId;
    private final String itemPrefix;

    public MusicItemBrowser(Context context, String folderId,String itemPrefix) {
        super(context);
        this.folderId = folderId;
        this.itemPrefix = itemPrefix;
    }

    @SuppressLint("Range")
    @Override
    public DIDLObject browseMeta(ContentDirectory contentDirectory,
                                 String myId, long firstResult, long maxResults, SortCriterion[] orderby) {
        Item result = null;

        String id = myId.substring(itemPrefix.length());
        MusicTag tag = MusixMateApp.getInstance().getOrmLite().findById(StringUtils.toLong(id));
        result = toMusicTrack(contentDirectory, tag, folderId, itemPrefix);

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
        String selection = MediaStore.Audio.Media._ID + "=?";
        String[] selectionArgs = new String[]{myId
                .substring(itemPrefix.length())};
        //        .substring(ContentDirectoryIDs.MUSIC_ALL_TITLES_ITEM_PREFIX
        //        .getId().length())};
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

                MimeType mimeType = MimeType.valueOf(mediaCursor
                        .getString(mediaCursor
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
                        itemPrefix
                       // ContentDirectoryIDs.MUSIC_ALL_TITLES_ITEM_PREFIX.getId()
                                + id,
                        folderId, title
                        //ContentDirectoryIDs.MUSIC_ALL_TITLES_FOLDER.getId(), title
                        + "-(" + name + ")", "", album, artist, resource);
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
        }*/
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
        result.add((Item) browseMeta(contentDirectory, myId, firstResult, maxResults, orderby));
        return result;
    }
}