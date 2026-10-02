package apincer.music.server.jupnp.content;

import android.annotation.SuppressLint;
import android.content.Context;

import org.jupnp.support.model.DIDLObject;
import org.jupnp.support.model.SortCriterion;
import org.jupnp.support.model.container.Container;
import org.jupnp.support.model.container.StorageFolder;
import org.jupnp.support.model.item.MusicTrack;

import java.util.ArrayList;
import java.util.List;

import apincer.music.core.model.Track;
import apincer.music.core.repository.PlaylistRepository;
import apincer.music.core.repository.TagRepository;
import apincer.music.core.utils.TagUtils;

public class CollectionFolderBrowser extends AbstractContentBrowser {
    private static final String TAG = "CollectionFolderBrowser";
    public CollectionFolderBrowser(Context context, TagRepository tagRepos) {
        super(context, tagRepos);
    }

    @Override
    public DIDLObject browseMeta(ContentDirectory contentDirectory,
                                 String myId, long firstResult, long maxResults, SortCriterion[] orderby) {
        return new StorageFolder(myId, ContentDirectoryIDs.MUSIC_COLLECTION_FOLDER.getId(), extractName(myId, ContentDirectoryIDs.MUSIC_COLLECTION_PREFIX), "mmate", getTotalMatches(
                contentDirectory, myId), null);
    }

    public Integer getTotalMatches(ContentDirectory contentDirectory, String myId) {
        String name = extractName(myId, ContentDirectoryIDs.MUSIC_COLLECTION_PREFIX);
        return getItems(contentDirectory, name).size();
    }

    // A browser serves one Browse request: the page and TotalMatches share one library scan
    private List<Track> items;
    private String itemsUuid;

    private List<Track> getItems(ContentDirectory contentDirectory, String uuid) {
        if (items != null && uuid.equals(itemsUuid)) {
            return items;
        }
        List<Track> list = tagRepos.getAllMusicsForPlaylist();
        if (clientProfile.adapts()) {
            list = new ArrayList<>(list);
            list.removeIf(clientProfile::hides); // e.g. DSD for TVs
        }
        List<Track> results;
        if (CollectionsBrowser.ALL_SONGS.equals(uuid)) {
            results = list;
        } else {
            java.util.function.Predicate<Track> member = CollectionsBrowser.DOWNLOADS_SONGS.equals(uuid)
                    ? TagUtils::isOnDownloadDir
                    : PlaylistRepository.playlistFilterByUuid(uuid);
            results = new ArrayList<>();
            for (Track tag : list) {
                if (member.test(tag)) results.add(tag);
            }
        }
        items = results;
        itemsUuid = uuid;
        return results;
    }

    /** Hides the client's unplayable formats in its track list (before paging and counting). */
    @Override
    protected boolean filtersChildren() {
        return true;
    }

    /** Sorts the track list itself, so a sorted "All Songs" page stays cheap. */
    @Override
    protected boolean sortsChildren() {
        return true;
    }

    /** Pages the track list before building DIDL items; "All Songs" has thousands of tracks. */
    @Override
    protected boolean pagesChildren() {
        return true;
    }

    @Override
    public List<Container> browseContainer(
            ContentDirectory contentDirectory, String myId, long firstResult, long maxResults, SortCriterion[] orderby) {
        return new ArrayList<>();
    }

    @SuppressLint("Range")
    @Override
    public List<MusicTrack> browseItem(ContentDirectory contentDirectory,
                                       String myId, long firstResult, long maxResults, SortCriterion[] orderby) {
        List<MusicTrack> result = new ArrayList<>();
        String uuid = extractName(myId, ContentDirectoryIDs.MUSIC_COLLECTION_PREFIX);
        List<Track> all = getItems(contentDirectory, uuid);
        java.util.Comparator<Track> sort = BrowseSort.tracks(orderby);
        if (sort != null) {
            all = new ArrayList<>(all);
            all.sort(sort);
        }
        List<Track> tags = page(all, firstResult, maxResults);
        for(Track tag: tags) {
                MusicTrack musicTrack = buildMusicTrack(contentDirectory, tag, myId, ContentDirectoryIDs.MUSIC_COLLECTION_ITEM_PREFIX.getId());
                result.add(musicTrack);
        }
        return result;
    }
}