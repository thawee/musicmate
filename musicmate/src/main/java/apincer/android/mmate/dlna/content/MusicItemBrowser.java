package apincer.android.mmate.dlna.content;

import android.annotation.SuppressLint;
import android.content.Context;

import org.jupnp.support.model.DIDLObject;
import org.jupnp.support.model.SortCriterion;
import org.jupnp.support.model.container.Container;
import org.jupnp.support.model.item.Item;

import java.util.ArrayList;
import java.util.List;

import apincer.android.mmate.MusixMateApp;
import apincer.android.mmate.repository.MusicTag;
import apincer.android.mmate.utils.StringUtils;

/**
 * Browser for a music item, for 1st level music folder, i.e. All Title, Downloads
 *
 */
public class MusicItemBrowser extends ContentBrowser {
    private static final String TAG = "MusicItemBrowser";
    private final String folderId;
    private final String itemPrefix;

    public MusicItemBrowser(Context context, String folderId,String itemPrefix) {
        super(context);
        this.folderId = folderId;
        this.itemPrefix = itemPrefix;
    }

    @SuppressLint("Range")
    @Override
    public DIDLObject browseMeta(ContentDirectory contentDirectory,
                                 String myId, long firstResult, long maxResults, SortCriterion[] orderby) {
        Item result = null;

        String id = myId.substring(itemPrefix.length());
        MusicTag tag = MusixMateApp.getInstance().getOrmLite().findById(StringUtils.toLong(id));
        result = toMusicTrack(contentDirectory, tag, folderId, itemPrefix);

        return result;
    }

    @Override
    public List<Container> browseContainer(
            ContentDirectory contentDirectory, String myId, long firstResult, long maxResults, SortCriterion[] orderby) {

        return new ArrayList<>();
    }

    @Override
    public List<Item> browseItem(ContentDirectory contentDirectory,
                                 String myId, long firstResult, long maxResults, SortCriterion[] orderby) {
        List<Item> result = new ArrayList<>();
        result.add((Item) browseMeta(contentDirectory, myId, firstResult, maxResults, orderby));
        return result;
    }
}