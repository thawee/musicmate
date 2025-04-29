package apincer.android.mmate.repository;

import static apincer.android.mmate.Constants.ARTIST_SEP;
import static apincer.android.mmate.dlna.content.CollectionsBrowser.AUDIOPHILE_SONGS;
import static apincer.android.mmate.dlna.content.CollectionsBrowser.SMART_LIST_BAANTHUNG_SONGS;
import static apincer.android.mmate.dlna.content.CollectionsBrowser.SMART_LIST_CLASSIC_SONGS;
import static apincer.android.mmate.dlna.content.CollectionsBrowser.SMART_LIST_FINFIN_SONGS;
import static apincer.android.mmate.dlna.content.CollectionsBrowser.SMART_LIST_ISAAN_SONGS;
import static apincer.android.mmate.dlna.content.CollectionsBrowser.SMART_LIST_LOUNGE_SONGS;
import static apincer.android.mmate.dlna.content.CollectionsBrowser.SMART_LIST_TRADITIONAL_SONGS;
import static apincer.android.mmate.dlna.content.CollectionsBrowser.SMART_LIST_VOCAL_SONGS;
import static apincer.android.mmate.utils.StringUtils.isEmpty;
import static apincer.android.mmate.utils.StringUtils.trimToEmpty;

import android.content.Context;
import android.util.Log;

import org.apache.commons.codec.digest.DigestUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import apincer.android.mmate.Constants;
import apincer.android.mmate.MusixMateApp;
import apincer.android.mmate.Settings;
import apincer.android.mmate.R;
import apincer.android.mmate.dlna.content.CollectionsBrowser;
import apincer.android.mmate.utils.MusicTagUtils;
import apincer.android.mmate.utils.StringUtils;
import apincer.android.utils.FileUtils;

public class TagRepository {
    private static final String TAG = "TagRepository";
    public static List<String> lossyAudioFormatList;

    TagRepository() {
        lossyAudioFormatList = new ArrayList<>();
        lossyAudioFormatList.add("MP3");
        lossyAudioFormatList.add("AAC");
        lossyAudioFormatList.add("OGG");
        lossyAudioFormatList.add("WMA");
    }

    private static String escapeString(String text) {
        return text.replace("'","''");
    }

    public static void saveTag(MusicTag tag) {
        String path = StringUtils.trimToEmpty(tag.getPath()).toLowerCase();
        if(StringUtils.isEmpty(tag.getUniqueKey())) {
            String keyword = escapeString(path+"_"+ tag.getAudioStartTime());
            tag.setUniqueKey(keyword);
        }
        if(path.contains("/music/") && !path.contains("/telegram/")) {
            // if has album, use parent dir
            if(!StringUtils.isEmpty(tag.getAlbum())) {
                // use directory
                path = FileUtils.getParentName(path);
            }
        }
        tag.setAlbumUniqueKey(DigestUtils.md5Hex(path));

        if(StringUtils.isEmpty(tag.getGenre())) {
            tag.setGenre(Constants.NONE);
        }
        if(StringUtils.isEmpty(tag.getGrouping())) {
            tag.setGrouping(Constants.NONE);
        }
        if(StringUtils.isEmpty(tag.getArtist())) {
            tag.setArtist(Constants.EMPTY);
        }
        if(StringUtils.isEmpty(tag.getAlbumArtist())) {
            tag.setAlbumArtist(tag.getArtist());
        }

        MusixMateApp.getInstance().getOrmLite().save(tag);
    }

    public static void removeTag(MusicTag tag) {
        MusixMateApp.getInstance().getOrmLite().delete(tag);
    }

    public static boolean isOutdated(MusicTag tag, long lastModified) {
        OrmLiteHelper helper = MusixMateApp.getInstance().getOrmLite();
        return helper.isOutdated(tag, lastModified);
    }

    public static MusicTag getByPath(String path) {
        OrmLiteHelper helper = MusixMateApp.getInstance().getOrmLite();
        return helper.getByPath(path);
    }

    // Add this method
    public static void saveTagsBatch(List<MusicTag> tags) {
        OrmLiteHelper helper = MusixMateApp.getInstance().getOrmLite();
        helper.saveTagsBatch(tags);
    }

