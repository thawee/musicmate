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

import apincer.music.core.repository.PlaylistRepository;
import apincer.music.core.database.MusicTag;
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

    private List<MusicTag> getItems(ContentDirectory contentDirectory, String uuid) {
        List<MusicTag> results = new ArrayList<>();
        List<MusicTag> list = tagRepos.getAllMusicsForPlaylist();
        for(MusicTag tag: list) {
            if (CollectionsBrowser.DOWNLOADS_SONGS.equals(uuid) && TagUtils.isOnDownloadDir(tag)) {
                results.add(tag);
            }else if (PlaylistRepository.isSongInPlaylist(tag, uuid)) {
                results.add(tag);
            }
        }
        return results;
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
        List<MusicTag> tags = getItems(contentDirectory, uuid);
        for(MusicTag tag: tags) {
                MusicTrack musicTrack = buildMusicTrack(contentDirectory, tag, myId, ContentDirectoryIDs.MUSIC_COLLECTION_ITEM_PREFIX.getId());
                result.add(musicTrack);
        }
        return result;
    }
}