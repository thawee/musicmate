package apincer.android.jupnp.content;

import android.content.Context;

import org.jupnp.support.model.DIDLObject;
import org.jupnp.support.model.SortCriterion;
import org.jupnp.support.model.container.Container;
import org.jupnp.support.model.container.MusicGenre;
import org.jupnp.support.model.container.StorageFolder;
import org.jupnp.support.model.item.Item;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import apincer.android.mmate.core.Constants;
import apincer.android.mmate.core.database.MusicTag;
import apincer.android.mmate.core.model.MusicFolder;
import apincer.android.mmate.core.repository.TagRepository;
import apincer.android.mmate.core.utils.TagUtils;
import musicmate.mediaserver.jupnp.R;

/**
 * Browser  for the music playlist folder.
 */
public class ResolutionsBrowser extends AbstractContentBrowser {
    private final List<String> playlists = new ArrayList<>();
    public ResolutionsBrowser(Context context, TagRepository tagRepos) {
        super(context, tagRepos);
        playlists.add(Constants.TITLE_HIRES);
        playlists.add(Constants.TITLE_HIFI_LOSSLESS);
        playlists.add(Constants.TITLE_HIGH_QUALITY);
        playlists.add(Constants.TITLE_MASTER_AUDIO);
        playlists.add(Constants.TITLE_DSD);
    }

    @Override
    public DIDLObject browseMeta(ContentDirectory contentDirectory, String myId, long firstResult, long maxResults, SortCriterion[] orderby) {

        return new StorageFolder(ContentDirectoryIDs.MUSIC_RESOLUTION_FOLDER.getId(), ContentDirectoryIDs.MUSIC_FOLDER.getId(), getContext().getString(R.string.label_audio_encoding_format), "mmate", getTotalMatches(contentDirectory, myId),
                null);
    }

    public Integer getTotalMatches(ContentDirectory contentDirectory, String myId) {
        return playlists.size();
    }

    @Override
    public List<Container> browseContainer(ContentDirectory contentDirectory, String myId, long firstResult, long maxResults, SortCriterion[] orderby) {
        List<Container> result = new ArrayList<>();

        Map<String, MusicFolder> mapped = new HashMap<>();
        for(String pls: playlists) {
            MusicFolder dir = new MusicFolder(pls);
            dir.setUniqueKey(pls);
            mapped.put(pls, dir);
        }
        List<MusicTag> songs = getOrmLite().findMySongs();
        for(MusicTag tag: songs) {
           if(TagUtils.isHiRes(tag)) {
                mapped.get(Constants.TITLE_HIRES).increaseChildCount();
           }
           if(TagUtils.isMQA(tag)) {
                mapped.get(Constants.TITLE_MASTER_AUDIO).increaseChildCount();
           }
            if(TagUtils.isLossless(tag)) {
                mapped.get(Constants.TITLE_HIFI_LOSSLESS).increaseChildCount();
            }
            if(TagUtils.isLossy(tag)) {
                mapped.get(Constants.TITLE_HIGH_QUALITY).increaseChildCount();
            }
            if(TagUtils.isDSD(tag)) {
                mapped.get(Constants.TITLE_DSD).increaseChildCount();
            }
        }

        for(MusicFolder group: mapped.values()) {
            if(group.getChildCount() > 0) {
                MusicGenre musicAlbum = new MusicGenre(ContentDirectoryIDs.MUSIC_RESOLUTION_PREFIX.getId() + group.getName(), ContentDirectoryIDs.MUSIC_RESOLUTION_FOLDER.getId(), group.getName(), "", 0);
                musicAlbum.setChildCount((int)group.getChildCount());
                result.add(musicAlbum);
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