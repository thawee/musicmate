package apincer.music.core.repository;

import static apincer.music.core.Constants.ARTIST_SEP;
import static apincer.music.core.repository.FileRepository.getDefaultMusicPaths;
import static apincer.music.core.utils.StringUtils.EMPTY;
import static apincer.music.core.utils.StringUtils.isEmpty;
import static apincer.music.core.utils.StringUtils.trimToEmpty;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;
import androidx.preference.PreferenceManager;

import java.sql.SQLException;
import java.util.ArrayList;
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
import apincer.music.core.Settings;
import apincer.music.core.model.AudioTag;
import apincer.music.core.model.Track;
import apincer.music.core.model.PlaylistEntry;
import apincer.music.core.model.SearchCriteria;
import apincer.music.core.repository.spi.DbHelper;
import apincer.music.core.utils.StringUtils;
import apincer.music.core.utils.TagUtils;
import dagger.hilt.android.qualifiers.ApplicationContext;
import musicmate.core.R;

@Singleton
public class TagRepository {
    private static final String TAG = "TagRepository";
    public static final List<String> LOSSY_AUDIO_FORMATS;
    private static final List<Track> EMPTY_LIST = new ArrayList<>();

    static {
        // Initialize once when the class is loaded
        LOSSY_AUDIO_FORMATS = List.of("MP3", "AAC", "OGG", "WMA"); // Make it unmodifiable
    }

    private final DbHelper dbHelper;
    private final Context context; // Can be injected if needed for resources

    @Inject // Hilt will now know how to create a TagRepository
    public TagRepository(@ApplicationContext Context context, DbHelper dbHepler) {
        this.dbHelper = dbHepler;
        this.context = context;
    }

    private static String escapeString(String text) {
        return text.replace("'","''");
    }

    public static List<String> getDefaultStyleList(Context context) {
        List<String> list = new ArrayList<>();
        String[] arrays =  context.getResources().getStringArray(R.array.styles);
        for(String genre: arrays) {
            if(!list.contains(genre)) {
                list.add(genre);
            }
        }

        Collections.sort(list);
        return list;
    }

    public static List<String> getDefaultMoodList(Context context) {
        List<String> list = new ArrayList<>();
        String[] arrays =  context.getResources().getStringArray(R.array.moods);
        for(String genre: arrays) {
            if(!list.contains(genre)) {
                list.add(genre);
            }
        }

        Collections.sort(list);
        return list;
    }

    public void saveTag(Track tag) {
        String path = StringUtils.trimToEmpty(tag.getPath()).toLowerCase();
        if(StringUtils.isEmpty(tag.getUniqueKey())) {
            String keyword = escapeString(path+"_"+ tag.getAudioStartTime());
            tag.setUniqueKey(keyword);
        }

        if(StringUtils.isEmpty(tag.getGenre())) {
            tag.setGenre(Constants.NONE);
        }
        if(StringUtils.isEmpty(tag.getArtist())) {
            tag.setArtist(Constants.EMPTY);
        }
        if(StringUtils.isEmpty(tag.getAlbumArtist())) {
            tag.setAlbumArtist(tag.getArtist());
        }

        // update id is unique key is valid
        if(tag.getId() == 0) {
            Track existingTag = dbHelper.findByUniqueKey(tag.getUniqueKey());
            if(existingTag != null) {
                tag.setId(existingTag.getId());
                if(tag.getDynamicRange()==0) {
                    // not set, use from existing tag
                    tag.setDynamicRange(existingTag.getDynamicRange());
                    tag.setQualityInd(existingTag.getQualityInd());
                    tag.setMqaSampleRate(existingTag.getMqaSampleRate());
                }
            }
        }
        dbHelper.save(tag);
    }

    public void removeTag(Track tag) {
        dbHelper.delete(tag);
    }

    public boolean isOutdated(Track tag, long lastModified) {
        return dbHelper.isOutdated(tag, lastModified);
    }

    public List<Track> getByPath(String path) {
        return dbHelper.getByPath(path);
    }

