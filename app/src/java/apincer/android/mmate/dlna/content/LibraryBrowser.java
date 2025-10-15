package apincer.android.mmate.dlna.content;

import android.content.Context;

import org.jupnp.support.model.DIDLObject;
import org.jupnp.support.model.SortCriterion;
import org.jupnp.support.model.container.Container;
import org.jupnp.support.model.container.StorageFolder;
import org.jupnp.support.model.item.Item;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import apincer.android.mmate.R;

/**
 * Browser  for the music folder.
 */
public class LibraryBrowser extends AbstractContentBrowser {

    public LibraryBrowser(Context context) {
        super(context);
    }

    @Override
    public DIDLObject browseMeta(ContentDirectory contentDirectory, String myId, long firstResult, long maxResults, SortCriterion[] orderby) {

        return new StorageFolder(ContentDirectoryIDs.MUSIC_FOLDER.getId(), ContentDirectoryIDs.PARENT_OF_ROOT.getId(), getContext().getString(R.string.music), "mmate", getTotalMatches(contentDirectory, myId),
                null);
    }

    @Override
    public List<Container> browseContainer(ContentDirectory contentDirectory, String myId, long firstResult, long maxResults, SortCriterion[] orderby) {
        // Common container classes:
        // object.container.storageFolder - Generic folder
        // object.container.album.musicAlbum - Music album
        // object.container.person.musicArtist - Music artist
        // object.container.genre.musicGenre - Music genre
        List<Container> result = new ArrayList<>();
        // recently added
        result.add((Container) new CollectionFolderBrowser(getContext()).browseMeta(contentDirectory, ContentDirectoryIDs.MUSIC_COLLECTION_PREFIX.getId()+CollectionsBrowser.DOWNLOADS_SONGS, firstResult, maxResults, orderby));
        result.add((Container) new CollectionsBrowser(getContext()).browseMeta(contentDirectory, ContentDirectoryIDs.MUSIC_COLLECTION_FOLDER.getId(), firstResult, maxResults, orderby));

        // result.add((Container) new AllTitlesBrowser(getContext()).browseMeta(contentDirectory, ContentDirectoryIDs.MUSIC_ALL_TITLES_FOLDER.getId(), firstResult, maxResults, orderby));
       // result.add((Container) new AlbumsBrowser(getContext()).browseMeta(contentDirectory, ContentDirectoryIDs.MUSIC_ALBUMS_FOLDER.getId(), firstResult, maxResults, orderby));
        result.add((Container) new ArtistsBrowser(getContext()).browseMeta(contentDirectory, ContentDirectoryIDs.MUSIC_ARTISTS_FOLDER.getId(), firstResult, maxResults, orderby));
        result.add((Container) new GenresBrowser(getContext()).browseMeta(contentDirectory, ContentDirectoryIDs.MUSIC_GENRES_FOLDER.getId(), firstResult, maxResults, orderby));
        result.add((Container) new GroupingsBrowser(getContext()).browseMeta(contentDirectory, ContentDirectoryIDs.MUSIC_GROUPING_FOLDER.getId(), firstResult, maxResults, orderby));

        //result.add((Container) new ResolutionsBrowser(getContext()).browseMeta(contentDirectory, ContentDirectoryIDs.MUSIC_RESOLUTION_FOLDER.getId(), firstResult, maxResults, orderby));
        result.add((Container) new SourcesBrowser(getContext()).browseMeta(contentDirectory, ContentDirectoryIDs.MUSIC_SOURCE_FOLDER.getId(), firstResult, maxResults, orderby));

        result.sort(Comparator.comparing(DIDLObject::getTitle));
        return result;
    }

    @Override
    public List<Item> browseItem(ContentDirectory contentDirectory, String myId, long firstResult, long maxResults, SortCriterion[] orderby) {
        return new ArrayList<>();
    }

    @Override
    public Integer getTotalMatches(ContentDirectory contentDirectory, String myId) {
        return browseContainer(contentDirectory, myId,0,0,null).size();
    }
}