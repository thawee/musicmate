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

import apincer.android.mmate.R;
import apincer.android.mmate.repository.MusicFolder;
import apincer.android.mmate.repository.MusicTag;
import apincer.android.mmate.repository.PlaylistRepository;
import apincer.android.mmate.repository.TagRepository;
import apincer.android.mmate.utils.MusicTagUtils;

/**
 * Browser  for the music playlist folder.
 */
public class CollectionsBrowser extends AbstractContentBrowser {
  //  public static final String MY_SONGS = "All Songs";
    public static final String DOWNLOADS_SONGS = "Recently Added";
    public static final String AUDIOPHILE_SONGS = "Audiophile";
    //public static final String TOP50_AUDIOPHILE_ALBUMS = "Top50 Audiophile Albums";

    public static final String SMART_LIST_FINFIN_SONGS = "เพลงฮิตเพราะๆ เปิดปุ๊ปเพราะปั๊ป ฟังปั๊ปเพราะปุ๊ป";  //"เพลงฟินๆ รินเบียร์เย็นๆ";
   // public static final String SMART_LIST_FINFIN_EN_SONGS = "ฟังเพลงสากลฟินๆ รินเบียร์เย็นๆ";
    //public static final String SMART_LIST_FINFIN_TH_SONGS = "เพลงไทยฟินๆ รินเบียร์เย็นๆ";
   // public static final String SMART_LIST_RELAXED_TH_SONGS = "ยานอนหลับ ฉบับไทยๆ";
   // public static final String SMART_LIST_RELAXED_EN_SONGS = "ยานอนหลับ ฉบับสากล";
    public static final String SMART_LIST_VOCAL_SONGS = "เสียงใสๆ สกิดใจวัยรุ่น";
    public static final String SMART_LIST_ISAAN_SONGS = "วาทะศิลป์ ถิ่นอีสาน ตำนานหมอลำ"; //"สะออนแฮง สำเนียงเสียงลำ";
    public static final String SMART_LIST_BAANTHUNG_SONGS = "ลูกทุ่งบ้านนา ฟังเพลินเหมือนเดินกลางทุ่ง"; // "คิดถึง บ้านทุ่งท้องนา";
    public static final String SMART_LIST_TRADITIONAL_SONGS = "เพลงพื้นบ้าน ตำนานท้องถิ่น ฟินๆ เพลินๆ";
    public static final String SMART_LIST_CLASSIC_SONGS = "คลาสสิคเพราะๆ ละมุนละไม ในวันสบายๆ"; //"คลาสสิคกล่อมโลก ฟังแล้วอารมณ์ดี";
    public static final String SMART_LIST_LOUNGE_SONGS = "ฟังง่ายๆ ผ่อนคลาย สะบายอารมณ์";
   // public static final String SMART_LIST_INDIE_SONGS = "นอกกระแส แค่ฟังก็ฟิน";
    public static final List<String> playlists = new ArrayList<>();
    static {
       // playlists.add(MY_SONGS);
       // playlists.add(DOWNLOADS_SONGS);
        playlists.add(AUDIOPHILE_SONGS);
      //  playlists.add(TOP50_AUDIOPHILE_ALBUMS);
        playlists.add(SMART_LIST_FINFIN_SONGS);
        playlists.add(SMART_LIST_VOCAL_SONGS);
        playlists.add(SMART_LIST_BAANTHUNG_SONGS);
        playlists.add(SMART_LIST_TRADITIONAL_SONGS);
        playlists.add(SMART_LIST_ISAAN_SONGS);
        playlists.add(SMART_LIST_CLASSIC_SONGS);
        playlists.add(SMART_LIST_LOUNGE_SONGS);
        playlists.addAll(PlaylistRepository.getPlaylistNames());
    }
    public CollectionsBrowser(Context context) {
        super(context);
    }

    @Override
    public DIDLObject browseMeta(ContentDirectory contentDirectory, String myId, long firstResult, long maxResults, SortCriterion[] orderby) {

        return new StorageFolder(ContentDirectoryIDs.MUSIC_COLLECTION_FOLDER.getId(), ContentDirectoryIDs.MUSIC_FOLDER.getId(), getContext().getString(R.string.label_dlna_collections), "mmate", getTotalMatches(contentDirectory, myId),
                null);
    }

    public Integer getTotalMatches(ContentDirectory contentDirectory, String myId) {
        return playlists.size();
    }

    @Override
    public List<Container> browseContainer(ContentDirectory contentDirectory, String myId, long firstResult, long maxResults, SortCriterion[] orderby) {
        List<Container> result = new ArrayList<>();

        Map<String, MusicFolder> mapped = new HashMap<>();
        for(String pls: playlists) {
            MusicFolder dir = new MusicFolder(pls);
            dir.setUniqueKey(pls);
            mapped.put(pls, dir);
        }
        List<MusicTag> songs = TagRepository.getAllMusics();
        PlaylistRepository.initPlaylist(getContext());
        for(MusicTag tag: songs) {
           // Objects.requireNonNull(mapped.get(MY_SONGS)).addChildCount();
           // if(MusicTagUtils.isOnDownloadDir(tag)) {
           //    Objects.requireNonNull(mapped.get(DOWNLOADS_SONGS)).addChildCount();
           // }
            for (String name : PlaylistRepository.getPlaylistNames()) {
                if(PlaylistRepository.isInAlbumPlaylist(tag, name)) {
                    Objects.requireNonNull(mapped.get(name)).addChildCount();
                }else if(PlaylistRepository.isInTitlePlaylist(tag, name)) {
                    Objects.requireNonNull(mapped.get(name)).addChildCount();
                }
            }
          //  if(PlaylistRepository.isInAlbumPlaylist(tag, TOP50_AUDIOPHILE_ALBUMS)) {
          //      Objects.requireNonNull(mapped.get(TOP50_AUDIOPHILE_ALBUMS)).addChildCount();
          //  }
            if(MusicTagUtils.isISaanPlaylist(tag)) {
                Objects.requireNonNull(mapped.get(SMART_LIST_ISAAN_SONGS)).addChildCount();
            }
            if(MusicTagUtils.isAudiophile(tag)) {
                Objects.requireNonNull(mapped.get(AUDIOPHILE_SONGS)).addChildCount();
            }
            if(MusicTagUtils.isVocalPlaylist(tag)) {
                Objects.requireNonNull(mapped.get(SMART_LIST_VOCAL_SONGS)).addChildCount();
            }
            if(MusicTagUtils.isTraditionalPlaylist(tag)) {
                Objects.requireNonNull(mapped.get(SMART_LIST_TRADITIONAL_SONGS)).addChildCount();
            }
            if(MusicTagUtils.isFinFinPlaylist(tag)) {
                Objects.requireNonNull(mapped.get(SMART_LIST_FINFIN_SONGS)).addChildCount();
            }
            if(MusicTagUtils.isBaanThungPlaylist(tag)) {
                Objects.requireNonNull(mapped.get(SMART_LIST_BAANTHUNG_SONGS)).addChildCount();
            }
            if(MusicTagUtils.isClassicPlaylist(tag)) {
                Objects.requireNonNull(mapped.get(SMART_LIST_CLASSIC_SONGS)).addChildCount();
            }
            if(MusicTagUtils.isLoungePlaylist(tag)) {
                Objects.requireNonNull(mapped.get(SMART_LIST_LOUNGE_SONGS)).addChildCount();
            }
        }

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