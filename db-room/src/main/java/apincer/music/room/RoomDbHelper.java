package apincer.music.room;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import android.content.Context;
import android.util.Log;

import androidx.sqlite.db.SimpleSQLiteQuery;

import apincer.music.core.model.AudioTag;
import apincer.music.core.model.SearchCriteria;
import apincer.music.core.model.SearchResultStats;
import apincer.music.core.model.Track;
import apincer.music.core.Constants;
import apincer.music.core.utils.StringUtils;
import apincer.music.core.repository.spi.DbHelper;
import apincer.music.core.repository.spi.TrackProcessor;
import apincer.music.room.dao.AlbumStats;
import apincer.music.room.dao.ArtistStats;
import apincer.music.room.dao.GenreStats;
import apincer.music.room.dao.SearchStats;
import apincer.music.room.dao.TrackDao;
import apincer.music.room.entity.TrackEntity;

public class RoomDbHelper implements DbHelper {

    // Same separator as OrmLite (Constants.ARTIST_SEP = ",")
    private static final Pattern ARTIST_SPLIT = Pattern.compile(",");

    private final Context context;
    private final MusicRoomDatabase database;
    private final TrackDao trackDao;

    public RoomDbHelper(MusicRoomDatabase database) {
        this(database, null);
    }

    public RoomDbHelper(MusicRoomDatabase database, Context context) {
        this.database = database;
        this.context = context != null ? context.getApplicationContext() : null;
        this.trackDao = database.trackDao();
        loadQueueFromDisk();
    }

    @Override
    public SearchResultStats getSearchStats(SearchCriteria criteria) {
        if (criteria == null) return new SearchResultStats(0, 0, 0.0);
        try {
            String[] whereAndArgs = buildWhereClause(criteria);
            String where = whereAndArgs[0];
            Object[] args = java.util.Arrays.copyOfRange(whereAndArgs, 1, whereAndArgs.length);

            String sql;
            boolean dedup = needsGroupDedup(criteria);
            if (dedup) {
                String inner = "SELECT fileSize, audioDuration FROM musictag"
                        + (where.isEmpty() ? "" : " WHERE " + where)
                        + " GROUP BY title, artist";
                sql = "SELECT COUNT(*) as cnt, SUM(fileSize) as totalSize, SUM(audioDuration) as totalDuration FROM (" + inner + ")";
            } else {
                sql = "SELECT COUNT(*) as cnt, SUM(fileSize) as totalSize, SUM(audioDuration) as totalDuration FROM musictag"
                        + (where.isEmpty() ? "" : " WHERE " + where);
            }
            SearchStats s = trackDao.getStatsByRawQuery(new SimpleSQLiteQuery(sql, args));
            if (s == null) return new SearchResultStats(0, 0, 0.0);
            return new SearchResultStats((int) s.cnt, s.totalSize, s.totalDuration);
        } catch (Exception e) {
            Log.e("RoomDbHelper", "getSearchStats error: " + e.getMessage(), e);
            return new SearchResultStats(0, 0, 0.0);
        }
    }

    @Override
    public SearchResultStats getSimilarSongsStats(boolean artistAware) {
        try {
            String sql;
            if (artistAware) {
                sql = "SELECT COUNT(*) as cnt, SUM(fileSize) as totalSize, SUM(audioDuration) as totalDuration FROM musictag "
                    + "WHERE normalizedTitle IN (SELECT normalizedTitle FROM musictag "
                    + "GROUP BY normalizedTitle, normalizedArtist HAVING COUNT(*) > 1)";
            } else {
                sql = "SELECT COUNT(*) as cnt, SUM(fileSize) as totalSize, SUM(audioDuration) as totalDuration FROM musictag "
                    + "WHERE normalizedTitle IN (SELECT normalizedTitle FROM musictag "
                    + "GROUP BY normalizedTitle HAVING COUNT(*) > 1)";
            }
            SearchStats s = trackDao.getStatsByRawQuery(new SimpleSQLiteQuery(sql));
            if (s == null) return new SearchResultStats(0, 0, 0.0);
            return new SearchResultStats((int) s.cnt, s.totalSize, s.totalDuration);
        } catch (Exception e) {
            Log.e("RoomDbHelper", "getSimilarSongsStats error: " + e.getMessage(), e);
            return new SearchResultStats(0, 0, 0.0);
        }
    }

