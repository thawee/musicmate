package apincer.android.mmate.dlna.content;

import android.content.Context;

import org.jupnp.support.model.DIDLObject;
import org.jupnp.support.model.SortCriterion;
import org.jupnp.support.model.container.Container;
import org.jupnp.support.model.container.MusicAlbum;
import org.jupnp.support.model.container.MusicGenre;
import org.jupnp.support.model.container.StorageFolder;
import org.jupnp.support.model.item.Item;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import apincer.android.mmate.MusixMateApp;
import apincer.android.mmate.R;
import apincer.android.mmate.dlna.ContentDirectory;
import apincer.android.mmate.repository.MusicFolder;
import apincer.android.mmate.repository.TagRepository;

/**
 * Browser  for the music genres folder.
 *
 * @author openbit (Tobias Schoene)
 */
public class GenresBrowser extends ContentBrowser {
    public GenresBrowser(Context context) {
        super(context);
    }

    @Override
    public DIDLObject browseMeta(ContentDirectory contentDirectory, String myId, long firstResult, long maxResults, SortCriterion[] orderby) {

        return new StorageFolder(ContentDirectoryIDs.MUSIC_GENRES_FOLDER.getId(), ContentDirectoryIDs.MUSIC_FOLDER.getId(), getContext().getString(R.string.genres), "mmate", getSize(contentDirectory, myId),
                null);

    }

    private Integer getSize(ContentDirectory contentDirectory, String myId) {
/*
        String[] projection = {MediaStore.Audio.Genres._ID};
        String selection = "";
        String[] selectionArgs = null;
        try (Cursor cursor = contentDirectory.getContext().getContentResolver().query(MediaStore.Audio.Genres.EXTERNAL_CONTENT_URI, projection, selection,
                selectionArgs, null)) {
            return cursor.getCount();
        } */
        return TagRepository.getActualGenreList(getContext()).size();
    }

    /*

    private Integer getMusicTrackSize(ContentDirectory contentDirectory, String parentId) {

        String[] projection = {MediaStore.Audio.Media._ID};
        String selection = MediaStore.Audio.Media.GENRE_ID + "=?";
        String[] selectionArgs = new String[]{parentId};
        try (Cursor cursor = contentDirectory.getContext().getContentResolver().query(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, projection, selection,
                selectionArgs, null)) {
            return cursor.getCount();
        }

    }*/

    @Override
    public List<Container> browseContainer(ContentDirectory contentDirectory, String myId, long firstResult, long maxResults, SortCriterion[] orderby) {
        List<Container> result = new ArrayList<>();
        List<MusicFolder> genres = MusixMateApp.getInstance().getOrmLite().getGenresWithChildrenCount();
        for(MusicFolder genre: genres) {
            MusicGenre musicGenre = new MusicGenre(ContentDirectoryIDs.MUSIC_GENRE_PREFIX.getId() + genre.getName(), ContentDirectoryIDs.MUSIC_GENRES_FOLDER.getId(), genre.getName(), "", (int)genre.getChildCount());
            result.add(musicGenre);
        }
        /*
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
        } */

        result.sort(Comparator.comparing(DIDLObject::getTitle));

        return result;
    }

    @Override
    public List<Item> browseItem(ContentDirectory contentDirectory, String myId, long firstResult, long maxResults, SortCriterion[] orderby) {
        return new ArrayList<>();
    }
}