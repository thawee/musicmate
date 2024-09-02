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
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import apincer.android.mmate.R;
import apincer.android.mmate.repository.MusicFolder;
import apincer.android.mmate.repository.MusicTag;
import apincer.android.mmate.repository.TagRepository;
import apincer.android.mmate.utils.MusicTagUtils;

/**
 * Browser  for the music playlist folder.
 */
public class ResolutionsBrowser extends ContentBrowser {
    public static final String HI_RES_AUDIO = "Hi-Res Lossless Audio";
    public static final String LOSSLESS_AUDIO = "Hi-Fi Lossless Audio";
    public static final String LOSSY_AUDIO = "Hi-Quality Audio";
    public static final String MQA_AUDIO = "MQA Audio";
    public static final String DSD_SONGS = "DSD Audio";
    private final List<String> playlists = new ArrayList<>();
    public ResolutionsBrowser(Context context) {
        super(context);
        playlists.add(HI_RES_AUDIO);
        playlists.add(LOSSLESS_AUDIO);
        playlists.add(LOSSY_AUDIO);
        playlists.add(MQA_AUDIO);
        playlists.add(DSD_SONGS);
    }

    @Override
    public DIDLObject browseMeta(ContentDirectory contentDirectory, String myId, long firstResult, long maxResults, SortCriterion[] orderby) {

        return new StorageFolder(ContentDirectoryIDs.MUSIC_RESOLUTION_FOLDER.getId(), ContentDirectoryIDs.MUSIC_FOLDER.getId(), getContext().getString(R.string.label_resolutions), "mmate", getSize(contentDirectory, myId),
                null);
    }

    private Integer getSize(ContentDirectory contentDirectory, String myId) {
        return playlists.size();
    }

    @Override
    public List<Container> browseContainer(ContentDirectory contentDirectory, String myId, long firstResult, long maxResults, SortCriterion[] orderby) {
        List<Container> result = new ArrayList<>();

        Map<String, MusicFolder> mapped = new HashMap<>();
        for(String pls: playlists) {
            MusicFolder dir = new MusicFolder();
            dir.setUniqueKey(pls);
            dir.setName(pls);
            mapped.put(pls, dir);
        }
        List<MusicTag> songs = TagRepository.getAllMusics();
        for(MusicTag tag: songs) {
           if(MusicTagUtils.isHiRes(tag)) {
                mapped.get(HI_RES_AUDIO).addChildCount();
           }
           if(MusicTagUtils.isMQA(tag)) {
                mapped.get(MQA_AUDIO).addChildCount();
           }
            if(MusicTagUtils.isLossless(tag)) {
                mapped.get(LOSSLESS_AUDIO).addChildCount();
            }
            if(MusicTagUtils.isLossy(tag)) {
                mapped.get(LOSSY_AUDIO).addChildCount();
            }
            if(MusicTagUtils.isDSD(tag)) {
                mapped.get(DSD_SONGS).addChildCount();
            }
        }

        for(MusicFolder group: mapped.values()) {
            MusicGenre musicAlbum = new MusicGenre(ContentDirectoryIDs.MUSIC_RESOLUTION_PREFIX.getId() + group.getName(), ContentDirectoryIDs.MUSIC_RESOLUTION_FOLDER.getId(), group.getName(), "", 0);
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