    @Override
    public Track findByUniqueKey(String uniqueKey) {
        return trackDao.findByUniqueKey(uniqueKey);
    }

    @Override
    public void purgeDatabase() throws SQLException {
        trackDao.purgeDatabase();
    }

    @Override
    public void processAllMusics(TrackProcessor processor) {
        List<TrackEntity> tracks = trackDao.getAllTracks();
        for (TrackEntity track : tracks) {
            processor.process(track);
        }
    }

    @Override
    public List<Track> findMySongs() {
        return new ArrayList<>(trackDao.findMySongs());
    }

    @Override
    public List<Track> findMySongs(long firstResult, long maxResults) {
        return new ArrayList<>(trackDao.findMySongs(firstResult, maxResults <= 0 ? -1 : maxResults));
    }

    @Override
    public List<Track> findByTitle(String title) {
        return new ArrayList<>(trackDao.findByTitle(title));
    }

    @Override
    public List<Track> findByPath(String path) {
        return new ArrayList<>(trackDao.findByPath(path));
    }

    @Override
    public List<Track> findInPath(String path) {
        return new ArrayList<>(trackDao.findInPath(path));
    }

    @Override
    public void save(Track tag) {
        if (tag instanceof TrackEntity trackEntity) {
            trackDao.insert(trackEntity);
        } else if (tag != null) {
            TrackEntity entity = toEntity(tag);
            trackDao.insert(entity);
        }
    }

    @Override
    public List<Track> getByPath(String path) {
        return new ArrayList<>(trackDao.getByPath(path));
    }

    @Override
    public boolean isOutdated(Track tag, long lastModified) {
        if (tag == null) return true;
        Track existing = findByUniqueKey(tag.getUniqueKey());
        if (existing == null) return true;
        return existing.getFileLastModified() < lastModified;
    }

    @Override
    public void saveTagsBatch(List<Track> tags) {
        if (tags == null || tags.isEmpty()) return;
        List<TrackEntity> entities = new ArrayList<>();
        for (Track tag : tags) {
            if (tag instanceof TrackEntity entity) {
                entities.add(entity);
            } else {
                entities.add(toEntity(tag));
            }
        }
        trackDao.insertAll(entities);
    }

    @Override
    public void delete(Track tag) {
        if (tag instanceof TrackEntity trackEntity) {
            trackDao.delete(trackEntity);
        } else if (tag != null) {
            TrackEntity existing = trackDao.findByUniqueKey(tag.getUniqueKey());
            if (existing != null) {
                trackDao.delete(existing);
            }
        }
    }

    @Override
    public List<Track> findRecentlyAdded(long firstResult, long maxResults) {
        long limit = maxResults <= 0 ? -1 : maxResults;
        return new ArrayList<>(trackDao.findRecentlyAdded(firstResult, limit));
    }

    @Override
    public List<Track> findMyNoDRMeterSongs() {
        return new ArrayList<>(trackDao.findMyNoDRMeterSongs());
    }

    @Override
    public List<Track> findByGenre(String genre) {
        return new ArrayList<>(trackDao.findByGenre(genre));
    }

    @Override
    public List<Track> findByGenre(String genre, long firstResult, long maxResults) {
        return new ArrayList<>(trackDao.findByGenre(genre, firstResult, maxResults <= 0 ? -1 : maxResults));
    }

    @Override
    public List<Track> findByGrouping(String grouping, long firstResult, long maxResults) {
        return new ArrayList<>(trackDao.findByGrouping(grouping, firstResult, maxResults <= 0 ? -1 : maxResults));
    }

