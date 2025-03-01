package apincer.android.mmate.dlna.content;

import static apincer.android.mmate.dlna.transport.StreamServerImpl.forceFullContent;

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
import apincer.android.mmate.repository.MusicTag;
import apincer.android.mmate.utils.MusicTagUtils;

public class CollectionFolderBrowser extends ContentBrowser {
    private static final String TAG = "CollectionFolderBrowser";
    public CollectionFolderBrowser(Context context) {
        super(context);
    }

    @Override
    public DIDLObject browseMeta(ContentDirectory contentDirectory,
                                 String myId, long firstResult, long maxResults, SortCriterion[] orderby) {
        return new StorageFolder(myId, ContentDirectoryIDs.MUSIC_COLLECTION_FOLDER.getId(), myId, "mmate", getSize(
                contentDirectory, myId), null);
    }

    private Integer getSize(ContentDirectory contentDirectory, String myId) {
        String name = myId.substring(ContentDirectoryIDs.MUSIC_COLLECTION_PREFIX.getId().length());
        if("_EMPTY".equalsIgnoreCase(name) ||
                "_NULL".equalsIgnoreCase(name)) { // ||
               // "None".equalsIgnoreCase(name)) {
            name = "";
        }
        return getItems(contentDirectory, name).size();
    }

    private List<MusicTag> getItems(ContentDirectory contentDirectory, String name) {
        if(CollectionsBrowser.MY_SONGS.equals(name)) {
            return MusixMateApp.getInstance().getOrmLite().findMySongs();
        }
        //if(CollectionsBrowser.DUPLICATED_SONGS.equals(name)) {
        //    return MusixMateApp.getInstance().getOrmLite().findDuplicateSong();
        //}
        List<MusicTag> results = new ArrayList<>();
        List<MusicTag> list = MusixMateApp.getInstance().getOrmLite().findMySongs();
        for(MusicTag tag: list) {
            if (CollectionsBrowser.DOWNLOADS_SONGS.equals(name) && MusicTagUtils.isOnDownloadDir(tag)) {
                results.add(tag);
            }else if (CollectionsBrowser.SMART_LIST_ISAAN_SONGS.equals(name) && MusicTagUtils.isISaanPlaylist(tag)) {
                results.add(tag);
            }else if (CollectionsBrowser.SMART_LIST_BAANTHUNG_SONGS.equals(name) && MusicTagUtils.isBaanThungPlaylist(tag)) {
                results.add(tag);
            }else if (CollectionsBrowser.SMART_LIST_FINFIN_SONGS.equals(name) && MusicTagUtils.isFinFinPlaylist(tag)) {
                results.add(tag);
            //}else if (CollectionsBrowser.SMART_LIST_FINFIN_TH_SONGS.equals(name) && MusicTagUtils.isThaiFinFinPlaylist(tag)) {
            //    results.add(tag);
           // }else if (CollectionsBrowser.SMART_LIST_FINFIN_EN_SONGS.equals(name) && MusicTagUtils.isEnglishFinFinPlaylist(tag)) {
           //     results.add(tag);
            }else if (CollectionsBrowser.SMART_LIST_CLASSIC_SONGS.equals(name) && MusicTagUtils.isClassicPlaylist(tag)) {
                results.add(tag);
            }else if (CollectionsBrowser.SMART_LIST_VOCAL_SONGS.equals(name) && MusicTagUtils.isVocalPlaylist(tag)) {
                results.add(tag);
            }else if (CollectionsBrowser.SMART_LIST_TRADITIONAL_SONGS.equals(name) && MusicTagUtils.isTraditionalPlaylist(tag)) {
                results.add(tag);
          //  }else if (CollectionsBrowser.SMART_LIST_RELAXED_TH_SONGS.equals(name) && MusicTagUtils.isRelaxedThaiPlaylist(tag)) {
           //     results.add(tag);
           // }else if (CollectionsBrowser.SMART_LIST_RELAXED_EN_SONGS.equals(name) && MusicTagUtils.isRelaxedEnglishPlaylist(tag)) {
           //     results.add(tag);
            }
        }
        return results;
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
        String name = extractName(myId, ContentDirectoryIDs.MUSIC_COLLECTION_PREFIX);
        List<MusicTag> tags = getItems(contentDirectory, name);
        int currentCount = 0;
        for(MusicTag tag: tags) {
            if ((currentCount >= firstResult) && currentCount < (firstResult+maxResults)){
                MusicTrack musicTrack = toMusicTrack(contentDirectory, tag, myId, ContentDirectoryIDs.MUSIC_COLLECTION_ITEM_PREFIX.getId());
                result.add(musicTrack);
            }
            if(!forceFullContent)  currentCount++;
            //currentCount++;
        }
        result.sort(Comparator.comparing(DIDLObject::getTitle));
        return result;
    }
}