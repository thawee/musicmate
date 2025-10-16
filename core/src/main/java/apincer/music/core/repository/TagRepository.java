package apincer.music.core.repository;

import static apincer.music.core.Constants.ARTIST_SEP;
import static apincer.music.core.repository.FileRepository.getDefaultMusicPaths;
import static apincer.music.core.utils.StringUtils.isEmpty;
import static apincer.music.core.utils.StringUtils.trimToEmpty;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;
import androidx.preference.PreferenceManager;

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
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.inject.Inject;
import javax.inject.Singleton;

import apincer.music.core.Constants;
import apincer.music.core.database.MusicTag;
import apincer.music.core.database.QueueItem;
import apincer.music.core.model.MusicFolder;
import apincer.music.core.model.SearchCriteria;
import apincer.music.core.utils.StringUtils;
import apincer.android.utils.FileUtils;
import dagger.hilt.android.qualifiers.ApplicationContext;
import musicmate.core.R;

@Singleton
public class TagRepository {
    private static final String TAG = "TagRepository";
    public static final List<String> LOSSY_AUDIO_FORMATS;
    static {
        // Initialize once when the class is loaded
        List<String> formats = new ArrayList<>();
        formats.add("MP3");
        formats.add("AAC");
        formats.add("OGG");
        formats.add("WMA");
        LOSSY_AUDIO_FORMATS = Collections.unmodifiableList(formats); // Make it unmodifiable
    }

    private final OrmLiteHelper dbHelper;
    private final Context context; // Can be injected if needed for resources

    @Inject // Hilt will now know how to create a TagRepository
    public TagRepository(@ApplicationContext Context context) {
        this.dbHelper = new OrmLiteHelper(context);
        this.context = context;
    }

    private static String escapeString(String text) {
        return text.replace("'","''");
    }