    public static void cleanMusicMate() {
        try {
            List<MusicTag> list =  MusixMateApp.getInstance().getOrmLite().findMySongs();
            for(int i=0; i<list.size();i++) {
                MusicTag mdata = list.get(i);
                String path = mdata.getPath();
                if(!FileRepository.isMediaFileExist(path) || mdata.getFileSize()==0.00) {
                    removeTag(mdata);
                }
            }
        } catch (Exception e) {
            Log.e(TAG,"cleanMusicMate",e);
        }
    }

    public static List<String> getActualGenreList(Context context) {
        List<String> list = new ArrayList<>();

        List<String> names = MusixMateApp.getInstance().getOrmLite().getGenres();
            for (String group:names) {
                if(StringUtils.isEmpty(group)) {
                    if(!list.contains(StringUtils.EMPTY)) {
                        list.add(StringUtils.EMPTY);
                    }
                }else {
                    list.add(group);
                }
            }

        Collections.sort(list);
        return list;
    }

    public static List<String> getDefaultGenreList(Context context) {
        List<String> list = new ArrayList<>();
        String[] genres =  context.getResources().getStringArray(R.array.default_genres);
        for(String genre: genres) {
            if(!list.contains(genre)) {
                list.add(genre);
            }
        }

        Collections.sort(list);
        return list;
    }
	
	public static List<String> getDefaultGroupingList(Context context) {
        String[] groupings =  context.getResources().getStringArray(R.array.default_groupings);
        List<String> list = new ArrayList<>(Arrays.asList(groupings));
        Collections.sort(list);
        return list;
    }

    public static List<String> getActualGroupingList(Context context) {
        List<String> list = new ArrayList<>();
        List<String> names = MusixMateApp.getInstance().getOrmLite().getGrouping();
        for (String group:names) {
            if(StringUtils.isEmpty(group)) {
                if(!list.contains(StringUtils.EMPTY)) {
                    list.add(StringUtils.EMPTY);
                }
            }else {
                list.add(group);
            }
        }
        Collections.sort(list);
        return list;
    }

    public static List<String> getGroupingList(Context context) {
        List<String> list = getActualGroupingList(context);
        String[] groupings =  context.getResources().getStringArray(R.array.default_groupings);
        for(String grouping: groupings) {
            if(!list.contains(grouping)) {
                list.add(grouping);
            }
        }

        Collections.sort(list);
        return list;
    }

    public static List<String> getArtistList() {
        List<String> list = MusixMateApp.getInstance().getOrmLite().getArtists();
        List<String> artistList = new ArrayList<>();
        for(String artist:list) {
            String[] arr = artist.split(ARTIST_SEP,-1);
            for(String a:arr) {
                a = trimToEmpty(a);
                if(!artistList.contains(a)) {
                    artistList.add(a);
                }
            }
        }

        Collections.sort(artistList);
        return artistList;
    }

    public static List<String> getDefaultAlbumArtistList(Context context) {
        List<String> list = new ArrayList<>();
        String[] names =  context.getResources().getStringArray(R.array.default_album_artist);
        for(String name: names) {
            if(!list.contains(name)) {
                list.add(name);
            }
        }
        Collections.sort(list);
        return list;
    }

    public static List<MusicTag> findByPath(String path) {
       return MusixMateApp.getInstance().getOrmLite().findByPath(path);
    }

    public static List<MusicTag> findInPath(String path) {
            return MusixMateApp.getInstance().getOrmLite().findInPath(path);
    }

    public static List<MusicTag> findMediaByTitle(String title) {
        return MusixMateApp.getInstance().getOrmLite().findByTitle(title);
    }

