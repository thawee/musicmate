package apincer.android.mmate.repository;

import static apincer.android.mmate.Constants.ARTIST_SEP;
import static apincer.android.mmate.utils.StringUtils.isEmpty;
import static apincer.android.mmate.utils.StringUtils.trimToEmpty;

import android.content.Context;
import android.util.Log;

import com.j256.ormlite.dao.Dao;
import com.j256.ormlite.stmt.QueryBuilder;

import org.apache.commons.codec.digest.DigestUtils;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import apincer.android.mmate.Constants;
import apincer.android.mmate.MusixMateApp;
import apincer.android.mmate.Settings;
import apincer.android.mmate.R;
import apincer.android.mmate.repository.database.MusicTag;
import apincer.android.mmate.repository.model.MusicFolder;
import apincer.android.mmate.repository.model.SearchCriteria;
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
        tag.setAlbumCoverUniqueKey(DigestUtils.md5Hex(path)); // used for cached coverart

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

        // update id is unique key is valid
        if(tag.getId() == 0) {
            MusicTag existingTag = MusixMateApp.getInstance().getOrmLite().findByUniqueKey(tag.getUniqueKey());
            if(existingTag != null) {
                tag.setId(existingTag.getId());
                if(tag.getDynamicRange()==0) {
                    // not set, use from existing tag
                    tag.setDynamicRange(existingTag.getDynamicRange());
                    tag.setQualityInd(existingTag.getQualityInd());
                    tag.setMqaSampleRate(existingTag.getMqaSampleRate());
                    tag.setQualityRating(existingTag.getQualityRating());
                    tag.setWaveformData(existingTag.getWaveformData());
                   // tag.setResampledScore(existingTag.getResampledScore());
                   // tag.setUpscaledScore(existingTag.getUpscaledScore());
                }
            }
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

    public static MusicTag findMediaItem(String currentTitle, String currentArtist, String currentAlbum) {
        try {
            List<MusicTag> list = TagRepository.findMediaByTitle(currentTitle);

            double prvTitleScore = 0.0;
            double prvArtistScore = 0.0;
            double prvAlbumScore = 0.0;
            double titleScore;
            double artistScore;
            double albumScore;
            MusicTag matchedMeta = null;

            for (MusicTag metadata : list) {
                titleScore = StringUtils.similarity(currentTitle, metadata.getTitle());
                artistScore = StringUtils.similarity(currentArtist, metadata.getArtist());
                albumScore = StringUtils.similarity(currentAlbum, metadata.getAlbum());

                if (getSimilarScore(titleScore, artistScore, albumScore) > getSimilarScore(prvTitleScore, prvArtistScore, prvAlbumScore)) {
                    matchedMeta = metadata;
                    prvTitleScore = titleScore;
                    prvArtistScore = artistScore;
                    prvAlbumScore = albumScore;
                }
            }
            if (matchedMeta != null) {
                return matchedMeta.copy();
            }
        }catch (Exception e) {
            Log.e(TAG, "findMediaItem",e);
        }
        return null;
    }

    private static double getSimilarScore(double titleScore, double artistScore, double albumScore) {
        return (titleScore*60)+(artistScore*20)+(albumScore*20);
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

    public static List<String> getActualGenreList() {
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
        if(criteria.getType() == SearchCriteria.TYPE.PLAYLIST) {
            return findMediaCollection(criteria);
        }else {
            List<MusicTag> results = findMediaTagOrEmpty(criteria);
            if (results == null || results.isEmpty()) {
                // try again
                results = findMediaTagOrEmpty(criteria);
            }
            return results;
        }
    }

    public static List<MusicTag> findMediaCollection(SearchCriteria criteria) {
        List<MusicTag> results = new ArrayList<>();
        if (criteria.getType() == SearchCriteria.TYPE.PLAYLIST) {
            if (criteria.getKeyword() != null) {
                List<MusicTag> list = TagRepository.getAllMusicsForPlaylist(); //MusixMateApp.getInstance().getOrmLite().findMySongs();
                String name = criteria.getKeyword();
                for (MusicTag tag : list) {
                    if(PlaylistRepository.isSongInPlaylistName(tag, name)) {
                        results.add(tag);
                    }
                }
                if(PlaylistRepository.isTitlePlaylist(name)) {
                    // add missing titles
                    List<MusicTag> missingList = PlaylistRepository.getMissingSongsForPlaylist(name, results);
                    missingList.sort(Comparator.comparing(MusicTag::getTitle));
                    results.addAll(missingList);
                }
            } else {
                int index = 1;
                for (String name : PlaylistRepository.getPlaylistNames()) {
                    results.add(buildMusicTagPlaylist(index++, name, name));
                }
                results.sort(Comparator.comparing(MusicTag::getTitle));

                // Create a map of playlist containers if not already done
                Map<String, MusicTag> playlistMap = new HashMap<>();
                for (MusicTag item : results) {
                    if (item instanceof MusicFolder) {
                        playlistMap.put(item.getUniqueKey(), item);
                    }
                }
                List<MusicTag> list = TagRepository.getAllMusicsForPlaylist(); //MusixMateApp.getInstance().getOrmLite().findMySongs();
                for(MusicTag tag: list) {

                    for (String name : PlaylistRepository.getPlaylistNames()) {
                        if (PlaylistRepository.isSongInPlaylistName(tag, name)) {
                            updateMusicFolder(playlistMap,name, tag);
                        }
                    }
                }
            }
        }
        return results;
    }

    public static void updateMusicFolder(Map<String, MusicTag> playlistMap, String key, MusicTag tag) {
        if(playlistMap.containsKey(key)) {
            MusicFolder folder = (MusicFolder) playlistMap.get(key);
            folder.addChildCount();
            folder.setFileSize(folder.getFileSize() + tag.getFileSize());
            folder.setAudioDuration(folder.getAudioDuration() + tag.getAudioDuration());
        }
    }

    private static MusicFolder buildMusicTagPlaylist(int index, String key, String title) {
        MusicFolder tag = new MusicFolder("PLS", title);
        tag.setUniqueKey(key);
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
                    list = findRecentlyAdded(0, 0);
               // } else if (Constants.TITLE_BROKEN.equals(criteria.getKeyword())) {
                //    list = MusixMateApp.getInstance().getOrmLite().findMyUnsatisfiedSongs();
                } else if (Constants.TITLE_TO_ANALYST_DR.equals(criteria.getKeyword())) {
                    list = MusixMateApp.getInstance().getOrmLite().findMyNoDRMeterSongs();
                } else if (Constants.TITLE_DUPLICATE.equals(criteria.getKeyword())) {
                    list = MusixMateApp.getInstance().getOrmLite().findDuplicateSong();
                } else if (Constants.TITLE_NO_COVERART.equals(criteria.getKeyword())) {
                    list = MusixMateApp.getInstance().getOrmLite().findNoEmbedCoverArtSong();
                }
            } else if (criteria.getType() == SearchCriteria.TYPE.PUBLISHER) {
                list = MusixMateApp.getInstance().getOrmLite().findByPublisher(criteria.getKeyword());
            } else if (criteria.getType() == SearchCriteria.TYPE.ARTIST) {
                list = MusixMateApp.getInstance().getOrmLite().findByArtist(criteria.getKeyword(),0,0);
            } else if (criteria.getType() == SearchCriteria.TYPE.MEDIA_QUALITY) {
                list = MusixMateApp.getInstance().getOrmLite().findByMediaQuality(criteria.getKeyword());
            } else if (criteria.getType() == SearchCriteria.TYPE.AUDIO_ENCODINGS && Constants.TITLE_DSD.equals(criteria.getKeyword())) {
                list = MusixMateApp.getInstance().getOrmLite().findDSDSongs(0, 0);
            } else if (criteria.getType() == SearchCriteria.TYPE.AUDIO_ENCODINGS && Constants.TITLE_MASTER_AUDIO.equals(criteria.getKeyword())) {
                list = MusixMateApp.getInstance().getOrmLite().findMQASongs(0, 0);
            } else if (criteria.getType() == SearchCriteria.TYPE.AUDIO_ENCODINGS && Constants.TITLE_HIGH_QUALITY.equals(criteria.getKeyword())) {
                list = MusixMateApp.getInstance().getOrmLite().findHighQuality(0, 0);
            } else if (criteria.getType() == SearchCriteria.TYPE.AUDIO_ENCODINGS && Constants.TITLE_HIFI_LOSSLESS.equals(criteria.getKeyword())) {
                list = MusixMateApp.getInstance().getOrmLite().findLosslessSong(0, 0);
            } else if (criteria.getType() == SearchCriteria.TYPE.AUDIO_ENCODINGS && Constants.TITLE_HIRES.equals(criteria.getKeyword())) {
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

    public static List<MusicTag> getAllMusics() {
        return MusixMateApp.getInstance().getOrmLite().findMySongs();
    }

    public static List<MusicTag> getAllMusicsForPlaylist() {
        OrmLiteHelper.ORDERED_BY [] aristAlbum = {OrmLiteHelper.ORDERED_BY.TITLE, OrmLiteHelper.ORDERED_BY.ARTIST};
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

    public static long getMusicTotal() {
        try {
            return MusixMateApp.getInstance().getOrmLite().getMusicTagDao().countOf();
        } catch (SQLException e) {
            return 0;
        }
    }

    public static List<MusicTag> findByIds(long[] ids) {
        try {
            Dao<MusicTag, Long> musicTagDao = MusixMateApp.getInstance().getOrmLite().getMusicTagDao();
            // OrmLite's 'in' operator needs an array of Objects, not primitives
            Long[] idObjects = new Long[ids.length];
            for (int i = 0; i < ids.length; i++) {
                idObjects[i] = ids[i];
            }

            QueryBuilder<MusicTag, Long> queryBuilder = musicTagDao.queryBuilder();
            queryBuilder.where().in("id", (Object[]) idObjects); // Cast is important
            return queryBuilder.query();
        } catch (SQLException ignored) {

        }
        return Collections.EMPTY_LIST;
    }

    public static MusicTag findById(long id) {
        return  MusixMateApp.getInstance().getOrmLite().findById(id);
    }

    public static List<MusicTag> findRecentlyAdded(long firstResult, long maxResults) {
        return  MusixMateApp.getInstance().getOrmLite().findRecentlyAdded(firstResult, maxResults);
    }

    public static void deleteMediaTag(MusicTag tag) {
        MusixMateApp.getInstance().getOrmLite().delete(tag);
    }

    public static MusicTag getByAlbumCoverUniqueKey(String albumCoverUniqueKey) {
        return MusixMateApp.getInstance().getOrmLite().findByAlbumCoverUniqueKey(albumCoverUniqueKey);
    }
}