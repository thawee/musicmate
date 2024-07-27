package apincer.android.mmate.dlna.content;

import android.annotation.SuppressLint;
import android.content.Context;

import org.jupnp.support.model.DIDLObject;
import org.jupnp.support.model.SortCriterion;
import org.jupnp.support.model.container.Container;
import org.jupnp.support.model.container.StorageFolder;
import org.jupnp.support.model.item.MusicTrack;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import apincer.android.mmate.MusixMateApp;
import apincer.android.mmate.R;
import apincer.android.mmate.repository.MusicTag;

public class DownloadsBrowser extends ContentBrowser {
    private static final String TAG = "DownloadsBrowser";

    public DownloadsBrowser(Context context) {
        super(context);
    }

    @Override
    public DIDLObject browseMeta(ContentDirectory contentDirectory,
                                 String myId, long firstResult, long maxResults, SortCriterion[] orderby) {
        return new StorageFolder(myId, ContentDirectoryIDs.MUSIC_DOWNLOADED_TITLES_FOLDER.getId(), getContext().getString(R.string.downloaded), creator, getSize(
                contentDirectory, myId), null);

    }

    private Integer getSize(ContentDirectory contentDirectory, String myId) {
        return MusixMateApp.getInstance().getOrmLite().findMyIncomingSongs().size();
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
        List<MusicTag> tags = MusixMateApp.getInstance().getOrmLite().findMyIncomingSongs();
        int currentCount = 0;
        for(MusicTag tag: tags) {
            if ((currentCount >= firstResult) && currentCount < (firstResult+maxResults)){
                MusicTrack musicTrack = toMusicTrack(contentDirectory, tag, ContentDirectoryIDs.MUSIC_DOWNLOADED_TITLES_FOLDER.getId(), ContentDirectoryIDs.MUSIC_DOWNLOADED_TITLES_ITEM_PREFIX.getId());
                result.add(musicTrack);
            }
           if(!forceFullContent) currentCount++;
        }
        result.sort(Comparator.comparing(DIDLObject::getTitle));
        return result;
    }
}