package apincer.android.mmate.dlna.content;

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

import apincer.android.mmate.R;
import apincer.android.mmate.repository.MusicFolder;
import apincer.android.mmate.repository.MusicTag;
import apincer.android.mmate.repository.TagRepository;

/**
 * Browser  for the music genres folder.
 */
public class SourcesBrowser extends ContentBrowser {
    public SourcesBrowser(Context context) {
        super(context);
    }

    @Override
    public DIDLObject browseMeta(ContentDirectory contentDirectory, String myId, long firstResult, long maxResults, SortCriterion[] orderby) {
        // /English
        // /Download
        // /IMDP
        return new StorageFolder(ContentDirectoryIDs.MUSIC_SOURCE_FOLDER.getId(), ContentDirectoryIDs.MUSIC_FOLDER.getId(), getContext().getString(R.string.directory), "mmate", getSize(contentDirectory, myId),
                null);
       // return new StorageFolder(ContentDirectoryIDs.MUSIC_DIRS_FOLDER.getId(), ContentDirectoryIDs.MUSIC_FOLDER.getId(), getContext().getString(R.string.directory), "mmate",0,null);
    }

    private Integer getSize(ContentDirectory contentDirectory, String myId) {
        Collection<MusicFolder> rootDIRs = TagRepository.getRootDIRs(getContext());

        return rootDIRs.size(); // +1; // +1 for Downloads folder
    }

    @Override
    public List<Container> browseContainer(ContentDirectory contentDirectory, String myId, long firstResult, long maxResults, SortCriterion[] orderby) {
        List<Container> result = new ArrayList<>();
        Collection<MusicFolder> rootDIRs = TagRepository.getRootDIRs(getContext());

        List<MusicTag> songs = TagRepository.getAllMusics();
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