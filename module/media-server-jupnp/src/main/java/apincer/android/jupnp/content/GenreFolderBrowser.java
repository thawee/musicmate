package apincer.android.jupnp.content;


import android.content.Context;

import org.jupnp.support.model.DIDLObject;
import org.jupnp.support.model.SortCriterion;
import org.jupnp.support.model.container.Container;
import org.jupnp.support.model.container.MusicGenre;
import org.jupnp.support.model.item.MusicTrack;

import java.util.ArrayList;
import java.util.List;

import apincer.android.mmate.core.database.MusicTag;
import apincer.android.mmate.core.repository.TagRepository;


/**
 * Browser for a music genre folder.
 */
public class GenreFolderBrowser extends AbstractContentBrowser {
    private static final String TAG = "GenreFolderBrowser";
    public GenreFolderBrowser(Context context, TagRepository tagRepos) {
        super(context, tagRepos);
    }

    @Override
    public DIDLObject browseMeta(ContentDirectory contentDirectory,
                                 String myId, long firstResult, long maxResults, SortCriterion[] orderby) {
        return new MusicGenre(myId, ContentDirectoryIDs.MUSIC_GENRES_FOLDER.getId(), myId, creator, getTotalMatches(
                contentDirectory, myId));
    }

    public Integer getTotalMatches(ContentDirectory contentDirectory, String myId) {
        String name = extractName(myId, ContentDirectoryIDs.MUSIC_GENRE_PREFIX);
        return getOrmLite().findByGenre(name).size();
    }

    @Override
    public List<Container> browseContainer(
            ContentDirectory contentDirectory, String myId, long firstResult, long maxResults, SortCriterion[] orderby) {

        return new ArrayList<>();
    }

    @Override
    public List<MusicTrack> browseItem(ContentDirectory contentDirectory,
                                       String myId, long firstResult, long maxResults, SortCriterion[] orderby) {
        List<MusicTrack> result = new ArrayList<>();
        String name = extractName(myId, ContentDirectoryIDs.MUSIC_GENRE_PREFIX);
        List<MusicTag> tags = getOrmLite().findByGenre(name, firstResult, maxResults);
        for(MusicTag tag: tags) {
                MusicTrack musicTrack = buildMusicTrack(contentDirectory, tag, myId, ContentDirectoryIDs.MUSIC_GENRE_ITEM_PREFIX.getId());
                result.add(musicTrack);
        }
        return result;
    }
}