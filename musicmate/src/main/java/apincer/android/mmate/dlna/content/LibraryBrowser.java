package apincer.android.mmate.dlna.content;

import android.content.Context;

import org.jupnp.support.model.DIDLObject;
import org.jupnp.support.model.SortCriterion;
import org.jupnp.support.model.container.Container;
import org.jupnp.support.model.container.StorageFolder;
import org.jupnp.support.model.item.Item;

import java.util.ArrayList;
import java.util.List;

import apincer.android.mmate.R;
import apincer.android.mmate.dlna.ContentDirectory;

/**
 * Browser  for the music folder.
 *
 * @author openbit (Tobias Schoene)
 */
public class LibraryBrowser extends ContentBrowser {

    public LibraryBrowser(Context context) {
        super(context);
    }

    @Override
    public DIDLObject browseMeta(ContentDirectory contentDirectory, String myId, long firstResult, long maxResults, SortCriterion[] orderby) {

        return new StorageFolder(ContentDirectoryIDs.MUSIC_FOLDER.getId(), ContentDirectoryIDs.PARENT_OF_ROOT.getId(), getContext().getString(R.string.music), "mmate", 4,
                null);
    }

    @Override
    public List<Container> browseContainer(ContentDirectory contentDirectory, String myId, long firstResult, long maxResults, SortCriterion[] orderby) {
        List<Container> result = new ArrayList<>();
        result.add((Container) new AllTitlesBrowser(getContext()).browseMeta(contentDirectory, ContentDirectoryIDs.MUSIC_ALL_TITLES_FOLDER.getId(), firstResult, maxResults, orderby));
        result.add((Container) new DownloadsBrowser(getContext()).browseMeta(contentDirectory, ContentDirectoryIDs.MUSIC_DOWNLOADED_TITLES_FOLDER.getId(), firstResult, maxResults, orderby));
        result.add((Container) new AlbumsBrowser(getContext()).browseMeta(contentDirectory, ContentDirectoryIDs.MUSIC_ALBUMS_FOLDER.getId(), firstResult, maxResults, orderby));
        result.add((Container) new ArtistsBrowser(getContext()).browseMeta(contentDirectory, ContentDirectoryIDs.MUSIC_ARTISTS_FOLDER.getId(), firstResult, maxResults, orderby));
        result.add((Container) new GenresBrowser(getContext()).browseMeta(contentDirectory, ContentDirectoryIDs.MUSIC_GENRES_FOLDER.getId(), firstResult, maxResults, orderby));
        result.add((Container) new GroupingsBrowser(getContext()).browseMeta(contentDirectory, ContentDirectoryIDs.MUSIC_GROUPING_FOLDER.getId(), firstResult, maxResults, orderby));
        return result;
    }

    @Override
    public List<Item> browseItem(ContentDirectory contentDirectory, String myId, long firstResult, long maxResults, SortCriterion[] orderby) {
        return new ArrayList<>();
    }
}