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

import apincer.android.mmate.R;
import apincer.android.mmate.repository.model.MusicFolder;
import apincer.android.mmate.repository.database.MusicTag;
import apincer.android.mmate.repository.model.PlaylistEntry;
import apincer.android.mmate.repository.PlaylistRepository;
import apincer.android.mmate.repository.TagRepository;

/**
 * Browser  for the music playlist folder.
 */
public class CollectionsBrowser extends AbstractContentBrowser {
    public static final String DOWNLOADS_SONGS = "** Recently Added";
    public final List<String> playlists = new ArrayList<>();
    public CollectionsBrowser(Context context) {
        super(context);
        PlaylistRepository.initPlaylist(context);
        playlists.addAll(PlaylistRepository.getPlaylistNames());
    }

    @Override
    public DIDLObject browseMeta(ContentDirectory contentDirectory, String myId, long firstResult, long maxResults, SortCriterion[] orderby) {

        return new StorageFolder(ContentDirectoryIDs.MUSIC_COLLECTION_FOLDER.getId(), ContentDirectoryIDs.MUSIC_FOLDER.getId(), getContext().getString(R.string.label_dlna_collections), "mmate", getTotalMatches(contentDirectory, myId),
                null);
    }

    public Integer getTotalMatches(ContentDirectory contentDirectory, String myId) {
        return playlists.size();
    }

    @Override
    public List<Container> browseContainer(ContentDirectory contentDirectory, String myId, long firstResult, long maxResults, SortCriterion[] orderby) {
        List<Container> result = new ArrayList<>();

        PlaylistRepository.initPlaylist(getContext());

        Map<String, MusicFolder> mapped = new HashMap<>();
        for(String pls: playlists) {
            MusicFolder dir = new MusicFolder(pls);
            PlaylistEntry entry = PlaylistRepository.getPlaylistByName(pls);
            dir.setUniqueKey(entry.getUuid());
            mapped.put(pls, dir);
        }
        List<MusicTag> songs = TagRepository.getAllMusics();
        for(MusicTag tag: songs) {
            for (String name : PlaylistRepository.getPlaylistNames()) {
                if(PlaylistRepository.isSongInPlaylistName(tag, name)) {
                    Objects.requireNonNull(mapped.get(name)).increaseChildCount();
                }
            }
        }

        for(MusicFolder group: mapped.values()) {
            MusicGenre musicAlbum = new MusicGenre(ContentDirectoryIDs.MUSIC_COLLECTION_PREFIX.getId() + group.getUniqueKey(), ContentDirectoryIDs.MUSIC_COLLECTION_FOLDER.getId(), group.getName(), "", 0);
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