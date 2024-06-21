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
import apincer.android.mmate.dlna.MusicMateContentDirectory;

/**
 * Browser  for the music artists folder.
 *
 * @author openbit (Tobias Schoene)
 */
public class MusicArtistsFolderBrowser extends ContentBrowser {

    public MusicArtistsFolderBrowser(Context context) {
        super(context);
    }

    @Override
    public DIDLObject browseMeta(MusicMateContentDirectory contentDirectory, String myId, long firstResult, long maxResults, SortCriterion[] orderby) {

        return new StorageFolder(ContentDirectoryIDs.MUSIC_ARTISTS_FOLDER.getId(), ContentDirectoryIDs.MUSIC_FOLDER.getId(), getContext().getString(R.string.artists), "yaacc", getSize(contentDirectory, myId),
                null);

    }

    private Integer getSize(MusicMateContentDirectory contentDirectory, String myId) {

        String[] projection = {MediaStore.Audio.Artists._ID};
        String selection = "";
        String[] selectionArgs = null;
        try (Cursor cursor = contentDirectory.getContext().getContentResolver().query(MediaStore.Audio.Artists.EXTERNAL_CONTENT_URI, projection, selection,
                selectionArgs, null)) {
            return cursor.getCount();
        }

    }


    private Integer getMusicTrackSize(MusicMateContentDirectory contentDirectory, String parentId) {
        String[] projection = {MediaStore.Audio.Media.ARTIST_ID};
        String selection = MediaStore.Audio.Media.ARTIST_ID + "=?";
        String[] selectionArgs = new String[]{parentId};
        try (Cursor cursor = contentDirectory.getContext().getContentResolver().query(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, projection, selection,
                selectionArgs, null)) {
            return cursor.getCount();
        }
    }

    @Override
    public List<Container> browseContainer(MusicMateContentDirectory contentDirectory, String myId, long firstResult, long maxResults, SortCriterion[] orderby) {
        List<Container> result = new ArrayList<>();
        String[] projection = {MediaStore.Audio.Artists._ID, MediaStore.Audio.Artists.ARTIST};
        String selection = "";
        String[] selectionArgs = null;
        Map<String, MusicAlbum> folderMap = new HashMap<>();
        try (Cursor mediaCursor = contentDirectory.getContext().getContentResolver().query(MediaStore.Audio.Artists.EXTERNAL_CONTENT_URI, projection, selection,
                selectionArgs, MediaStore.Audio.Artists.ARTIST + " ASC")) {

            if (mediaCursor != null && mediaCursor.getCount() > 0) {
                mediaCursor.moveToFirst();
                int currentIndex = 0;
                int currentCount = 0;
                while (!mediaCursor.isAfterLast() && currentCount < maxResults) {
                    if (firstResult <= currentIndex) {
                        @SuppressLint("Range") String id = mediaCursor.getString(mediaCursor.getColumnIndex(MediaStore.Audio.Albums._ID));
                        @SuppressLint("Range") String name = mediaCursor.getString(mediaCursor.getColumnIndex(MediaStore.Audio.Albums.ARTIST));
                        MusicAlbum musicAlbum = new MusicAlbum(ContentDirectoryIDs.MUSIC_ARTIST_PREFIX.getId() + id, ContentDirectoryIDs.MUSIC_ALBUMS_FOLDER.getId(), name, "", 0);
                        folderMap.put(id, musicAlbum);
                        Log.d(getClass().getName(), "Artists Folder: " + id + " Name: " + name);
                        currentCount++;
                    }
                    currentIndex++;
                    mediaCursor.moveToNext();
                }

                for (Map.Entry<String, MusicAlbum> entry : folderMap.entrySet()) {
                    entry.getValue().setChildCount(getMusicTrackSize(contentDirectory, entry.getKey()));
                    result.add(entry.getValue());
                }
            } else {
                Log.d(getClass().getName(), "System media store is empty.");
            }
        }
        result.sort(Comparator.comparing(DIDLObject::getTitle));
        Log.d(getClass().getName(), "Returning " + result.size() + " MusicAlbum Containers");
        return result;
    }

    @Override
    public List<Item> browseItem(MusicMateContentDirectory contentDirectory, String myId, long firstResult, long maxResults, SortCriterion[] orderby) {
        return new ArrayList<>();
    }

}