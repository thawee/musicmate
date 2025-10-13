package apincer.android.jupnp.content;

import android.content.Context;

import org.jupnp.support.model.DIDLObject;
import org.jupnp.support.model.SortCriterion;
import org.jupnp.support.model.container.Container;
import org.jupnp.support.model.container.GenreContainer;
import org.jupnp.support.model.container.MusicGenre;
import org.jupnp.support.model.item.Item;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import apincer.android.mmate.core.model.MusicFolder;
import apincer.android.mmate.core.repository.TagRepository;
import musicmate.mediaserver.jupnp.R;

/**
 * Browser  for the music genres folder.
 */
public class GenresBrowser extends AbstractContentBrowser {
    public GenresBrowser(Context context, TagRepository tagRepos) {
        super(context, tagRepos);
    }

    @Override
    public DIDLObject browseMeta(ContentDirectory contentDirectory, String myId, long firstResult, long maxResults, SortCriterion[] orderby) {
        return new GenreContainer(ContentDirectoryIDs.MUSIC_GENRES_FOLDER.getId(), ContentDirectoryIDs.MUSIC_FOLDER.getId(), getContext().getString(R.string.label_genres), creator, getTotalMatches(contentDirectory, myId));
    }

    public Integer getTotalMatches(ContentDirectory contentDirectory, String myId) {
        return getOrmLite().getGenres().size();
    }

    @Override
    public List<Container> browseContainer(ContentDirectory contentDirectory, String myId, long firstResult, long maxResults, SortCriterion[] orderby) {
        List<Container> result = new ArrayList<>();
        List<MusicFolder> genres = getOrmLite().getGenresWithChildrenCount();
        for(MusicFolder genre: genres) {
            MusicGenre musicGenre = new MusicGenre(ContentDirectoryIDs.MUSIC_GENRE_PREFIX.getId() + genre.getName(), ContentDirectoryIDs.MUSIC_GENRES_FOLDER.getId(), genre.getName(), "", (int)genre.getChildCount());
            result.add(musicGenre);
        }

        result.sort(Comparator.comparing(DIDLObject::getTitle));

        return result;
    }

    @Override
    public List<Item> browseItem(ContentDirectory contentDirectory, String myId, long firstResult, long maxResults, SortCriterion[] orderby) {
        return new ArrayList<>();
    }
}