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
import java.util.Objects;

import apincer.android.mmate.MusixMateApp;
import apincer.android.mmate.R;
import apincer.android.mmate.repository.MusicFolder;
import apincer.android.mmate.repository.MusicTag;
import apincer.android.mmate.repository.TagRepository;
import apincer.android.mmate.utils.MusicTagUtils;

/**
 * Browser  for the music playlist folder.
 */
public class CollectionsBrowser extends ContentBrowser {
    public static final String MY_SONGS = "All Songs";
    public static final String DOWNLOADS_SONGS = "Download Songs";
    public static final String DUPLICATED_SONGS = "Duplicated Songs";
    public static final String I_AM_HAPPY_SONGS = "SmartList - I Am HAPPY";
    public static final String I_FEEL_RELAXED_SONGS = "SmartList - I Feel RELAXED";
    public static final String I_AM_FUN_SONGS = "SmartList - I Am FUN";
    private final List<String> playlists = new ArrayList<>();
    public CollectionsBrowser(Context context) {
        super(context);
        playlists.add(MY_SONGS);
        playlists.add(DOWNLOADS_SONGS);
        playlists.add(DUPLICATED_SONGS);
        playlists.add(I_AM_HAPPY_SONGS);
        playlists.add(I_FEEL_RELAXED_SONGS);
        playlists.add(I_AM_FUN_SONGS);
       // playlists.add("Up Scaled Songs");
    }

    @Override
    public DIDLObject browseMeta(ContentDirectory contentDirectory, String myId, long firstResult, long maxResults, SortCriterion[] orderby) {

        return new StorageFolder(ContentDirectoryIDs.MUSIC_COLLECTION_FOLDER.getId(), ContentDirectoryIDs.MUSIC_FOLDER.getId(), getContext().getString(R.string.label_collections), "mmate", getSize(contentDirectory, myId),
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
            Objects.requireNonNull(mapped.get(MY_SONGS)).addChildCount();

           if(MusicTagUtils.isOnDownloadDir(tag)) {
               Objects.requireNonNull(mapped.get(DOWNLOADS_SONGS)).addChildCount();
           }
            if(MusicTagUtils.isFunPlaylist(tag)) {
                Objects.requireNonNull(mapped.get(I_AM_FUN_SONGS)).addChildCount();
            }
            if(MusicTagUtils.isRelaxedPlaylist(tag)) {
                Objects.requireNonNull(mapped.get(I_FEEL_RELAXED_SONGS)).addChildCount();
            }
            if(MusicTagUtils.isHappyPlaylist(tag)) {
                Objects.requireNonNull(mapped.get(I_AM_HAPPY_SONGS)).addChildCount();
            }
        }
        List<MusicTag> tags = MusixMateApp.getInstance().getOrmLite().findDuplicateSong();
        Objects.requireNonNull(mapped.get(DUPLICATED_SONGS)).setChildCount(tags.size());

        for(MusicFolder group: mapped.values()) {
            MusicGenre musicAlbum = new MusicGenre(ContentDirectoryIDs.MUSIC_COLLECTION_PREFIX.getId() + group.getName(), ContentDirectoryIDs.MUSIC_COLLECTION_FOLDER.getId(), group.getName(), "", 0);
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