    public static List<MusicTag> findMediaTag(SearchCriteria criteria) {
        if(criteria.getType() == SearchCriteria.TYPE.COLLECTIONS) {
            List<MusicTag> results = new ArrayList<>();
            if(criteria.keyword != null) {
                List<MusicTag> list =  TagRepository.getAllMusicsForPlaylist(); //MusixMateApp.getInstance().getOrmLite().findMySongs();
                String name = criteria.keyword;
                for(MusicTag tag: list) {
                    if (CollectionsBrowser.DOWNLOADS_SONGS.equals(name) && MusicTagUtils.isOnDownloadDir(tag)) {
                        results.add(tag);
                    }else if (CollectionsBrowser.SMART_LIST_ISAAN_SONGS.equals(name) && MusicTagUtils.isISaanPlaylist(tag)) {
                        results.add(tag);
                    }else if (CollectionsBrowser.SMART_LIST_BAANTHUNG_SONGS.equals(name) && MusicTagUtils.isBaanThungPlaylist(tag)) {
                        results.add(tag);
                    }else if (CollectionsBrowser.SMART_LIST_FINFIN_SONGS.equals(name) && MusicTagUtils.isFinFinPlaylist(tag)) {
                        results.add(tag);
                    }else if (CollectionsBrowser.SMART_LIST_CLASSIC_SONGS.equals(name) && MusicTagUtils.isClassicPlaylist(tag)) {
                        results.add(tag);
                    }else if (CollectionsBrowser.SMART_LIST_LOUNGE_SONGS.equals(name) && MusicTagUtils.isLoungePlaylist(tag)) {
                        results.add(tag);
                    }else if (CollectionsBrowser.SMART_LIST_VOCAL_SONGS.equals(name) && MusicTagUtils.isVocalPlaylist(tag)) {
                        results.add(tag);
                    }else if (CollectionsBrowser.SMART_LIST_TRADITIONAL_SONGS.equals(name) && MusicTagUtils.isTraditionalPlaylist(tag)) {
                        results.add(tag);
                    }else if (CollectionsBrowser.AUDIOPHILE_SONGS.equals(name) && MusicTagUtils.isAudiophile(tag)) {
                        results.add(tag);
                    }else if(PlaylistRepository.isInAlbumPlaylist(tag, name)) {
                        results.add(tag);
                    }else if(PlaylistRepository.isInTitlePlaylist(tag, name)) {
                        results.add(tag);
                    }
                }
            }else {
                int index = 1;
                results.add(buildMusicTagPlaylist(index++, AUDIOPHILE_SONGS, "Audiophile Songs"));
                results.add(buildMusicTagPlaylist(index++, SMART_LIST_FINFIN_SONGS, "เพลงฮิตเพราะๆ เปิดปุ๊ปเพราะปั๊ป ฟังปั๊ปเพราะปุ๊ป"));  //"เพลงฟินๆ รินเบียร์เย็นๆ";
                results.add(buildMusicTagPlaylist(index++, SMART_LIST_VOCAL_SONGS, "เสียงใสๆ สกิดใจวัยรุ่น"));
                results.add(buildMusicTagPlaylist(index++, SMART_LIST_ISAAN_SONGS, "วาทะศิลป์ ถิ่นอีสาน ตำนานหมอลำ")); //"สะออนแฮง สำเนียงเสียงลำ";
                results.add(buildMusicTagPlaylist(index++, SMART_LIST_BAANTHUNG_SONGS, "ลูกทุ่งบ้านนา ฟังเพลินเหมือนเดินกลางทุ่ง")); // "คิดถึง บ้านทุ่งท้องนา";
                results.add(buildMusicTagPlaylist(index++, SMART_LIST_TRADITIONAL_SONGS, "เพลงพื้นบ้าน ตำนานท้องถิ่น ฟินๆ เพลินๆ"));
                results.add(buildMusicTagPlaylist(index++, SMART_LIST_CLASSIC_SONGS, "คลาสสิคเพราะๆ ละมุนละไม ในวันสบายๆ")); //"คลาสสิคกล่อมโลก ฟังแล้วอารมณ์ดี";
                results.add(buildMusicTagPlaylist(index++, SMART_LIST_LOUNGE_SONGS, "ฟังง่ายๆ ผ่อนคลาย สะบายอารมณ์"));

                for(String name: PlaylistRepository.getPlaylistNames()) {
                    results.add(buildMusicTagPlaylist(index++, name, name));
                }
                Collections.sort(results, (musicTag, t1) -> musicTag.getTitle().compareTo(t1.getTitle()));
            }
            return results;
        }else {
            List<MusicTag> results = findMediaTagOrEmpty(criteria);
            if (results == null || results.isEmpty()) {
                // try again
                results = findMediaTagOrEmpty(criteria);
            }
            return results;
        }
    }

