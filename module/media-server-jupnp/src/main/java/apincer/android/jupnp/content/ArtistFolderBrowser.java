package apincer.android.jupnp.content;

import android.annotation.SuppressLint;
import android.content.Context;
import android.util.Log;

import org.jupnp.support.model.DIDLObject;
import org.jupnp.support.model.SortCriterion;
import org.jupnp.support.model.container.Container;
import org.jupnp.support.model.container.StorageFolder;
import org.jupnp.support.model.item.MusicTrack;

import java.util.ArrayList;
import java.util.List;

import apincer.android.mmate.core.database.MusicTag;
import apincer.android.mmate.core.repository.TagRepository;

/**
 * Browser for a music artist folder.
 */
public class ArtistFolderBrowser extends AbstractContentBrowser {
    public ArtistFolderBrowser(Context context, TagRepository tagRepos) {
        super(context, tagRepos);
    }

    @Override
    public DIDLObject browseMeta(ContentDirectory contentDirectory,
                                 String myId, long firstResult, long maxResults, SortCriterion[] orderby) {
        return new StorageFolder(myId, ContentDirectoryIDs.MUSIC_ARTISTS_FOLDER.getId(), myId, "mmate", getTotalMatches(
                contentDirectory, myId), null);
    }

    public Integer getTotalMatches(ContentDirectory contentDirectory, String myId) {
        String name = extractName(myId, ContentDirectoryIDs.MUSIC_ARTIST_PREFIX);
        return getOrmLite().findByArtist(name, 0, 0).size();
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
        String name = extractName(myId, ContentDirectoryIDs.MUSIC_ARTIST_PREFIX);
        List<MusicTag> tags = getOrmLite().findByArtist(name, firstResult, maxResults);
        for(MusicTag tag: tags) {
            try {
                MusicTrack musicTrack = buildMusicTrack(contentDirectory, tag, myId, ContentDirectoryIDs.MUSIC_ARTIST_ITEM_PREFIX.getId());
                result.add(musicTrack);
            } catch (Exception e) {
                // Log error but continue processing other tracks
                Log.e("ArtistFolderBrowser", "Error processing track: " + tag.getTitle(), e);
            }
        }
        return result;
    }
}