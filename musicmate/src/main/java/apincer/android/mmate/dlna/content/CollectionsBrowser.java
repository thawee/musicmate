package apincer.android.mmate.dlna.content;

import android.content.Context;

import org.jupnp.support.model.DIDLObject;
import org.jupnp.support.model.SortCriterion;
import org.jupnp.support.model.container.Container;
import org.jupnp.support.model.container.MusicGenre;
import org.jupnp.support.model.container.StorageFolder;
import org.jupnp.support.model.item.Item;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import apincer.android.mmate.MusixMateApp;
import apincer.android.mmate.R;
import apincer.android.mmate.repository.MusicFolder;
import apincer.android.mmate.repository.MusicTag;
import apincer.android.mmate.repository.TagRepository;
import apincer.android.mmate.utils.MusicTagUtils;

/**
 * Browser  for the music playlist folder.
 */
public class CollectionsBrowser extends ContentBrowser {
    public static final String MY_SONGS = "All Songs";
    public static final String DOWNLOADS_SONGS = "Download Songs";
    public static final String DUPLICATED_SONGS = "Duplicate Songs";
    public static final String SMART_LIST_FINFIN_SONGS = "เพลงฟินๆ รินเบียร์เย็นๆ";
    public static final String SMART_LIST_FINFIN_EN_SONGS = "เพลงสากลฟินๆ รินเบียร์เย็นๆ";
    public static final String SMART_LIST_FINFIN_TH_SONGS = "เพลงไทยฟินๆ รินเบียร์เย็นๆ";
    public static final String SMART_LIST_RELAXED_TH_SONGS = "ยานอนหลับ ฉบับไทยๆ";
    public static final String SMART_LIST_RELAXED_EN_SONGS = "ยานอนหลับ ฉบับสากล";
    public static final String SMART_LIST_RELAXED_SONGS = "ยานอนหลับ ฉบับรวมมิตร";
    public static final String SMART_LIST_ISAAN_SONGS = "สะออนแฮง สำเนียงเสียงลำ";
    public static final String SMART_LIST_BAANTHUNG_SONGS = "คิดถึง บ้านทุ่งท้องนา";
    public static final String SMART_LIST_CLASSIC_SONGS = "คลาสสิคกล่อมโลก ฟังแล้วอารมณ์ดี";
   // public static final String SMART_LIST_INDIE_SONGS = "นอกกระแส แค่ฟังก็ฟิน";
    public static final List<String> playlists = new ArrayList<>();
    static {
        playlists.add(MY_SONGS);
        playlists.add(DOWNLOADS_SONGS);
        playlists.add(DUPLICATED_SONGS);
        playlists.add(SMART_LIST_FINFIN_SONGS);
        playlists.add(SMART_LIST_FINFIN_TH_SONGS);
        playlists.add(SMART_LIST_FINFIN_EN_SONGS);
        playlists.add(SMART_LIST_RELAXED_TH_SONGS);
        playlists.add(SMART_LIST_RELAXED_EN_SONGS);
        playlists.add(SMART_LIST_RELAXED_SONGS);
        playlists.add(SMART_LIST_BAANTHUNG_SONGS);
        playlists.add(SMART_LIST_ISAAN_SONGS);
        playlists.add(SMART_LIST_CLASSIC_SONGS);
     //   playlists.add(SMART_LIST_INDIE_SONGS);
    }
    public CollectionsBrowser(Context context) {
        super(context);
    }

    @Override
    public DIDLObject browseMeta(ContentDirectory contentDirectory, String myId, long firstResult, long maxResults, SortCriterion[] orderby) {

        return new StorageFolder(ContentDirectoryIDs.MUSIC_COLLECTION_FOLDER.getId(), ContentDirectoryIDs.MUSIC_FOLDER.getId(), getContext().getString(R.string.label_dlna_collections), "mmate", getSize(contentDirectory, myId),
                null);
    }