    private static MusicFolder buildMusicTagPlaylist(int index, String key, String title) {
        MusicFolder tag = new MusicFolder("PLS", title);
        tag.setId(10000+index);
        return tag;
    }

    private static List<MusicTag> findMediaTagOrEmpty(SearchCriteria criteria) {
        try {
            return findMediaTagByCriteria(criteria);
        }catch (Exception e) {
            // retry one more time
            return OrmLiteHelper.EMPTY_LIST;
        }
    }

    private static List<MusicTag> findMediaTagByCriteria(SearchCriteria criteria) {
        List<MusicTag> list = new ArrayList<>();
        if (criteria.isSearchMode()) {
            // search title only, limit 5 songs
            list = MusixMateApp.getInstance().getOrmLite().findByKeyword(criteria.getSearchText());
        }else if (criteria.getType() == SearchCriteria.TYPE.LIBRARY) {
                if (StringUtils.isEmpty(criteria.getKeyword()) || Constants.TITLE_ALL_SONGS.equals(StringUtils.trimToEmpty(criteria.getKeyword()))) {
                    list = MusixMateApp.getInstance().getOrmLite().findMySongs();
                } else if (Constants.TITLE_INCOMING_SONGS.equals(criteria.getKeyword())) {
                    list = MusixMateApp.getInstance().getOrmLite().findMyIncomingSongs(0, 0);
                } else if (Constants.TITLE_BROKEN.equals(criteria.getKeyword())) {
                    list = MusixMateApp.getInstance().getOrmLite().findMyUnsatisfiedSongs();
                } else if (Constants.TITLE_TO_ANALYST_DR.equals(criteria.getKeyword())) {
                    list = MusixMateApp.getInstance().getOrmLite().findMyNoDRMeterSongs();
                } else if (Constants.TITLE_DUPLICATE.equals(criteria.getKeyword())) {
                    list = MusixMateApp.getInstance().getOrmLite().findDuplicateSong();
                } else if (Constants.TITLE_NO_COVERART.equals(criteria.getKeyword())) {
                    list = MusixMateApp.getInstance().getOrmLite().findNoEmbedCoverArtSong();
                }
            } else if (criteria.getType() == SearchCriteria.TYPE.PUBLISHER) {
                list = MusixMateApp.getInstance().getOrmLite().findByPublisher(criteria.getKeyword());
            } else if (criteria.getType() == SearchCriteria.TYPE.MEDIA_QUALITY) {
                list = MusixMateApp.getInstance().getOrmLite().findByMediaQuality(criteria.getKeyword());
            } else if (criteria.getType() == SearchCriteria.TYPE.AUDIO_ENCODINGS && Constants.TITLE_DSD.equals(criteria.keyword)) {
                list = MusixMateApp.getInstance().getOrmLite().findDSDSongs(0, 0);
            } else if (criteria.getType() == SearchCriteria.TYPE.AUDIO_ENCODINGS && Constants.TITLE_MASTER_AUDIO.equals(criteria.keyword)) {
                list = MusixMateApp.getInstance().getOrmLite().findMQASongs(0, 0);
            } else if (criteria.getType() == SearchCriteria.TYPE.AUDIO_ENCODINGS && Constants.TITLE_HIGH_QUALITY.equals(criteria.keyword)) {
                list = MusixMateApp.getInstance().getOrmLite().findHighQuality(0, 0);
            } else if (criteria.getType() == SearchCriteria.TYPE.AUDIO_ENCODINGS && Constants.TITLE_HIFI_LOSSLESS.equals(criteria.keyword)) {
                list = MusixMateApp.getInstance().getOrmLite().findLosslessSong(0, 0);
            } else if (criteria.getType() == SearchCriteria.TYPE.AUDIO_ENCODINGS && Constants.TITLE_HIRES.equals(criteria.keyword)) {
                list = MusixMateApp.getInstance().getOrmLite().findHiRes(0, 0);
            } else if (criteria.getType() == SearchCriteria.TYPE.GROUPING) {
                String val = criteria.getKeyword();
                if (isEmpty(val) || StringUtils.EMPTY.equalsIgnoreCase(val)) {
                    val = "";
                }
                list = MusixMateApp.getInstance().getOrmLite().findByGrouping(val, 0, 0);
            } else if (criteria.getType() == SearchCriteria.TYPE.GENRE) {

                String val = criteria.getKeyword();
                if (isEmpty(val) || StringUtils.EMPTY.equalsIgnoreCase(val)) {
                    val = "";
                }
                list = MusixMateApp.getInstance().getOrmLite().findByGenre(val);
          //  } else if (criteria.getType() == SearchCriteria.TYPE.SEARCH) {
                // search title only, limit 5 songs
          //      list = MusixMateApp.getInstance().getOrmLite().findByKeyword(criteria.getKeyword());
            } else {
                // default for MY_SONGS and others
                list = MusixMateApp.getInstance().getOrmLite().findMySongs();
            }
        return list;
    }