    @Override
    public List<Track> findHiRes(long firstResult, long maxResults) {
        return new ArrayList<>(trackDao.findHiRes(firstResult, maxResults <= 0 ? -1 : maxResults));
    }

    @Override
    public List<Track> findHiRes48(long firstResult, long maxResults) {
        return new ArrayList<>(trackDao.findHiRes48(firstResult, maxResults <= 0 ? -1 : maxResults));
    }

    @Override
    public List<Track> findHighQuality(long firstResult, long maxResults) {
        return new ArrayList<>(trackDao.findHighQuality(firstResult, maxResults <= 0 ? -1 : maxResults));
    }

    @Override
    public List<Track> findMQASongs(long firstResult, long maxResults) {
        return new ArrayList<>(trackDao.findMQASongs(firstResult, maxResults <= 0 ? -1 : maxResults));
    }

    @Override
    public List<Track> findDSDSongs(long firstResult, long maxResults) {
        return new ArrayList<>(trackDao.findDSDSongs(firstResult, maxResults <= 0 ? -1 : maxResults));
    }

    @Override
    public List<Track> findByMediaQuality(String keyword) {
        return Collections.emptyList();
    }

    @Override
    public List<Track> findByPublisher(String keyword) {
        return new ArrayList<>(trackDao.findByPublisher(keyword));
    }

    @Override
    public List<Track> findCDQuality(long firstResult, long maxResults) {
        return new ArrayList<>(trackDao.findCDQuality(firstResult, maxResults <= 0 ? -1 : maxResults));
    }

    @Override
    public List<Track> findByKeyword(String keyword) {
        return new ArrayList<>(trackDao.findByKeyword(keyword));
    }

    @Override
    public List<Track> findByKeyword(String keyword, long firstResult, long maxResults) {
        return new ArrayList<>(trackDao.findByKeyword(keyword, firstResult, maxResults <= 0 ? -1 : maxResults));
    }

    @Override
    public List<Track> findSimilarSongs(boolean artistAware) {
        if (artistAware) {
            return new ArrayList<>(trackDao.findSimilarByTitleAndArtist());
        }
        return new ArrayList<>(trackDao.findSimilarByTitle());
    }

    @Override
    public List<Track> findSimilarSongs(boolean artistAware, long firstResult, long maxResults) {
        long limit = maxResults <= 0 ? -1 : maxResults;
        if (artistAware) {
            return new ArrayList<>(trackDao.findSimilarByTitleAndArtist(firstResult, limit));
        }
        return new ArrayList<>(trackDao.findSimilarByTitle(firstResult, limit));
    }

    @Override
    public List<String> getGenres() {
        return trackDao.getGenres();
    }

    @Override
    public List<Track> getGenresWithChildrenCount() {
        List<Track> list = new ArrayList<>();
        for (GenreStats s : trackDao.getGenreStats()) {
            AudioTag item = new AudioTag(SearchCriteria.TYPE.GENRE, s.genre != null ? s.genre : "");
            item.setChildCount(s.cnt);
            item.setAudioDuration(s.dur);
            list.add(item);
        }
        return list;
    }

    @Override
    public List<String> getPublishers() {
        return trackDao.getPublishers();
    }

    @Override
    public List<String> getArtists() {
        return trackDao.getArtists();
    }

    @Override
    public List<String> getAlbumArtists() {
        return trackDao.getAlbumArtists();
    }

    @Override
    public Track findById(long id) {
        return trackDao.findById(id);
    }

    @Override
    public List<Track> findByGroupingAndArtist(String grouping, String artist) {
        return new ArrayList<>(trackDao.findByGroupingAndArtist(grouping, artist));
    }

    @Override
    public List<Track> findByIdRanges(long idRange1, long idRange2) {
        return new ArrayList<>(trackDao.findByIdRanges(idRange1, idRange2));
    }

    @Override
    public List<Track> findNoEmbedCoverArtSong() {
        return new ArrayList<>(trackDao.findNoEmbedCoverArtSong());
    }

