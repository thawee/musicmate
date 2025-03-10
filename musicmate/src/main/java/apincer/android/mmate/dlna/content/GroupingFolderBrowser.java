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

public class GroupingFolderBrowser extends AbstractContentBrowser {
    private static final String TAG = "GroupingFolderBrowser";
    public GroupingFolderBrowser(Context context) {
        super(context);
    }

    @Override
    public DIDLObject browseMeta(ContentDirectory contentDirectory,
                                 String myId, long firstResult, long maxResults, SortCriterion[] orderby) {
        return new StorageFolder(myId,
                ContentDirectoryIDs.MUSIC_GROUPING_FOLDER.getId(),
                myId,
                "mmate",
                getTotalMatches(contentDirectory, myId), null);
    }

    public Integer getTotalMatches(ContentDirectory contentDirectory, String myId) {
        String name = extractName(myId,ContentDirectoryIDs.MUSIC_GROUPING_PREFIX);
        String downloadName = getContext().getString(R.string.downloaded);
        if(downloadName.equals(name)) {
            return MusixMateApp.getInstance().getOrmLite().findMyIncomingSongs(0, 0).size();
        }else {
            return MusixMateApp.getInstance().getOrmLite().findByGrouping(name, 0, 0).size();
        }
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
        String name = extractName(myId, ContentDirectoryIDs.MUSIC_GROUPING_PREFIX);
        List<MusicTag> tags;
        String downloadName = getContext().getString(R.string.downloaded);
        if(downloadName.equals(name)) {
            tags = MusixMateApp.getInstance().getOrmLite().findMyIncomingSongs(firstResult, maxResults);
        }else {
            tags = MusixMateApp.getInstance().getOrmLite().findByGrouping(name, firstResult, maxResults);
        }
        //int currentCount = 0;
        for(MusicTag tag: tags) {
           // if ((currentCount >= firstResult) && currentCount < (firstResult+maxResults)){
                MusicTrack musicTrack = buildMusicTrack(contentDirectory, tag, myId, ContentDirectoryIDs.MUSIC_GROUPING_ITEM_PREFIX.getId());

                result.add(musicTrack);
           // }
           // if(!forceFullContent)  currentCount++;
            //currentCount++;
        }
        result.sort(Comparator.comparing(DIDLObject::getTitle));
        return result;
    }
}