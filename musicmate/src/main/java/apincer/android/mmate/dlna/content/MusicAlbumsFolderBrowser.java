package apincer.android.mmate.dlna.content;

import android.annotation.SuppressLint;
import android.content.Context;
import android.database.Cursor;
import android.provider.MediaStore;
import android.util.Log;

import org.jupnp.support.model.DIDLObject;
import org.jupnp.support.model.SortCriterion;
import org.jupnp.support.model.container.Container;
import org.jupnp.support.model.container.MusicAlbum;
import org.jupnp.support.model.container.StorageFolder;
import org.jupnp.support.model.item.Item;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import apincer.android.mmate.R;
import apincer.android.mmate.dlna.ContentDirectory;

/**
 * Browser  for the music albums folder.
 *
 * @author openbit (Tobias Schoene)
 */
public class MusicAlbumsFolderBrowser extends ContentBrowser {
    public MusicAlbumsFolderBrowser(Context context) {
        super(context);
    }

    @Override
    public DIDLObject browseMeta(ContentDirectory contentDirectory, String myId, long firstResult, long maxResults, SortCriterion[] orderby) {

        return new StorageFolder(ContentDirectoryIDs.MUSIC_ALBUMS_FOLDER.getId(), ContentDirectoryIDs.MUSIC_FOLDER.getId(), getContext().getString(R.string.albums), "yaacc", getSize(contentDirectory, myId),
                null);

    }

    private Integer getSize(ContentDirectory contentDirectory, String myId) {

        String[] projection = {MediaStore.Audio.Albums._ID};
        String selection = "";
        String[] selectionArgs = null;
        try (Cursor cursor = contentDirectory.getContext().getContentResolver().query(MediaStore.Audio.Albums.EXTERNAL_CONTENT_URI, projection, selection,
                selectionArgs, null)) {
            return cursor.getCount();
        }

    }


    private Integer getMusicTrackSize(ContentDirectory contentDirectory, String parentId) {

        String[] projection = {MediaStore.Audio.Albums._ID};
        String selection = MediaStore.Audio.Media.ALBUM_ID + "=?";
        String[] selectionArgs = new String[]{parentId};
        try (Cursor cursor = contentDirectory.getContext().getContentResolver().query(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, projection, selection,
                selectionArgs, null)) {
            return cursor.getCount();
        }

    }

    @Override
    public List<Container> browseContainer(ContentDirectory contentDirectory, String myId, long firstResult, long maxResults, SortCriterion[] orderby) {
        List<Container> result = new ArrayList<>();
        String[] projection = {MediaStore.Audio.Albums._ID, MediaStore.Audio.Albums.ALBUM};
        String selection = "";
        String[] selectionArgs = null;
        Map<String, MusicAlbum> folderMap = new HashMap<>();
        try (Cursor mediaCursor = contentDirectory.getContext().getContentResolver().query(MediaStore.Audio.Albums.EXTERNAL_CONTENT_URI, projection, selection,
                selectionArgs, MediaStore.Audio.Albums.ALBUM + " ASC")) {

            if (mediaCursor != null && mediaCursor.getCount() > 0) {
                mediaCursor.moveToFirst();
                int currentIndex = 0;
                int currentCount = 0;
                while (!mediaCursor.isAfterLast() && currentCount < maxResults) {
                    if (firstResult <= currentIndex) {
                        @SuppressLint("Range") String id = mediaCursor.getString(mediaCursor.getColumnIndex(MediaStore.Audio.Albums._ID));
                        @SuppressLint("Range") String name = mediaCursor.getString(mediaCursor.getColumnIndex(MediaStore.Audio.Albums.ALBUM));
                        MusicAlbum musicAlbum = new MusicAlbum(ContentDirectoryIDs.MUSIC_ALBUM_PREFIX.getId() + id, ContentDirectoryIDs.MUSIC_ALBUMS_FOLDER.getId(), name, "", 0);
                        folderMap.put(id, musicAlbum);
                        Log.d(getClass().getName(), "Album Folder: " + id + " Name: " + name);
                        currentCount++;
                    }
                    currentIndex++;
                    mediaCursor.moveToNext();
                }

                //Fetch folder size

                for (Map.Entry<String, MusicAlbum> entry : folderMap.entrySet()) {
                    entry.getValue().setChildCount(getMusicTrackSize(contentDirectory, entry.getKey()));
                    result.add(entry.getValue());
                }
            } else {
                Log.d(getClass().getName(), "System media store is empty.");
            }
        }
        result.sort(Comparator.comparing(DIDLObject::getTitle));

        return result;
    }

    @Override
    public List<Item> browseItem(ContentDirectory contentDirectory, String myId, long firstResult, long maxResults, SortCriterion[] orderby) {
        return new ArrayList<>();

    }

}