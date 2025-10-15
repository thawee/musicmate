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

import apincer.music.core.database.MusicTag;
import apincer.music.core.model.MusicFolder;
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
       // Collection<MusicFolder> rootDIRs = TagRepository.getRootDIRs(getContext());
        // return rootDIRs.size(); // +1; // +1 for Downloads folder
        List<Container> result = new ArrayList<>();
        Collection<MusicFolder> rootDIRs = tagRepos.getRootDIRs();

        List<MusicTag> songs = tagRepos.getAllMusicsForPlaylist();
        for(MusicTag tag: songs) {
            for(MusicFolder dir: rootDIRs) {
                if(tag.getPath().startsWith(dir.getUniqueKey())) {
                    dir.setChildCount(dir.getChildCount()+1);
                }
            }
        }

        for(MusicFolder dir: rootDIRs) {
            if(dir.getChildCount() > 0) {
                StorageFolder musicDir = new StorageFolder(ContentDirectoryIDs.MUSIC_SOURCE_PREFIX.getId() + dir.getUniqueKey(), ContentDirectoryIDs.MUSIC_SOURCE_FOLDER.getId(), dir.getName(), "", 0, null);
                musicDir.setChildCount((int) dir.getChildCount());
                result.add(musicDir);
            }
        }
        return result.size();
    }

    @Override
    public List<Container> browseContainer(ContentDirectory contentDirectory, String myId, long firstResult, long maxResults, SortCriterion[] orderby) {
        List<Container> result = new ArrayList<>();
        Collection<MusicFolder> rootDIRs = tagRepos.getRootDIRs();

        List<MusicTag> songs = tagRepos.getAllMusicsForPlaylist();
        for(MusicTag tag: songs) {
                for(MusicFolder dir: rootDIRs) {
                    if(tag.getPath().startsWith(dir.getUniqueKey())) {
                        dir.setChildCount(dir.getChildCount()+1);
                    }
                }
        }

        for(MusicFolder dir: rootDIRs) {
            if(dir.getChildCount() > 0) {
                StorageFolder musicDir = new StorageFolder(ContentDirectoryIDs.MUSIC_SOURCE_PREFIX.getId() + dir.getUniqueKey(), ContentDirectoryIDs.MUSIC_SOURCE_FOLDER.getId(), dir.getName(), "", 0, null);
                musicDir.setChildCount((int) dir.getChildCount());
                result.add(musicDir);
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