    private Integer getSize(ContentDirectory contentDirectory, String myId) {
        return playlists.size();
    }

    @Override
    public List<Container> browseContainer(ContentDirectory contentDirectory, String myId, long firstResult, long maxResults, SortCriterion[] orderby) {
        List<Container> result = new ArrayList<>();

        Map<String, MusicFolder> mapped = new HashMap<>();
        for(String pls: playlists) {
            MusicFolder dir = new MusicFolder();
            dir.setUniqueKey(pls);
            dir.setName(pls);
            mapped.put(pls, dir);
        }
        List<MusicTag> songs = TagRepository.getAllMusics();
        for(MusicTag tag: songs) {
            Objects.requireNonNull(mapped.get(MY_SONGS)).addChildCount();
           if(MusicTagUtils.isOnDownloadDir(tag)) {
               Objects.requireNonNull(mapped.get(DOWNLOADS_SONGS)).addChildCount();
           }
            if(MusicTagUtils.isISaanPlaylist(tag)) {
                Objects.requireNonNull(mapped.get(SMART_LIST_ISAAN_SONGS)).addChildCount();
            }
            if(MusicTagUtils.isRelaxedThaiPlaylist(tag)) {
                Objects.requireNonNull(mapped.get(SMART_LIST_RELAXED_TH_SONGS)).addChildCount();
            }
            if(MusicTagUtils.isRelaxedEnglishPlaylist(tag)) {
                Objects.requireNonNull(mapped.get(SMART_LIST_RELAXED_EN_SONGS)).addChildCount();
            }
            if(MusicTagUtils.isRelaxedPlaylist(tag)) {
                Objects.requireNonNull(mapped.get(SMART_LIST_RELAXED_SONGS)).addChildCount();
            }
            if(MusicTagUtils.isFinFinPlaylist(tag)) {
                Objects.requireNonNull(mapped.get(SMART_LIST_FINFIN_SONGS)).addChildCount();
            }
            if(MusicTagUtils.isFinFinThaiPlaylist(tag)) {
                Objects.requireNonNull(mapped.get(SMART_LIST_FINFIN_TH_SONGS)).addChildCount();
            }
            if(MusicTagUtils.isFinFinEnglishPlaylist(tag)) {
                Objects.requireNonNull(mapped.get(SMART_LIST_FINFIN_EN_SONGS)).addChildCount();
            }
            if(MusicTagUtils.isBaanThungPlaylist(tag)) {
                Objects.requireNonNull(mapped.get(SMART_LIST_BAANTHUNG_SONGS)).addChildCount();
            }
            if(MusicTagUtils.isClassicPlaylist(tag)) {
                Objects.requireNonNull(mapped.get(SMART_LIST_CLASSIC_SONGS)).addChildCount();
            }
           /* if(MusicTagUtils.isIndiePlaylist(tag)) {
                Objects.requireNonNull(mapped.get(SMART_LIST_INDIE_SONGS)).addChildCount();
            }*/

        }
        List<MusicTag> tags = MusixMateApp.getInstance().getOrmLite().findDuplicateSong();
        Objects.requireNonNull(mapped.get(DUPLICATED_SONGS)).setChildCount(tags.size());

        for(MusicFolder group: mapped.values()) {
            MusicGenre musicAlbum = new MusicGenre(ContentDirectoryIDs.MUSIC_COLLECTION_PREFIX.getId() + group.getName(), ContentDirectoryIDs.MUSIC_COLLECTION_FOLDER.getId(), group.getName(), "", 0);
            musicAlbum.setChildCount((int)group.getChildCount());
            result.add(musicAlbum);
        }
        result.sort(Comparator.comparing(DIDLObject::getTitle));
        return result;
    }

    @Override
    public List<Item> browseItem(ContentDirectory contentDirectory, String myId, long firstResult, long maxResults, SortCriterion[] orderby) {
        return new ArrayList<>();
    }
}