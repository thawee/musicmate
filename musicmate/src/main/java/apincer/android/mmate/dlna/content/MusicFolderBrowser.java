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
import apincer.android.mmate.dlna.MusicMateContentDirectory;

/**
 * Browser  for the music folder.
 *
 * @author openbit (Tobias Schoene)
 */
public class MusicFolderBrowser extends ContentBrowser {

    public MusicFolderBrowser(Context context) {
        super(context);
    }

    @Override
    public DIDLObject browseMeta(MusicMateContentDirectory contentDirectory, String myId, long firstResult, long maxResults, SortCriterion[] orderby) {

        return new StorageFolder(ContentDirectoryIDs.MUSIC_FOLDER.getId(), ContentDirectoryIDs.ROOT.getId(), getContext().getString(R.string.music), "mmate", 4,
                null);
    }

    @Override
    public List<Container> browseContainer(MusicMateContentDirectory contentDirectory, String myId, long firstResult, long maxResults, SortCriterion[] orderby) {
        List<Container> result = new ArrayList<>();
        result.add((Container) new MusicAllTitlesFolderBrowser(getContext()).browseMeta(contentDirectory, ContentDirectoryIDs.MUSIC_ALL_TITLES_FOLDER.getId(), firstResult, maxResults, orderby));
        result.add((Container) new MusicAlbumsFolderBrowser(getContext()).browseMeta(contentDirectory, ContentDirectoryIDs.MUSIC_ALBUMS_FOLDER.getId(), firstResult, maxResults, orderby));
        result.add((Container) new MusicArtistsFolderBrowser(getContext()).browseMeta(contentDirectory, ContentDirectoryIDs.MUSIC_ARTISTS_FOLDER.getId(), firstResult, maxResults, orderby));
        result.add((Container) new MusicGenresFolderBrowser(getContext()).browseMeta(contentDirectory, ContentDirectoryIDs.MUSIC_GENRES_FOLDER.getId(), firstResult, maxResults, orderby));
        return result;
    }

    @Override
    public List<Item> browseItem(MusicMateContentDirectory contentDirectory, String myId, long firstResult, long maxResults, SortCriterion[] orderby) {
        return new ArrayList<>();
    }

}