    public Track findMusic(String currentTitle, String currentArtist, String currentAlbum) {
        try {
            List<Track> list = findByTitle(trimToEmpty(currentTitle));

            double prvTitleScore = 0.0;
            double prvArtistScore = 0.0;
            double prvAlbumScore = 0.0;
            double titleScore;
            double artistScore;
            double albumScore;
            Track matchedMeta = null;

            for (Track metadata : list) {
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

    public void cleanInvalidTags() {
        try {
            dbHelper.cleanInvalidTag();
        } catch (Exception e) {
            Log.e(TAG, "cleanMusicMate", e);
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
        String[] genresCore =  context.getResources().getStringArray(R.array.genres_core);
        String[] genresThai =  context.getResources().getStringArray(R.array.genres_thai);
        for(String genre: genresCore) {
            if(!list.contains(genre)) {
                list.add(genre);
            }
        }
        for(String genre: genresThai) {
            if(!list.contains(genre)) {
                list.add(genre);
            }
        }

        Collections.sort(list);
        return list;
    }

    public List<String> getArtistList() {
        List<String> allArtistStrings = dbHelper.getArtists();
        // Use a HashSet for efficient de-duplication (fast lookups).
        Set<String> uniqueArtists = new HashSet<>();

        for (String artistField : allArtistStrings) {
            // Still creates temp arrays, but is a vast improvement.
            String[] artists = artistField.split(ARTIST_SEP, -1);
            for (String artist : artists) {
                String trimmedArtist = trimToEmpty(artist);
                if (!trimmedArtist.isEmpty()) {
                    uniqueArtists.add(trimmedArtist);
                }
            }
        }

        // Convert the set to a list and sort it.
        List<String> sortedList = new ArrayList<>(uniqueArtists);
        Collections.sort(sortedList);
        return sortedList;
    }

    @Deprecated
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

    @Deprecated
    public List<Track> findByPath(String path) {
       return dbHelper.findByPath(path);
    }

    public List<Track> findInPath(String path) {
            return dbHelper.findInPath(path);
    }

    public List<Track> findByTitle(String title) {
        return dbHelper.findByTitle(title);
    }

    public List<Track> findMusic(SearchCriteria criteria) {
        if(criteria.getType() == SearchCriteria.TYPE.PLAYLIST) {
            return findPlaylist(criteria);
        }else {
            List<Track> results = findMusicOrEmpty(criteria);
            if (results == null || results.isEmpty()) {
                // try again
                results = findMusicOrEmpty(criteria);
            }
            return results;
        }
    }

    public List<Track> findPlaylist(SearchCriteria criteria) {
        List<Track> results = new ArrayList<>();
        if (criteria.getType() == SearchCriteria.TYPE.PLAYLIST) {
            if (!isEmpty(criteria.getKeyword())) {
                // get all song and filter matched playlist criteria
                List<Track> list = getAllMusicsForPlaylist();
                String name = criteria.getKeyword();
                for (Track tag : list) {
                    if(PlaylistRepository.isSongInPlaylistName(tag, name)) {
                        results.add(tag);
                    }
                }
                List<Track> missing = PlaylistRepository.getMissingSongs(name, results);
                results.addAll(missing);
            } else {
                int index = 1;
                for (PlaylistEntry entry : PlaylistRepository.getPlaylists()) {
                    results.add(buildMusicTagPlaylist(index++, entry));
                }
                results.sort(Comparator.comparing(Track::getTitle));

                // Create a map of playlist containers if not already done
                Map<String, Track> playlistMap = new HashMap<>();
                for (Track item : results) {
                    //if (item instanceof MusicFolder folder) {
                    if(item.isContainer()) {
                        playlistMap.put(item.getTitle(), item);
                    }
                }
                List<Track> list = getAllMusicsForPlaylist(); //dbHelper.findMySongs();
                for(Track tag: list) {

                    for (PlaylistEntry entry : PlaylistRepository.getPlaylists()) {
                        if (PlaylistRepository.isSongInPlaylistName(tag, entry.getName())) {
                            updateMusicFolder(playlistMap,entry.getName(), tag);
                        }
                    }
                }
            }
        }
        return results;
    }

    public static void updateMusicFolder(Map<String, Track> playlistMap, String key, Track tag) {
        if(playlistMap.containsKey(key)) {
            Track folder = playlistMap.get(key);
            if(folder != null) {
                folder.increaseChildCount();
                folder.setFileSize(folder.getFileSize() + tag.getFileSize());
                folder.setAudioDuration(folder.getAudioDuration() + tag.getAudioDuration());
            }
        }
    }

    private static AudioTag buildMusicTagPlaylist(int index, PlaylistEntry entry) {
        AudioTag tag = new AudioTag(SearchCriteria.TYPE.PLAYLIST, entry.getName()); //SearchCriteria.TYPE.PLAYLIST, entry.getName());
        tag.setUniqueKey(entry.getUuid());
        tag.setId(10000+index);
        tag.setDescription(entry.getDescription());
        return tag;
    }

    private List<Track> findMusicOrEmpty(SearchCriteria criteria) {
        try {
            return findByCriteria(criteria);
        }catch (Exception e) {
            // retry one more time
            return EMPTY_LIST;
        }
    }

    private List<Track> findByCriteria(SearchCriteria criteria) {
        List<Track> list = new ArrayList<>();
        if (criteria.isSearchMode()) {
            // search title only, limit 5 songs
            list = dbHelper.findByKeyword(criteria.getSearchText());
        }else if (criteria.getType() == SearchCriteria.TYPE.LIBRARY) {
                if (StringUtils.isEmpty(criteria.getKeyword())) {
                    criteria.setKeyword(Constants.TITLE_ALL_SONGS);
                }

                if(Constants.TITLE_ALL_SONGS.equals(StringUtils.trimToEmpty(criteria.getKeyword()))) {
                    list = dbHelper.findMySongs();
                } else if (Constants.TITLE_INCOMING_SONGS.equals(criteria.getKeyword())) {
                    list = findRecentlyAdded(0, 0);
               // } else if (Constants.TITLE_BROKEN.equals(criteria.getKeyword())) {
                //    list = dbHelper.findMyUnsatisfiedSongs();
                } else if (Constants.TITLE_TO_ANALYST_DR.equals(criteria.getKeyword())) {
                    list = dbHelper.findMyNoDRMeterSongs();
                } else if (Constants.TITLE_DUPLICATE.equals(criteria.getKeyword())) {
                   // list = dbHelper.findDuplicateSong();
                    boolean includeArtist = Settings.isArtistAwareSimilarSongs(context);
                    list = dbHelper.findSimilarSongs(includeArtist);
                } else if (Constants.TITLE_NO_COVERART.equals(criteria.getKeyword())) {
                    list = dbHelper.findNoEmbedCoverArtSong();
                }
            } else if (criteria.getType() == SearchCriteria.TYPE.PUBLISHER) {
                list = dbHelper.findByPublisher(criteria.getKeyword());
            } else if (criteria.getType() == SearchCriteria.TYPE.ARTIST) {
                String keyword = criteria.getKeyword();
                if(isEmpty(keyword)) {
                    list = findArtistItems();
                }else {
                    if (isEmpty(keyword) || StringUtils.EMPTY.equalsIgnoreCase(keyword)) {
                        keyword = "";
                    }
                    list = dbHelper.findByArtist(keyword, 0, 0);
                }
            //} else if (criteria.getType() == SearchCriteria.TYPE.MEDIA_QUALITY) {
            //    list = dbHelper.findByMediaQuality(criteria.getKeyword());
            } else if (criteria.getType() == SearchCriteria.TYPE.CODEC && isEmpty(criteria.getKeyword())) {
                list = findQualityItems();
            } else if (criteria.getType() == SearchCriteria.TYPE.CODEC && Constants.TITLE_DSD.equals(criteria.getKeyword())) {
                list = dbHelper.findDSDSongs(0, 0);
            } else if (criteria.getType() == SearchCriteria.TYPE.CODEC && Constants.TITLE_MQA_MASTER_QUALITY.equals(criteria.getKeyword())) {
                list = dbHelper.findMQASongs(0, 0);
            } else if (criteria.getType() == SearchCriteria.TYPE.CODEC && Constants.TITLE_HIGH_QUALITY.equals(criteria.getKeyword())) {
                list = dbHelper.findHighQuality(0, 0);
            } else if (criteria.getType() == SearchCriteria.TYPE.CODEC && Constants.TITLE_CD_QUALITY.equals(criteria.getKeyword())) {
                list = dbHelper.findCDQuality(0, 0);
            } else if (criteria.getType() == SearchCriteria.TYPE.CODEC && Constants.TITLE_HIRES_QUALITY.equals(criteria.getKeyword())) {
                list = dbHelper.findHiRes(0, 0);
            } else if (criteria.getType() == SearchCriteria.TYPE.CODEC && Constants.TITLE_CD_EXT_QUALITY.equals(criteria.getKeyword())) {
            list = dbHelper.findHiRes48(0, 0);
           /* } else if (criteria.getType() == SearchCriteria.TYPE.GROUPING) {
                String keyword = criteria.getKeyword();
                if(isEmpty(keyword)) {
                    list = findGroupingItems();
                }else {
                    if (isEmpty(keyword) || StringUtils.EMPTY.equalsIgnoreCase(keyword)) {
                        keyword = "";
                    }
                    list = dbHelper.findByGrouping(keyword, 0, 0);
                } */
            } else if (criteria.getType() == SearchCriteria.TYPE.GENRE) {
                String keyword = criteria.getKeyword();
                if(isEmpty(keyword)) {
                    list = findGenreItems();
                }else {
                    if (StringUtils.EMPTY.equalsIgnoreCase(keyword)) {
                        keyword = "";
                    }
                    list = dbHelper.findByGenre(keyword);
                }
          //  } else if (criteria.getType() == SearchCriteria.TYPE.SEARCH) {
                // search title only, limit 5 songs
          //      list = dbHelper.findByKeyword(criteria.getKeyword());
         //   } else {
                // default for MY_SONGS and others
          //      list = dbHelper.findMySongs();
            }
        return list;
    }

    private List<Track> findQualityItems() {
        Map<String, AudioTag> mapped = new HashMap<>();
        List<Track> list = getAllMusics();
        for(Track song: list) {
            String codec = Constants.TITLE_CD_QUALITY;
            String codecAlt = null;
            if(TagUtils.isDSD(song)) {
                codec = Constants.TITLE_DSD;
            }else if(TagUtils.isMQA(song)) {
                codec = Constants.TITLE_MQA_MASTER_QUALITY;
            }else if(TagUtils.isHiRes48(song)) {
                codec = Constants.TITLE_CD_EXT_QUALITY;
            }else if(TagUtils.isHiRes(song)) {
                codec = Constants.TITLE_HIRES_QUALITY;
            }else if(TagUtils.isLossy(song)) {
                codec = Constants.TITLE_HIGH_QUALITY;
            }

            AudioTag folder = mapped.getOrDefault(codec, new AudioTag(SearchCriteria.TYPE.CODEC, codec));
            if(folder != null) {
                if (folder.getChildCount() == 0) {
                    //first time created
                    folder.setDescription(TagUtils.getQualityNote(codec));
                }
                folder.increaseChildCount();
                folder.setAudioDuration(folder.getAudioDuration() + song.getAudioDuration());
                mapped.put(codec, folder);
            }

            if(codec.equalsIgnoreCase(Constants.TITLE_MQA_MASTER_QUALITY)) {
                if (TagUtils.isHiRes48(song)) {
                    codecAlt = Constants.TITLE_CD_EXT_QUALITY;
                } else if (TagUtils.isCDQuality(song)) {
                    codecAlt = Constants.TITLE_CD_QUALITY;
                }

                if (codecAlt != null) {
                    folder = mapped.getOrDefault(codecAlt, new AudioTag(SearchCriteria.TYPE.CODEC, codecAlt));
                    if(folder != null) {
                        if (folder.getChildCount() == 0) {
                            //first time created
                            folder.setDescription(TagUtils.getQualityNote(codec));
                        }
                        folder.increaseChildCount();
                        folder.setAudioDuration(folder.getAudioDuration() + song.getAudioDuration());
                        mapped.put(codecAlt, folder);
                    }
                }
            }
        }

        // This is the line you already have
        List<Track> folderList = new ArrayList<>(mapped.values());
        final Map<String, Integer> customOrder = Map.of(
                Constants.TITLE_HIGH_QUALITY, 5,
                Constants.TITLE_CD_QUALITY, 4,
                Constants.TITLE_CD_EXT_QUALITY, 3,
                Constants.TITLE_HIRES_QUALITY, 2,
                Constants.TITLE_MQA_MASTER_QUALITY, 1,
                Constants.TITLE_DSD, 0
        );
        // This new line sorts the list in-place alphabetically by title
        //folderList.sort(Comparator.comparing(MusicTag::getTitle));

        //todo: add notes to every items


        final int defaultPriority = Integer.MAX_VALUE;

        // Sort the list using the custom order
        folderList.sort(Comparator.comparingInt(tag ->
                // Get the priority from the map, or use the default
                customOrder.getOrDefault(tag.getTitle(), defaultPriority)
        ));

        return folderList;
    }

    private List<Track> findGenreItems() {
       // List<MusicTag> genres = new ArrayList<>();
        Map<String, AudioTag> mapped = new HashMap<>();
        List<Track> list = getAllMusics();
        for(Track song: list) {
            String genre = song.getGenre();
            if(isEmpty(genre)) {
                genre = EMPTY;
            }
            AudioTag folder = mapped.getOrDefault(genre, new AudioTag(SearchCriteria.TYPE.GENRE, genre));
            if(folder != null) {
                folder.increaseChildCount();
                folder.setAudioDuration(folder.getAudioDuration() + song.getAudioDuration());
                mapped.put(genre, folder);
            }
        }

        // This is the line you already have
        List<Track> folderList = new ArrayList<>(mapped.values());

        // This new line sorts the list in-place alphabetically by title
        folderList.sort(Comparator.comparing(Track::getTitle));

        return folderList;
    }

    private List<Track> findArtistItems() {
       // List<MusicTag> artists = new ArrayList<>();
        Map<String, AudioTag> mapped = new HashMap<>();
        List<Track> list = getAllMusics();

        for (Track song : list) {
            String artistString = song.getArtist();

            if (isEmpty(artistString)) {
                // Handle songs with no artist tag, group them under EMPTY
                String artistName = EMPTY;
                AudioTag folder = mapped.getOrDefault(artistName, new AudioTag(SearchCriteria.TYPE.ARTIST, artistName));
                if(folder != null) {
                    folder.increaseChildCount();
                    folder.setAudioDuration(folder.getAudioDuration() + song.getAudioDuration());
                    mapped.put(artistName, folder);
                }
            } else {
                // Split the artist string by either comma or semicolon
                String[] individualArtists = artistString.split("[;,]");

                // Loop through each individual artist name
                for (String artistName : individualArtists) {
                    String trimmedName = artistName.trim();

                    // Only process non-empty names after trimming
                    if (!isEmpty(trimmedName)) {
                        AudioTag folder = mapped.getOrDefault(trimmedName, new AudioTag(SearchCriteria.TYPE.ARTIST, trimmedName));
                        if(folder != null) {
                            folder.increaseChildCount();
                            // Add the song's duration to *each* artist it belongs to
                            folder.setAudioDuration(folder.getAudioDuration() + song.getAudioDuration());
                            mapped.put(trimmedName, folder);
                        }
                    }
                }
            }
        }

        // This is the line you already have
        List<Track> folderList = new ArrayList<>(mapped.values());

        // This new line sorts the list in-place alphabetically by title
        folderList.sort(Comparator.comparing(Track::getTitle));

        return folderList;
    }

    public List<Track> getAllMusics() {
        return dbHelper.findMySongs();
    }

    public List<Track> getAllMusicsForPlaylist() {
       return dbHelper.findForPlaylist();
    }

    public List<String> getDefaultPublisherList(Context context) {
        List<String> list = new ArrayList<>();
        String[] genres =  context.getResources().getStringArray(R.array.default_publisher);
        for(String genre: genres) {
            if(!list.contains(genre)) {
                list.add(genre);
            }
        }

        Collections.sort(list);
        return list;
    }

    @Deprecated
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

    public void load(Track tag) {
        Track newTag = dbHelper.findById(tag.getId());
        if(newTag !=null) {
            tag.copy(newTag);
        }
    }

    @Deprecated
    public List<Track> getMusicTags(Context context, String grouping, String artist) {
        return dbHelper.findByGroupingAndArtist(grouping, artist);
    }

    @Deprecated
    public List<Track> getMusicTags(long idRange1, long idRange2) {
        return dbHelper.findByIdRanges(idRange1, idRange2);
    }

    public Collection<Track> getRootDIRs() {
        List<AudioTag> list = new ArrayList<>();
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
                AudioTag dir = new AudioTag(SearchCriteria.TYPE.LIBRARY, name);
                    dir.setUniqueKey(musicDir);
                    dir.setChildCount(0);
                    list.add(dir);
        }
        return new ArrayList<>(list);
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

    public long getTotalSongs() {
        try {
            return dbHelper.getTotalSongs();
        } catch (Exception e) {
            return 0;
        }
    }

    public long getTotalDuration() {
        try {
            return dbHelper.getTotalDuration();
        } catch (SQLException e) {
            return 0;
        }
    }

    public List<Track> findByIds(long[] ids) {
        return dbHelper.findByIds(ids);
    }

    public Track findById(long id) {
        return  dbHelper.findById(id);
    }

    public List<Track> findRecentlyAdded(long firstResult, long maxResults) {
        return  dbHelper.findRecentlyAdded(firstResult, maxResults);
    }

    public void deleteMediaTag(Track tag) {
        dbHelper.delete(tag);
    }

    public Track getByAlbumArtFilename(String albumArtFilename) {
        return dbHelper.findByAlbumArtFilename(albumArtFilename);
    }

    public List<Track> findMyNoDRMeterSongs() {
        return dbHelper.findMyNoDRMeterSongs();
    }

    public List<Track> findByAlbumAndAlbumArtist(String album, String albumArtist, long firstResult, long maxResults) {
        return dbHelper.findByAlbumAndAlbumArtist(album, albumArtist, firstResult, maxResults);
    }

    public List<Track> findMySongs() {
        return dbHelper.findMySongs();
    }

    @Deprecated
    public List<Track> findByGrouping(String grouping, long firstResult, long maxResults) {
        return dbHelper.findByGrouping(grouping, firstResult, maxResults);
    }

    public List<Track> findByArtist(String name, long firstResult, long maxResults) {
        return dbHelper.findByArtist(name, firstResult, maxResults);
    }

    public List<Track> findByGenre(String name) {
        return dbHelper.findByGenre(name);
    }

    public List<Track> findByGenre(String name, long firstResult, long maxResults) {
        return dbHelper.findByGenre(name, firstResult, maxResults);
    }

    public List<Track> getAlbumAndArtistWithChildrenCount() {
        return  dbHelper.getAlbumAndArtistWithChildrenCount();
    }

    public List<String> getGenres() {
        return dbHelper.getGenres();
    }

    public List<Track> getGenresWithChildrenCount() {
        return  dbHelper.getGenresWithChildrenCount();
    }

    @Deprecated
    public List<Track> findMQASongs(long firstResult, long maxResults) {
        return dbHelper.findMQASongs(firstResult, maxResults);
    }

    @Deprecated
    public List<Track> findHiRes(long firstResult, long maxResults) {
        return dbHelper.findHiRes(firstResult, maxResults);
    }

    @Deprecated
    public List<Track> findCDQuality(long firstResult, long maxResults) {
        return dbHelper.findCDQuality(firstResult, maxResults);
    }

    @Deprecated
    public List<Track> findHighQuality(long firstResult, long maxResults) {
        return dbHelper.findHighQuality(firstResult, maxResults);
    }

    @Deprecated
    public List<Track> findDSDSongs(long firstResult, long maxResults) {
        return dbHelper.findDSDSongs(firstResult, maxResults);
    }

    public List<String> getArtists() {
        return dbHelper.getArtists();
    }

    public List<Track> getArtistWithChildrenCount() {
        return dbHelper.getArtistWithChildrenCount();
    }

    public void purgeDatabase() {
        try {
            dbHelper.purgeDatabase();
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public DbHelper getDbHelper() {
        return dbHelper;
    }
}