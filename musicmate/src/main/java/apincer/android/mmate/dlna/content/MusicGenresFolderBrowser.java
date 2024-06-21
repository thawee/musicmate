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
 * Browser  for the music genres folder.
 *
 * @author openbit (Tobias Schoene)
 */
public class MusicGenresFolderBrowser extends ContentBrowser {
    public MusicGenresFolderBrowser(Context context) {
        super(context);
    }

    @Override
    public DIDLObject browseMeta(MusicMateContentDirectory contentDirectory, String myId, long firstResult, long maxResults, SortCriterion[] orderby) {

        return new StorageFolder(ContentDirectoryIDs.MUSIC_GENRES_FOLDER.getId(), ContentDirectoryIDs.MUSIC_FOLDER.getId(), getContext().getString(R.string.genres), "yaacc", getSize(contentDirectory, myId),
                null);

    }

    private Integer getSize(MusicMateContentDirectory contentDirectory, String myId) {

        String[] projection = {MediaStore.Audio.Genres._ID};
        String selection = "";
        String[] selectionArgs = null;
        try (Cursor cursor = contentDirectory.getContext().getContentResolver().query(MediaStore.Audio.Genres.EXTERNAL_CONTENT_URI, projection, selection,
                selectionArgs, null)) {
            return cursor.getCount();
        }

    }


    private Integer getMusicTrackSize(MusicMateContentDirectory contentDirectory, String parentId) {

        String[] projection = {MediaStore.Audio.Media._ID};
        String selection = MediaStore.Audio.Media.GENRE_ID + "=?";
        String[] selectionArgs = new String[]{parentId};
        try (Cursor cursor = contentDirectory.getContext().getContentResolver().query(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, projection, selection,
                selectionArgs, null)) {
            return cursor.getCount();
        }

    }

    @Override
    public List<Container> browseContainer(MusicMateContentDirectory contentDirectory, String myId, long firstResult, long maxResults, SortCriterion[] orderby) {
        List<Container> result = new ArrayList<>();
        String[] projection = {MediaStore.Audio.Genres._ID, MediaStore.Audio.Genres.NAME};
        String selection = "";
        String[] selectionArgs = null;
        Map<String, MusicAlbum> folderMap = new HashMap<>();
        try (Cursor mediaCursor = contentDirectory.getContext().getContentResolver().query(MediaStore.Audio.Genres.EXTERNAL_CONTENT_URI, projection, selection,
                selectionArgs, MediaStore.Audio.Genres.NAME + " ASC")) {

            if (mediaCursor != null && mediaCursor.getCount() > 0) {
                mediaCursor.moveToFirst();
                int currentIndex = 0;
                int currentCount = 0;
                while (!mediaCursor.isAfterLast() && currentCount < maxResults) {
                    if (firstResult <= currentIndex) {
                        @SuppressLint("Range") String id = mediaCursor.getString(mediaCursor.getColumnIndex(MediaStore.Audio.Genres._ID));
                        @SuppressLint("Range") String name = mediaCursor.getString(mediaCursor.getColumnIndex(MediaStore.Audio.Genres.NAME));
                        MusicAlbum musicAlbum = new MusicAlbum(ContentDirectoryIDs.MUSIC_GENRE_PREFIX.getId() + id, ContentDirectoryIDs.MUSIC_GENRES_FOLDER.getId(), name, "", 0);
                        if (id != null) {
                            folderMap.put(id, musicAlbum);
                            Log.d(getClass().getName(), "Genre Folder: " + id + " Name: " + name);
                        }
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

        return result;
    }

    @Override
    public List<Item> browseItem(MusicMateContentDirectory contentDirectory, String myId, long firstResult, long maxResults, SortCriterion[] orderby) {
        return new ArrayList<>();


    }

}