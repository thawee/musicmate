package apincer.music.server.jupnp.content;

import android.content.Context;

import org.jupnp.support.model.DIDLObject;
import org.jupnp.support.model.SortCriterion;
import org.jupnp.support.model.container.Container;
import org.jupnp.support.model.container.StorageFolder;
import org.jupnp.support.model.item.Item;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;

import apincer.music.core.model.Track;
import apincer.music.core.repository.TagRepository;
import musicmate.jupnp.nio.R;

/**
 * Browser  for the music genres folder.
 */
public class SourcesBrowser extends AbstractContentBrowser {
    public SourcesBrowser(Context context, TagRepository tagRepos) {
        super(context, tagRepos);
    }

    @Override
    public DIDLObject browseMeta(ContentDirectory contentDirectory, String myId, long firstResult, long maxResults, SortCriterion[] orderby) {
        return new StorageFolder(ContentDirectoryIDs.MUSIC_SOURCE_FOLDER.getId(), ContentDirectoryIDs.MUSIC_FOLDER.getId(), getContext().getString(R.string.directory), "mmate", getTotalMatches(contentDirectory, myId),
                null);
    }

    public Integer getTotalMatches(ContentDirectory contentDirectory, String myId) {
        return browseContainer(contentDirectory, myId, 0, 0, null).size();
    }

    @Override
    public List<Container> browseContainer(ContentDirectory contentDirectory, String myId, long firstResult, long maxResults, SortCriterion[] orderby) {
        // One library scan per Browse request: TotalMatches reuses the list built for the page
        if (rootFolders != null) return rootFolders;
        List<Container> result = new ArrayList<>();
        Collection<Track> rootDIRs = tagRepos.getRootDIRs();

        List<Track> songs = tagRepos.getAllMusicsForPlaylist();
        for(Track tag: songs) {
                for(Track dir: rootDIRs) {
                    if(tag.getPath().startsWith(dir.getUniqueKey())) {
                        dir.increaseChildCount();
                    }
                }
        }

        for(Track dir: rootDIRs) {
            if(dir.getChildCount() > 0) {
                StorageFolder musicDir = new StorageFolder(ContentDirectoryIDs.MUSIC_SOURCE_PREFIX.getId() + dir.getUniqueKey(), ContentDirectoryIDs.MUSIC_SOURCE_FOLDER.getId(), dir.getTitle(), "", 0, null);
                musicDir.setChildCount((int) dir.getChildCount());
                result.add(musicDir);
            }
        }
        result.sort(Comparator.comparing(DIDLObject::getTitle));
        rootFolders = result;
        return result;
    }
    private List<Container> rootFolders;

    @Override
    public List<Item> browseItem(ContentDirectory contentDirectory, String myId, long firstResult, long maxResults, SortCriterion[] orderby) {
        return new ArrayList<>();
    }
}