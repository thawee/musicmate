package apincer.android.mmate.dlna.content;

import static apincer.android.mmate.dlna.transport.StreamServerImpl.forceFullContent;

import android.content.Context;

import org.jupnp.support.model.DIDLObject;
import org.jupnp.support.model.SortCriterion;
import org.jupnp.support.model.container.Container;
import org.jupnp.support.model.container.MusicGenre;
import org.jupnp.support.model.item.MusicTrack;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import apincer.android.mmate.MusixMateApp;
import apincer.android.mmate.repository.MusicTag;


/**
 * Browser for a music genre folder.
 */
public class GenreFolderBrowser extends ContentBrowser {
    private static final String TAG = "GenreFolderBrowser";
    public GenreFolderBrowser(Context context) {
        super(context);
    }

    @Override
    public DIDLObject browseMeta(ContentDirectory contentDirectory,
                                 String myId, long firstResult, long maxResults, SortCriterion[] orderby) {
        return new MusicGenre(myId, ContentDirectoryIDs.MUSIC_GENRES_FOLDER.getId(), myId, creator, getSize(
                contentDirectory, myId));
    }

    private Integer getSize(ContentDirectory contentDirectory, String myId) {
        String name = myId.substring(ContentDirectoryIDs.MUSIC_GENRE_PREFIX.getId().length());
        if("_EMPTY".equalsIgnoreCase(name) ||
                "_NULL".equalsIgnoreCase(name) ) {
               // "None".equalsIgnoreCase(name)) {
            name = "";
        }
        return MusixMateApp.getInstance().getOrmLite().findByGenre(name).size();
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
       /* String name = myId.substring(ContentDirectoryIDs.MUSIC_GENRE_PREFIX.getId().length());
        if("_EMPTY".equalsIgnoreCase(name) ||
                "_NULL".equalsIgnoreCase(name) ) {
            name = "";
        } */
        String name = extractName(myId, ContentDirectoryIDs.MUSIC_GENRE_PREFIX);
        List<MusicTag> tags = MusixMateApp.getInstance().getOrmLite().findByGenre(name);
        int currentCount = 0;
        for(MusicTag tag: tags) {
            if ((currentCount >= firstResult) && currentCount < (firstResult+maxResults)){
                MusicTrack musicTrack = toMusicTrack(contentDirectory, tag, myId, ContentDirectoryIDs.MUSIC_GENRE_ITEM_PREFIX.getId());
                result.add(musicTrack);
            }
            if(!forceFullContent)  currentCount++;
        }

        result.sort(Comparator.comparing(DIDLObject::getTitle));
        return result;

    }

}