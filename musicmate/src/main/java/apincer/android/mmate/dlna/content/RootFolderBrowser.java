package apincer.android.mmate.dlna.content;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.preference.PreferenceManager;

import org.jupnp.support.model.DIDLObject;
import org.jupnp.support.model.SortCriterion;
import org.jupnp.support.model.container.Container;
import org.jupnp.support.model.container.StorageFolder;
import org.jupnp.support.model.item.Item;

import java.util.ArrayList;
import java.util.List;

import apincer.android.mmate.dlna.MusicMateContentDirectory;

/**
 * Browser  for the root folder.
 *
 * @author openbit (Tobias Schoene)
 */
public class RootFolderBrowser extends ContentBrowser {
    public RootFolderBrowser(Context context) {
        super(context);
    }

    @Override
    public DIDLObject browseMeta(MusicMateContentDirectory contentDirectory, String myId, long firstResult, long maxResults, SortCriterion[] orderby) {

        return new StorageFolder(ContentDirectoryIDs.ROOT.getId(), ContentDirectoryIDs.PARENT_OF_ROOT.getId(), "mmate", "mmate", getSize(),
                null);
    }

    private Integer getSize() {
        int result = 1;
        return result;
    }


    @Override
    public List<Container> browseContainer(MusicMateContentDirectory contentDirectory, String myId, long firstResult, long maxResults, SortCriterion[] orderby) {
        List<Container> result = new ArrayList<>();
        result.add((Container) new MusicFolderBrowser(getContext()).browseMeta(contentDirectory, ContentDirectoryIDs.MUSIC_FOLDER.getId(), firstResult, maxResults, orderby
            ));
        return result;
    }

    @Override
    public List<Item> browseItem(MusicMateContentDirectory contentDirectory, String myId, long firstResult, long maxResults, SortCriterion[] orderby) {
        return new ArrayList<>();

    }
}