    public void saveTag(MusicTag tag) {
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
            MusicTag existingTag = dbHelper.findByUniqueKey(tag.getUniqueKey());
            if(existingTag != null) {
                tag.setId(existingTag.getId());
                if(tag.getDynamicRange()==0) {
                    // not set, use from existing tag
                    tag.setDynamicRange(existingTag.getDynamicRange());
                    tag.setQualityInd(existingTag.getQualityInd());
                    tag.setMqaSampleRate(existingTag.getMqaSampleRate());
                    tag.setQualityRating(existingTag.getQualityRating());
                   // tag.setWaveformData(existingTag.getWaveformData());
                }
            }
        }
        dbHelper.save(tag);
    }

    public void removeTag(MusicTag tag) {
        dbHelper.delete(tag);
    }

    public boolean isOutdated(MusicTag tag, long lastModified) {
        return dbHelper.isOutdated(tag, lastModified);
    }

    public List<MusicTag> getByPath(String path) {
        return dbHelper.getByPath(path);
    }

    public MusicTag findMediaItem(String currentTitle, String currentArtist, String currentAlbum) {
        try {
            List<MusicTag> list = findMediaByTitle(currentTitle);

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

    public void cleanMusicMate() {
        try {
            List<MusicTag> list =  dbHelper.findMySongs();
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

    public List<String> getActualGenreList() {
        List<String> list = new ArrayList<>();

        List<String> names = dbHelper.getGenres();
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

    public List<String> getActualGroupingList(Context context) {
        List<String> list = new ArrayList<>();
        List<String> names = dbHelper.getGrouping();
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

    public List<String> getGroupingList(Context context) {
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

    public List<String> getArtistList() {
        List<String> list = dbHelper.getArtists();
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

    public List<MusicTag> findByPath(String path) {
       return dbHelper.findByPath(path);
    }

    public List<MusicTag> findInPath(String path) {
            return dbHelper.findInPath(path);
    }

    public List<MusicTag> findMediaByTitle(String title) {
        return dbHelper.findByTitle(title);
    }

    public List<MusicTag> findMediaTag(SearchCriteria criteria) {
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

    public List<MusicTag> findMediaCollection(SearchCriteria criteria) {
        List<MusicTag> results = new ArrayList<>();
        if (criteria.getType() == SearchCriteria.TYPE.PLAYLIST) {
            if (criteria.getKeyword() != null) {
                List<MusicTag> list = getAllMusicsForPlaylist(); //dbHelper.findMySongs();
                String name = criteria.getKeyword();
                for (MusicTag tag : list) {
                    if(PlaylistRepository.isSongInPlaylistName(tag, name)) {
                        results.add(tag);
                    }
                }
                /*
                if(PlaylistRepository.isTitlePlaylist(name)) {
                    // add missing titles
                    List<MusicTag> missingList = PlaylistRepository.getMissingSongsForPlaylist(name, results);
                    missingList.sort(Comparator.comparing(MusicTag::getTitle));
                    results.addAll(missingList);
                } */
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
                List<MusicTag> list = getAllMusicsForPlaylist(); //dbHelper.findMySongs();
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
            folder.increaseChildCount();
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

    private List<MusicTag> findMediaTagOrEmpty(SearchCriteria criteria) {
        try {
            return findMediaTagByCriteria(criteria);
        }catch (Exception e) {
            // retry one more time
            return OrmLiteHelper.EMPTY_LIST;
        }
    }

    private List<MusicTag> findMediaTagByCriteria(SearchCriteria criteria) {
        List<MusicTag> list = new ArrayList<>();
        if (criteria.isSearchMode()) {
            // search title only, limit 5 songs
            list = dbHelper.findByKeyword(criteria.getSearchText());
        }else if (criteria.getType() == SearchCriteria.TYPE.LIBRARY) {
                if (StringUtils.isEmpty(criteria.getKeyword()) || Constants.TITLE_ALL_SONGS.equals(StringUtils.trimToEmpty(criteria.getKeyword()))) {
                    list = dbHelper.findMySongs();
                } else if (Constants.TITLE_INCOMING_SONGS.equals(criteria.getKeyword())) {
                    list = findRecentlyAdded(0, 0);
               // } else if (Constants.TITLE_BROKEN.equals(criteria.getKeyword())) {
                //    list = dbHelper.findMyUnsatisfiedSongs();
                } else if (Constants.TITLE_TO_ANALYST_DR.equals(criteria.getKeyword())) {
                    list = dbHelper.findMyNoDRMeterSongs();
                } else if (Constants.TITLE_DUPLICATE.equals(criteria.getKeyword())) {
                   // list = dbHelper.findDuplicateSong();
                    list = dbHelper.findSimilarSongs(true);
                } else if (Constants.TITLE_NO_COVERART.equals(criteria.getKeyword())) {
                    list = dbHelper.findNoEmbedCoverArtSong();
                }
            } else if (criteria.getType() == SearchCriteria.TYPE.PUBLISHER) {
                list = dbHelper.findByPublisher(criteria.getKeyword());
            } else if (criteria.getType() == SearchCriteria.TYPE.ARTIST) {
                if(criteria.getKeyword()==null) {
                    list = findArtistItems();
                }else {
                    list = dbHelper.findByArtist(criteria.getKeyword(), 0, 0);
                }
            } else if (criteria.getType() == SearchCriteria.TYPE.MEDIA_QUALITY) {
                list = dbHelper.findByMediaQuality(criteria.getKeyword());
            } else if (criteria.getType() == SearchCriteria.TYPE.AUDIO_ENCODINGS && Constants.TITLE_DSD.equals(criteria.getKeyword())) {
                list = dbHelper.findDSDSongs(0, 0);
            } else if (criteria.getType() == SearchCriteria.TYPE.AUDIO_ENCODINGS && Constants.TITLE_MASTER_AUDIO.equals(criteria.getKeyword())) {
                list = dbHelper.findMQASongs(0, 0);
            } else if (criteria.getType() == SearchCriteria.TYPE.AUDIO_ENCODINGS && Constants.TITLE_HIGH_QUALITY.equals(criteria.getKeyword())) {
                list = dbHelper.findHighQuality(0, 0);
            } else if (criteria.getType() == SearchCriteria.TYPE.AUDIO_ENCODINGS && Constants.TITLE_HIFI_LOSSLESS.equals(criteria.getKeyword())) {
                list = dbHelper.findLosslessSong(0, 0);
            } else if (criteria.getType() == SearchCriteria.TYPE.AUDIO_ENCODINGS && Constants.TITLE_HIRES.equals(criteria.getKeyword())) {
                list = dbHelper.findHiRes(0, 0);
            } else if (criteria.getType() == SearchCriteria.TYPE.GROUPING) {
                String val = criteria.getKeyword();
                if (isEmpty(val) || StringUtils.EMPTY.equalsIgnoreCase(val)) {
                    val = "";
                }
                list = dbHelper.findByGrouping(val, 0, 0);
            } else if (criteria.getType() == SearchCriteria.TYPE.GENRE) {

                String val = criteria.getKeyword();
                if (isEmpty(val) || StringUtils.EMPTY.equalsIgnoreCase(val)) {
                    val = "";
                }
                list = dbHelper.findByGenre(val);
          //  } else if (criteria.getType() == SearchCriteria.TYPE.SEARCH) {
                // search title only, limit 5 songs
          //      list = dbHelper.findByKeyword(criteria.getKeyword());
            } else {
                // default for MY_SONGS and others
                list = dbHelper.findMySongs();
            }
        return list;
    }

    private List<MusicTag> findArtistItems() {
        List<MusicTag> artists = new ArrayList<>();
        Map<String, MusicFolder> mapped = new HashMap<>();
        List<MusicTag> list = getAllMusics();
        for(MusicTag song: list) {
            String artist = song.getArtist();
            if(mapped.containsKey(artist)) {
                mapped.get(artist).increaseChildCount();
            }else {
                MusicFolder folder = new MusicFolder(SearchCriteria.TYPE.ARTIST.name(), artist);
                folder.increaseChildCount();
                mapped.put(artist, folder);
                artists.add(folder);
            }
        }
        return artists;
    }

    public List<MusicTag> getAllMusics() {
        return dbHelper.findMySongs();
    }

    public List<MusicTag> getAllMusicsForPlaylist() {
        OrmLiteHelper.ORDERED_BY [] aristAlbum = {OrmLiteHelper.ORDERED_BY.TITLE, OrmLiteHelper.ORDERED_BY.ARTIST};
        return dbHelper.findMySongs(aristAlbum);
    }

    public List<String> getDefaultPublisherList(Context context) {
        List<String> list = new ArrayList<>();
        List<String> names = dbHelper.getPublishers();
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

    public List<String> getPublisherList(Context context) {
        List<String> list = new ArrayList<>();
        List<String> names = dbHelper.getPublishers();
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

    public void load(MusicTag tag) {
        MusicTag newTag = dbHelper.findById(tag.getId());
        if(newTag !=null) {
            tag.cloneFrom(newTag);
        }
    }

    @Deprecated
    public List<MusicTag> getMusicTags(Context context, String grouping, String artist) {
        return dbHelper.findByGroupingAndArtist(grouping, artist);
    }

    @Deprecated
    public List<MusicTag> getMusicTags(long idRange1, long idRange2) {
        return dbHelper.findByIdRanges(idRange1, idRange2);
    }

    public Collection<MusicFolder> getRootDIRs() {
        List<MusicFolder> list = new ArrayList<>();
        List<String> musicDirs  = getDirectories(context);
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

    public static List<String> getDirectories(Context context) {
        // The default preference file name is constructed like this
        SharedPreferences prefs =
                PreferenceManager.getDefaultSharedPreferences(context);

        List<String> defaultDirs = getDefaultMusicPaths(context);
        Set<String> defaultDirsSet = new HashSet<>(defaultDirs);
        Set<String> dirs = prefs.getStringSet(Constants.PREF_MUSICMATE_DIRECTORIES, defaultDirsSet);
        return new ArrayList<>(dirs);
    }

    public long getMusicTotal() {
        try {
            return dbHelper.getMusicTagDao().countOf();
        } catch (SQLException e) {
            return 0;
        }
    }

    public List<MusicTag> findByIds(long[] ids) {
        try {
            Dao<MusicTag, Long> musicTagDao = dbHelper.getMusicTagDao();
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

    public MusicTag findById(long id) {
        return  dbHelper.findById(id);
    }

    public List<MusicTag> findRecentlyAdded(long firstResult, long maxResults) {
        return  dbHelper.findRecentlyAdded(firstResult, maxResults);
    }

    public void deleteMediaTag(MusicTag tag) {
        dbHelper.delete(tag);
    }

    public MusicTag getByAlbumCoverUniqueKey(String albumCoverUniqueKey) {
        return dbHelper.findByAlbumCoverUniqueKey(albumCoverUniqueKey);
    }

    public Dao<QueueItem, Long> getQueueItemDao() throws SQLException {
        return dbHelper.getQueueItemDao();
    }

    public OrmLiteHelper getOrmLiteHelper() {
        return dbHelper;
    }

    public List<MusicTag> findMyNoDRMeterSongs() {
        return dbHelper.findMyNoDRMeterSongs();
    }

    public void addToQueue(MusicTag song) {
        try {
            Dao<QueueItem, Long> queueDao = dbHelper.getQueueItemDao();
            // Get the current size of the queue to determine the next position.
            // If the queue has 5 items (positions 0-4), countOf() returns 5, which is the correct next position.
            long nextPosition = queueDao.countOf();
            QueueItem qItem = new QueueItem(song, nextPosition);
            queueDao.create(qItem);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public List<QueueItem> getQueueItems() {
        try {
            return dbHelper.getQueueItemDao().queryForAll();
        } catch (SQLException ignored) { }

        return Collections.EMPTY_LIST;
    }

    public List<MusicTag> findByAlbumAndAlbumArtist(String album, String albumArtist, long firstResult, long maxResults) {
        return dbHelper.findByAlbumAndAlbumArtist(album, albumArtist, firstResult, maxResults);
    }

    public List<MusicTag> findMySongs() {
        return dbHelper.findMySongs();
    }

    public List<MusicTag> findByGrouping(String grouping, long firstResult, long maxResults) {
        return dbHelper.findByGrouping(grouping, firstResult, maxResults);
    }

    public List<MusicTag> findByArtist(String name, long firstResult, long maxResults) {
        return dbHelper.findByArtist(name, firstResult, maxResults);
    }

    public List<MusicTag> findByGenre(String name) {
        return dbHelper.findByGenre(name);
    }

    public List<MusicTag> findByGenre(String name, long firstResult, long maxResults) {
        return dbHelper.findByGenre(name, firstResult, maxResults);
    }

    public List<MusicFolder> getAlbumAndArtistWithChildrenCount() {
        return  dbHelper.getAlbumAndArtistWithChildrenCount();
    }

    public List<String> getGenres() {
        return dbHelper.getGenres();
    }

    public List<MusicFolder> getGenresWithChildrenCount() {
        return  dbHelper.getGenresWithChildrenCount();
    }

    public List<MusicTag> findMQASongs(long firstResult, long maxResults) {
        return dbHelper.findMQASongs(firstResult, maxResults);
    }

    public List<MusicTag> findHiRes(long firstResult, long maxResults) {
        return dbHelper.findHiRes(firstResult, maxResults);
    }

    public List<MusicTag> findLosslessSong(long firstResult, long maxResults) {
        return dbHelper.findLosslessSong(firstResult, maxResults);
    }

    public List<MusicTag> findHighQuality(long firstResult, long maxResults) {
        return dbHelper.findHighQuality(firstResult, maxResults);
    }

    public List<MusicTag> findDSDSongs(long firstResult, long maxResults) {
        return dbHelper.findDSDSongs(firstResult, maxResults);
    }

    public List<String> getArtists() {
        return dbHelper.getArtists();
    }

    public List<MusicFolder> getArtistWithChildrenCount() {
        return dbHelper.getArtistWithChildrenCount();
    }

    public List<String> getGrouping() {
        return dbHelper.getGrouping();
    }

    public List<MusicFolder> getGroupingWithChildrenCount() {
        return dbHelper.getGroupingWithChildrenCount();
    }
}