    @Deprecated
    public static MusicTag getMusicTag(long id) {
        return MusixMateApp.getInstance().getOrmLite().findById(id);
    }

    public static List<MusicTag> getAllMusics() {
        return MusixMateApp.getInstance().getOrmLite().findMySongs();
    }

    public static List<MusicTag> getAllMusicsForPlaylist() {
        OrmLiteHelper.ORDERED_BY [] aristAlbum = {OrmLiteHelper.ORDERED_BY.ARTIST, OrmLiteHelper.ORDERED_BY.ALBUM};
        return MusixMateApp.getInstance().getOrmLite().findMySongs(aristAlbum);
    }

    public static List<String> getDefaultPublisherList(Context context) {
        List<String> list = new ArrayList<>();
        List<String> names = MusixMateApp.getInstance().getOrmLite().getPublishers();
            for (String group:names) {
                if(StringUtils.isEmpty(group)) {
                    list.add(StringUtils.EMPTY);
                }else {
                    list.add(group);
                }
            }
        String[] genres =  context.getResources().getStringArray(R.array.default_publisher);
        for(String genre: genres) {
            if(!list.contains(genre)) {
                list.add(genre);
            }
        }

        Collections.sort(list);
        return list;
    }

    public static List<String> getPublisherList(Context context) {
        List<String> list = new ArrayList<>();
        List<String> names = MusixMateApp.getInstance().getOrmLite().getPublishers();
            for (String group:names) {
                if(StringUtils.isEmpty(group)) {
                    list.add(StringUtils.EMPTY);
                }else {
                    list.add(group);
                }
            }

        Collections.sort(list);
        return list;
    }

    public static void load(MusicTag tag) {
        MusicTag newTag = MusixMateApp.getInstance().getOrmLite().findById(tag.getId());
        if(newTag !=null) {
            tag.cloneFrom(newTag);
        }
    }

    @Deprecated
    public static List<MusicTag> getMusicTags(Context context, String grouping, String artist) {
        return MusixMateApp.getInstance().getOrmLite().findByGroupingAndArtist(grouping, artist);
    }

    @Deprecated
    public static List<MusicTag> getMusicTags(long idRange1, long idRange2) {
        return MusixMateApp.getInstance().getOrmLite().findByIdRanges(idRange1, idRange2);
    }

    public static Collection<MusicFolder> getRootDIRs(Context context) {
        List<MusicFolder> list = new ArrayList<>();
        List<String> musicDirs  = Settings.getDirectories(context);
            for(String musicDir: musicDirs) {
                    int indx = musicDir.lastIndexOf("/");
                    String name = musicDir;
                    if(indx >0 && !musicDir.endsWith("/")) {
                        name = musicDir.substring(indx+1);
                    }
                    if(musicDir.contains("/emulated/")) {
                        name = "Built-in/"+ name;
                    }else {
                        name = "SD Card/"+name;
                    }
                    MusicFolder dir = new MusicFolder(name);
                    dir.setUniqueKey(musicDir);
                    dir.setName(name);
                    dir.setChildCount(0);
                    list.add(dir);
        }
        return list;
    }

}