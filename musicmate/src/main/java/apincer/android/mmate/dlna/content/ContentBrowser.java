package apincer.android.mmate.dlna.content;
import android.content.Context;
import android.util.Log;
import android.webkit.MimeTypeMap;

import org.jupnp.support.model.DIDLObject;
import org.jupnp.support.model.SortCriterion;
import org.jupnp.support.model.container.Container;
import org.jupnp.support.model.item.Item;
import org.jupnp.util.MimeType;

import java.util.ArrayList;
import java.util.List;

import apincer.android.mmate.dlna.MediaServerService;
import apincer.android.mmate.dlna.ContentDirectory;
import apincer.android.mmate.repository.MusicTag;


/**
 * Super class for all contentent directory browsers.
 *
 * @author openbit (Tobias Schoene)
 */
public abstract class ContentBrowser {

    Context context;

    protected ContentBrowser(Context context) {
        this.context = context;
    }

    public Context getContext() {
        return context;
    }


    public abstract DIDLObject browseMeta(ContentDirectory contentDirectory, String myId, long firstResult, long maxResults, SortCriterion[] orderby);

    public abstract List<Container> browseContainer(
            ContentDirectory content, String myId, long firstResult, long maxResults, SortCriterion[] orderby);

    public abstract List<? extends Item> browseItem(ContentDirectory contentDirectory, String myId, long firstResult, long maxResults, SortCriterion[] orderby);

    public List<DIDLObject> browseChildren(ContentDirectory contentDirectory, String myId, long firstResult, long maxResults, SortCriterion[] orderby) {
        List<DIDLObject> result = new ArrayList<>();
        result.addAll(browseContainer(contentDirectory, myId, firstResult, maxResults, orderby));
        result.addAll(browseItem(contentDirectory, myId, firstResult, maxResults, orderby));
        return result;
    }

    public String getUriString(ContentDirectory contentDirectory, String id, MimeType mimeType) {
        String fileExtension = MimeTypeMap.getSingleton().getExtensionFromMimeType(mimeType.toString());
        if (fileExtension == null) {
            Log.d(getClass().getName(), "Can't lookup file extension from mimetype: " + mimeType);
            //try subtype
            fileExtension = mimeType.getSubtype();

        }
        return "http://" + contentDirectory.getIpAddress() + ":"
                + MediaServerService.CONTENT_SERVER_PORT + "/res/" + id + "/file." + fileExtension;
    }

    public String getUriString(ContentDirectory contentDirectory, MusicTag tag) {
        return "http://" + contentDirectory.getIpAddress() + ":"
                + MediaServerService.CONTENT_SERVER_PORT + "/res/" + tag.getId() + "/file." + tag.getFileFormat();
    }

}