    @Override
    public List<Track> getArtistWithChildrenCount() {
        Map<String, AudioTag> artistMap = new HashMap<>();
        for (ArtistStats s : trackDao.getArtistStats()) {
            String artistField = s.artist != null ? s.artist.trim() : "";
            String[] parts = ARTIST_SPLIT.split(artistField);
            for (String part : parts) {
                part = part.trim();
                if (part.isEmpty()) part = Constants.NONE;
                AudioTag item = artistMap.getOrDefault(part, new AudioTag(SearchCriteria.TYPE.ARTIST, part));
                item.setChildCount(item.getChildCount() + s.cnt);
                item.setAudioDuration(item.getAudioDuration() + s.dur);
                artistMap.put(part, item);
            }
        }
        return new ArrayList<>(artistMap.values());
    }

    @Override
    public List<Track> getAlbumAndArtistWithChildrenCount() {
        List<Track> list = new ArrayList<>();
        for (AlbumStats s : trackDao.getAlbumStats()) {
            String album = s.album != null ? s.album : Constants.NONE;
            String albumArtist = s.albumArtist;
            String name;
            if (StringUtils.isVariousArtists(albumArtist)) {
                name = album;
            } else {
                name = album + " (by " + albumArtist + ")";
            }
            AudioTag item = new AudioTag(SearchCriteria.TYPE.ARTIST, name);
            item.setUniqueKey(s.albumArtFilename != null ? s.albumArtFilename : "");
            item.setChildCount(s.cnt);
            list.add(item);
        }
        return list;
    }

    @Override
    public List<Track> findByArtist(String name, long firstResult, long maxResults) {
        return new ArrayList<>(trackDao.findByArtist(name, firstResult, maxResults <= 0 ? -1 : maxResults));
    }

    @Override
    public List<Track> findByAlbumAndAlbumArtist(String album, String albumArtist, long firstResult, long maxResults) {
        return new ArrayList<>(trackDao.findByAlbumAndAlbumArtist(album, albumArtist, firstResult, maxResults <= 0 ? -1 : maxResults));
    }

    @Override
    public Track findByAlbumArtFilename(String albumUniqueKey) {
        return trackDao.findByAlbumArtFilename(albumUniqueKey);
    }

    @Override
    public void cleanInvalidTag() throws Exception {}

    @Override
    public List<Track> findForPlaylist() {
        return new ArrayList<>(trackDao.findForPlaylist());
    }

    @Override
    public List<Track> findForPlaylist(long firstResult, long maxResults) {
        return new ArrayList<>(trackDao.findForPlaylist(firstResult, maxResults <= 0 ? -1 : maxResults));
    }

    @Override
    public long getTotalSongs() throws SQLException {
        return trackDao.getTotalCount();
    }

    @Override
    public long getTotalDuration() throws SQLException {
        return (long) trackDao.getTotalDuration();
    }

    @Override
    public List<Track> findByIds(long[] ids) {
        return new ArrayList<>(trackDao.findByIds(ids));
    }

    private final List<Long> cachedQueueIds = new ArrayList<>();
    private static final String PREF_QUEUE_NAME = "mmate_playing_queue";
    private static final String PREF_KEY_IDS = "queue_track_ids";
    private static final String PREF_KEY_SHUFFLE = "queue_shuffle_mode";
    private static final String PREF_KEY_REPEAT = "queue_repeat_mode";

    private void loadQueueFromDisk() {
        if (context == null) return;
        try {
            android.content.SharedPreferences prefs = context.getSharedPreferences(PREF_QUEUE_NAME, Context.MODE_PRIVATE);
            String raw = prefs.getString(PREF_KEY_IDS, "");
            cachedQueueIds.clear();
            if (raw != null && !raw.isEmpty()) {
                String[] parts = raw.split(",");
                for (String part : parts) {
                    try {
                        cachedQueueIds.add(Long.parseLong(part.trim()));
                    } catch (NumberFormatException ignored) {}
                }
            }
        } catch (Exception e) {
            Log.e("RoomDbHelper", "Failed to load playing queue from disk", e);
        }
    }

