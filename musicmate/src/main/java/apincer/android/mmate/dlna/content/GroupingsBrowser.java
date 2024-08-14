package apincer.android.mmate.dlna.content;

import android.content.Context;

import org.jupnp.support.model.DIDLObject;
import org.jupnp.support.model.SortCriterion;
import org.jupnp.support.model.container.Container;
import org.jupnp.support.model.container.MusicGenre;
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
 * Browser  for the music genres folder.
 */
public class GroupingsBrowser extends ContentBrowser {
    public GroupingsBrowser(Context context) {
        super(context);
    }

    @Override
    public DIDLObject browseMeta(ContentDirectory contentDirectory, String myId, long firstResult, long maxResults, SortCriterion[] orderby) {

        return new StorageFolder(ContentDirectoryIDs.MUSIC_GROUPING_FOLDER.getId(), ContentDirectoryIDs.MUSIC_FOLDER.getId(), getContext().getString(R.string.groupings), "mmate", getSize(contentDirectory, myId),
                null);

    }

    private Integer getSize(ContentDirectory contentDirectory, String myId) {
        return TagRepository.getActualGroupingList(getContext()).size(); // +1; // +1 for Downloads folder
    }

    @Override
    public List<Container> browseContainer(ContentDirectory contentDirectory, String myId, long firstResult, long maxResults, SortCriterion[] orderby) {
        List<Container> result = new ArrayList<>();
        List<MusicFolder> groupings = MusixMateApp.getInstance().getOrmLite().getGroupingWithChildrenCount();
        for(MusicFolder group: groupings) {
            MusicGenre musicAlbum = new MusicGenre(ContentDirectoryIDs.MUSIC_GROUPING_PREFIX.getId() + group.getName(), ContentDirectoryIDs.MUSIC_GROUPING_FOLDER.getId(), group.getName(), "", 0);
            musicAlbum.setChildCount((int)group.getChildCount());
            result.add(musicAlbum);
        }

        result.sort(Comparator.comparing(DIDLObject::getTitle));

        return result;
    }

    @Override
    public List<Item> browseItem(ContentDirectory contentDirectory, String myId, long firstResult, long maxResults, SortCriterion[] orderby) {
        return new ArrayList<>();
    }
}