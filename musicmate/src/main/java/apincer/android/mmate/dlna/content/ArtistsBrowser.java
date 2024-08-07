package apincer.android.mmate.dlna.content;

import android.content.Context;
import android.util.Log;

import org.jupnp.support.model.DIDLObject;
import org.jupnp.support.model.SortCriterion;
import org.jupnp.support.model.container.Container;
import org.jupnp.support.model.container.MusicArtist;
import org.jupnp.support.model.container.StorageFolder;
import org.jupnp.support.model.item.Item;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import apincer.android.mmate.MusixMateApp;
import apincer.android.mmate.R;
import apincer.android.mmate.repository.MusicFolder;
import apincer.android.mmate.repository.TagRepository;

/**
 * Browser  for the music artists folder.
 *
 * @author openbit (Tobias Schoene)
 */
public class ArtistsBrowser extends ContentBrowser {
    private static final String TAG = "MusicArtistsBrowser";
    public ArtistsBrowser(Context context) {
        super(context);
    }

    @Override
    public DIDLObject browseMeta(ContentDirectory contentDirectory, String myId, long firstResult, long maxResults, SortCriterion[] orderby) {

        return new StorageFolder(ContentDirectoryIDs.MUSIC_ARTISTS_FOLDER.getId(), ContentDirectoryIDs.MUSIC_FOLDER.getId(), getContext().getString(R.string.artists), "mmate", getSize(contentDirectory, myId),
                null);

    }

    private Integer getSize(ContentDirectory contentDirectory, String myId) {
        return TagRepository.getArtistList().size();
        /*
        String[] projection = {MediaStore.Audio.Artists._ID};
        String selection = "";
        String[] selectionArgs = null;
        try (Cursor cursor = contentDirectory.getContext().getContentResolver().query(MediaStore.Audio.Artists.EXTERNAL_CONTENT_URI, projection, selection,
                selectionArgs, null)) {
            return cursor.getCount();
        }*/

    }
/*

    private Integer getMusicTrackSize(ContentDirectory contentDirectory, String parentId) {
        String[] projection = {MediaStore.Audio.Media.ARTIST_ID};
        String selection = MediaStore.Audio.Media.ARTIST_ID + "=?";
        String[] selectionArgs = new String[]{parentId};
        try (Cursor cursor = contentDirectory.getContext().getContentResolver().query(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, projection, selection,
                selectionArgs, null)) {
            return cursor.getCount();
        }
    } */

    @Override
    public List<Container> browseContainer(ContentDirectory contentDirectory, String myId, long firstResult, long maxResults, SortCriterion[] orderby) {
        List<Container> result = new ArrayList<>();
        List<MusicFolder> groupings = MusixMateApp.getInstance().getOrmLite().getArtistWithChildrenCount();
        for(MusicFolder group: groupings) {
            MusicArtist musicAlbum = new MusicArtist(ContentDirectoryIDs.MUSIC_ARTIST_PREFIX.getId() + group.getName(), ContentDirectoryIDs.MUSIC_ARTISTS_FOLDER.getId(), group.getName(), "", 0);
            musicAlbum.setChildCount((int)group.getChildCount());
            result.add(musicAlbum);
        }
        /*
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
        } */
        result.sort(Comparator.comparing(DIDLObject::getTitle));
        Log.d(TAG, "Returning " + result.size() + " MusicAlbum Containers");
        return result;
    }

    @Override
    public List<Item> browseItem(ContentDirectory contentDirectory, String myId, long firstResult, long maxResults, SortCriterion[] orderby) {
        return new ArrayList<>();
    }

}