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

import apincer.music.core.Constants;
import apincer.music.core.database.MusicTag;
import apincer.music.core.repository.TagRepository;

public class ResolutionFolderBrowser extends AbstractContentBrowser {
    private static final String TAG = "ResolutionFolderBrowser";
    public ResolutionFolderBrowser(Context context, TagRepository tagRepos) {
        super(context, tagRepos);
    }

    @Override
    public DIDLObject browseMeta(ContentDirectory contentDirectory,
                                 String myId, long firstResult, long maxResults, SortCriterion[] orderby) {
        return new StorageFolder(myId, ContentDirectoryIDs.MUSIC_RESOLUTION_FOLDER.getId(), myId, "mmate", getTotalMatches(
                contentDirectory, myId), null);
    }

    public Integer getTotalMatches(ContentDirectory contentDirectory, String myId) {
        String name = extractName(myId, ContentDirectoryIDs.MUSIC_RESOLUTION_PREFIX);
        return getItems(contentDirectory, name, 0, 0).size();
    }

    private List<MusicTag> getItems(ContentDirectory contentDirectory, String name, long firstResult, long maxResults) {
        if(Constants.TITLE_MASTER_AUDIO.equals(name)) {
            return tagRepos.findMQASongs(firstResult, maxResults);
        }
        if(Constants.TITLE_HIRES.equals(name)) {
            return tagRepos.findHiRes(firstResult, maxResults);
        }
        if(Constants.TITLE_HIFI_LOSSLESS.equals(name)) {
            return tagRepos.findLosslessSong(firstResult, maxResults);
        }
        if(Constants.TITLE_HIGH_QUALITY.equals(name)) {
            return tagRepos.findHighQuality(firstResult, maxResults);
        }
        if(Constants.TITLE_DSD.equals(name)) {
            return tagRepos.findDSDSongs(firstResult, maxResults);
        }
        return new ArrayList<>();
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
        String name = extractName(myId, ContentDirectoryIDs.MUSIC_RESOLUTION_PREFIX);
        List<MusicTag> tags = getItems(contentDirectory, name, firstResult, maxResults);
        for(MusicTag tag: tags) {
                MusicTrack musicTrack = buildMusicTrack(contentDirectory, tag, myId, ContentDirectoryIDs.MUSIC_RESOLUTION_ITEM_PREFIX.getId());
                result.add(musicTrack);
        }
        return result;
    }
}