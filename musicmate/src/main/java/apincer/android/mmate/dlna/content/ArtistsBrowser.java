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
 */
public class ArtistsBrowser extends AbstractContentBrowser {
    private static final String TAG = "MusicArtistsBrowser";
    public ArtistsBrowser(Context context) {
        super(context);
    }

    @Override
    public DIDLObject browseMeta(ContentDirectory contentDirectory, String myId, long firstResult, long maxResults, SortCriterion[] orderby) {

        return new StorageFolder(ContentDirectoryIDs.MUSIC_ARTISTS_FOLDER.getId(), ContentDirectoryIDs.MUSIC_FOLDER.getId(), getContext().getString(R.string.artists), "mmate", getTotalMatches(contentDirectory, myId),
                null);
    }

    public Integer getTotalMatches(ContentDirectory contentDirectory, String myId) {
        return TagRepository.getArtistList().size();
    }

    @Override
    public List<Container> browseContainer(ContentDirectory contentDirectory, String myId, long firstResult, long maxResults, SortCriterion[] orderby) {
        List<Container> result = new ArrayList<>();
        List<MusicFolder> groupings = MusixMateApp.getInstance().getOrmLite().getArtistWithChildrenCount();
        for(MusicFolder group: groupings) {
            MusicArtist musicAlbum = new MusicArtist(ContentDirectoryIDs.MUSIC_ARTIST_PREFIX.getId() + group.getName(), ContentDirectoryIDs.MUSIC_ARTISTS_FOLDER.getId(), group.getName(), "", 0);
            musicAlbum.setChildCount((int)group.getChildCount());
            result.add(musicAlbum);
        }
        result.sort(Comparator.comparing(DIDLObject::getTitle));
       // Log.d(TAG, "Returning " + result.size() + " MusicAlbum Containers");
        return result;
    }

    @Override
    public List<Item> browseItem(ContentDirectory contentDirectory, String myId, long firstResult, long maxResults, SortCriterion[] orderby) {
        return new ArrayList<>();
    }
}