    private void saveQueueToDisk() {
        if (context == null) return;
        try {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < cachedQueueIds.size(); i++) {
                if (i > 0) sb.append(",");
                sb.append(cachedQueueIds.get(i));
            }
            context.getSharedPreferences(PREF_QUEUE_NAME, Context.MODE_PRIVATE)
                    .edit()
                    .putString(PREF_KEY_IDS, sb.toString())
                    .apply();
        } catch (Exception e) {
            Log.e("RoomDbHelper", "Failed to save playing queue to disk", e);
        }
    }

    @Override
    public synchronized void addToPlayingQueue(Track song) throws SQLException {
        if (song != null) {
            cachedQueueIds.add(song.getId());
            saveQueueToDisk();
        }
    }

    @Override
    public synchronized void savePlayingQueue(List<Track> songsInContext) {
        cachedQueueIds.clear();
        if (songsInContext != null) {
            for (Track track : songsInContext) {
                if (track != null) {
                    cachedQueueIds.add(track.getId());
                }
            }
        }
        saveQueueToDisk();
    }

    @Override
    public synchronized List<Track> getPlayingQueue() {
        if (cachedQueueIds.isEmpty()) {
            loadQueueFromDisk();
        }
        if (cachedQueueIds.isEmpty()) {
            return Collections.emptyList();
        }
        long[] ids = new long[cachedQueueIds.size()];
        for (int i = 0; i < cachedQueueIds.size(); i++) {
            ids[i] = cachedQueueIds.get(i);
        }
        List<TrackEntity> entities = trackDao.findByIds(ids);
        if (entities == null || entities.isEmpty()) return Collections.emptyList();

        Map<Long, Track> trackMap = new HashMap<>();
        for (TrackEntity entity : entities) {
            trackMap.put(entity.getId(), entity);
        }

        List<Track> result = new ArrayList<>();
        for (Long id : cachedQueueIds) {
            Track t = trackMap.get(id);
            if (t != null) {
                result.add(t);
            }
        }
        return result;
    }

    @Override
    public synchronized void emptyPlayingQueue() {
        cachedQueueIds.clear();
        saveQueueToDisk();
    }

    @Override
    public List<Track> getSoundGradeWithStats() {
        List<Track> list = new ArrayList<>();
        addSoundGrade(list, "DSD",        trackDao.countDSD(),        trackDao.durDSD());
        addSoundGrade(list, "MQA",        trackDao.countMQA(),        trackDao.durMQA());
        addSoundGrade(list, "Hi-Res",     trackDao.countHiRes(),      trackDao.durHiRes());
        addSoundGrade(list, "Studio",     trackDao.countStudio(),     trackDao.durStudio());
        addSoundGrade(list, "CD",         trackDao.countCD(),         trackDao.durCD());
        addSoundGrade(list, "Compressed", trackDao.countCompressed(),  trackDao.durCompressed());
        return list;
    }

    private void addSoundGrade(List<Track> list, String grade, long count, double dur) {
        if (count > 0) {
            AudioTag item = new AudioTag(SearchCriteria.TYPE.SOUND_GRADE, grade);
            item.setChildCount(count);
            item.setAudioDuration(dur);
            list.add(item);
        }
    }

    @Override
    public List<Track> getGenreWithStats() {
        List<Track> list = new ArrayList<>();
        for (GenreStats s : trackDao.getGenreStats()) {
            AudioTag item = new AudioTag(SearchCriteria.TYPE.GENRE, s.genre != null ? s.genre : "");
            item.setChildCount(s.cnt);
            item.setAudioDuration(s.dur);
            list.add(item);
        }
        return list;
    }

    @Override
    public List<Track> getArtistWithStats() {
        Map<String, AudioTag> artistMap = new HashMap<>();
        for (ArtistStats s : trackDao.getArtistStats()) {
            String artistField = s.artist != null ? s.artist.trim() : "";
            String[] parts = ARTIST_SPLIT.split(artistField);
            for (String part : parts) {
                part = part.trim();
                if (part.isEmpty()) part = "";
                AudioTag item = artistMap.getOrDefault(part, new AudioTag(SearchCriteria.TYPE.ARTIST, part));
                item.setChildCount(item.getChildCount() + s.cnt);
                item.setAudioDuration(item.getAudioDuration() + s.dur);
                artistMap.put(part, item);
            }
        }
        return new ArrayList<>(artistMap.values());
    }

    private TrackEntity toEntity(Track tag) {
        TrackEntity entity = new TrackEntity();
        entity.setId(tag.getId());
        entity.setUniqueKey(tag.getUniqueKey() != null ? tag.getUniqueKey() : "");
        entity.setPath(tag.getPath());
        entity.setTitle(tag.getTitle());
        entity.setArtist(tag.getArtist());
        entity.setAlbum(tag.getAlbum());
        entity.setYear(tag.getYear());
        entity.setGenre(tag.getGenre());
        entity.setMood(tag.getMood());
        entity.setStyle(tag.getStyle());
        entity.setOrigin(tag.getOrigin());
        entity.setTrack(tag.getTrack());
        entity.setComment(tag.getComment());
        entity.setComposer(tag.getComposer());
        entity.setAlbumArtist(tag.getAlbumArtist());
        entity.setCompilation(tag.isCompilation());
        entity.setPublisher(tag.getPublisher());
        entity.setQualityInd(tag.getQualityInd());
        entity.setAudioBitsDepth(tag.getAudioBitsDepth());
        entity.setAudioSampleRate(tag.getAudioSampleRate());
        entity.setAudioBitRate(tag.getAudioBitRate());
        entity.setAudioChannels(tag.getAudioChannels());
        entity.setAudioDuration(tag.getAudioDuration());
        entity.setAudioEncoding(tag.getAudioEncoding());
        entity.setFileSize(tag.getFileSize());
        entity.setFileLastModified(tag.getFileLastModified());
        entity.setFileType(tag.getFileType());
        entity.setAlbumArtFilename(tag.getAlbumArtFilename());
        entity.setDrScore(tag.getDrScore());
        entity.setDynamicRange(tag.getDynamicRange());
        entity.setBpm(tag.getBpm());
        entity.setIsManaged(tag.isManaged());
        entity.setStorageId(tag.getStorageId());
        entity.setSimpleName(tag.getSimpleName());
        entity.setMqaSampleRate(tag.getMqaSampleRate());
        return entity;
    }

    // -------------------------------------------------------------------------
    // WHERE clause builder — mirrors OrmLite buildWhereClauseWithArgs logic
    // Returns String[]: [0]=WHERE clause (with ? placeholders), [1..n]=bind args
    // -------------------------------------------------------------------------
    private String[] buildWhereClause(SearchCriteria criteria) {
        if (criteria.isSearchMode()) {
            String like = "%" + criteria.getSearchText() + "%";
            return new String[]{"(title LIKE ? OR artist LIKE ? OR album LIKE ?)", like, like, like};
        }
        String kw;
        switch (criteria.getType()) {
            case LIBRARY:
                kw = criteria.getKeyword() != null ? criteria.getKeyword().trim() : "";
                if (kw.isEmpty() || Constants.TITLE_ALL_SONGS.equals(kw)) return new String[]{""};
                if (Constants.TITLE_INCOMING_SONGS.equals(kw))  return new String[]{"isManaged = 0"};
                if (Constants.TITLE_TO_ANALYST_DR.equals(kw))   return new String[]{"drScore = 0 OR dynamicRange = 0"};
                if (Constants.TITLE_NO_COVERART.equals(kw))     return new String[]{"albumArtFilename IS NULL OR albumArtFilename = ''"};
                return new String[]{""};

            case PUBLISHER:
                kw = criteria.getKeyword() != null ? criteria.getKeyword().trim() : "";
                if (kw.isEmpty() || Constants.UNKNOWN.equals(kw)) return new String[]{"publisher IS NULL"};
                return new String[]{"publisher = ?", kw};

            case ARTIST:
                kw = criteria.getKeyword();
                if (kw == null || kw.isEmpty()) return new String[]{""};
                // Match exact or within comma-separated multi-artist field
                return new String[]{
                    "artist = ? OR artist LIKE ? OR artist LIKE ? OR artist LIKE ?",
                    kw,
                    kw + ",%",
                    "%," + kw,
                    "%," + kw + ",%"
                };

            case SOUND_GRADE:
                kw = criteria.getKeyword();
                if (kw == null || kw.isEmpty()) return new String[]{""};
                if (Constants.TITLE_DSD.equals(kw))
                    return new String[]{"audioEncoding IN ('dsd', 'dff')"};
                if (Constants.TITLE_MQA_MASTER_QUALITY.equals(kw))
                    return new String[]{"qualityInd LIKE 'MQA%'"};
                if (Constants.TITLE_HIGH_QUALITY.equals(kw))
                    return new String[]{"audioEncoding IN ('aac', 'mpeg')"};
                if (Constants.TITLE_CD_QUALITY.equals(kw))
                    return new String[]{"audioEncoding IN ('flac','alac','aiff','wave','wav') AND audioBitsDepth = 16 AND qualityInd NOT LIKE 'MQA%'"};
                if (Constants.TITLE_HIRES_QUALITY.equals(kw))
                    return new String[]{"audioEncoding IN ('alac','flac','aiff','wave','wav') AND audioBitsDepth >= 24 AND audioSampleRate >= 96000 AND qualityInd NOT LIKE 'MQA%'"};
                if (Constants.TITLE_CD_EXT_QUALITY.equals(kw))
                    return new String[]{"audioEncoding IN ('alac','flac','aiff','wave','wav') AND audioBitsDepth >= 24 AND audioSampleRate < 96000 AND qualityInd NOT LIKE 'MQA%'"};
                return new String[]{""};

            case GENRE:
                kw = criteria.getKeyword();
                if (kw == null || kw.isEmpty()) return new String[]{""};
                return new String[]{"genre = ?", kw};

            default:
                return new String[]{""};
        }
    }

    private boolean needsGroupDedup(SearchCriteria criteria) {
        if (criteria.getType() == SearchCriteria.TYPE.ARTIST && criteria.getKeyword() != null && !criteria.getKeyword().isEmpty()) return true;
        if (criteria.getType() == SearchCriteria.TYPE.GENRE  && criteria.getKeyword() != null && !criteria.getKeyword().isEmpty()) return true;
        return false;
    }

    @Override
    public void saveShuffleMode(boolean enabled) {
        if (context == null) return;
        context.getSharedPreferences(PREF_QUEUE_NAME, Context.MODE_PRIVATE)
                .edit()
                .putBoolean(PREF_KEY_SHUFFLE, enabled)
                .apply();
    }

    @Override
    public boolean getShuffleMode() {
        if (context == null) return false;
        return context.getSharedPreferences(PREF_QUEUE_NAME, Context.MODE_PRIVATE)
                .getBoolean(PREF_KEY_SHUFFLE, false);
    }

    @Override
    public void saveRepeatMode(String mode) {
        if (context == null) return;
        context.getSharedPreferences(PREF_QUEUE_NAME, Context.MODE_PRIVATE)
                .edit()
                .putString(PREF_KEY_REPEAT, mode)
                .apply();
    }

    @Override
    public String getRepeatMode() {
        if (context == null) return "OFF";
        return context.getSharedPreferences(PREF_QUEUE_NAME, Context.MODE_PRIVATE)
                .getString(PREF_KEY_REPEAT, "OFF");
    }
}

