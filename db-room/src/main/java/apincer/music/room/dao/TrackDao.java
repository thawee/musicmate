package apincer.music.room.dao;

import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.RawQuery;
import androidx.room.Update;
import androidx.sqlite.db.SupportSQLiteQuery;

import java.util.List;

import apincer.music.room.entity.TrackEntity;

@Dao
public interface TrackDao {

    @Query("SELECT * FROM musictag WHERE uniqueKey = :uniqueKey LIMIT 1")
    TrackEntity findByUniqueKey(String uniqueKey);

    @Query("SELECT * FROM musictag WHERE id = :id LIMIT 1")
    TrackEntity findById(long id);

    @Query("SELECT * FROM musictag ORDER BY title ASC")
    List<TrackEntity> findMySongs();

    @Query("SELECT * FROM musictag ORDER BY title ASC LIMIT :maxResults OFFSET :firstResult")
    List<TrackEntity> findMySongs(long firstResult, long maxResults);

    @Query("SELECT * FROM musictag WHERE title LIKE '%' || :title || '%' ORDER BY title ASC")
    List<TrackEntity> findByTitle(String title);

    @Query("SELECT * FROM musictag WHERE path = :path LIMIT 1")
    List<TrackEntity> findByPath(String path);

    @Query("SELECT * FROM musictag WHERE path LIKE :path || '%' ORDER BY title ASC")
    List<TrackEntity> findInPath(String path);

    @Query("SELECT * FROM musictag WHERE path = :path")
    List<TrackEntity> getByPath(String path);

    @Query("SELECT * FROM musictag WHERE isManaged = 0 ORDER BY fileLastModified DESC LIMIT :maxResults OFFSET :firstResult")
    List<TrackEntity> findRecentlyAdded(long firstResult, long maxResults);

    @Query("SELECT * FROM musictag WHERE drScore = 0 OR dynamicRange = 0 ORDER BY title ASC")
    List<TrackEntity> findMyNoDRMeterSongs();

    @Query("SELECT * FROM musictag WHERE genre = :genre ORDER BY title ASC")
    List<TrackEntity> findByGenre(String genre);

    @Query("SELECT * FROM musictag WHERE genre = :genre ORDER BY title ASC LIMIT :maxResults OFFSET :firstResult")
    List<TrackEntity> findByGenre(String genre, long firstResult, long maxResults);

    @Query("SELECT * FROM musictag WHERE mood = :grouping OR style = :grouping OR origin = :grouping ORDER BY title ASC LIMIT :maxResults OFFSET :firstResult")
    List<TrackEntity> findByGrouping(String grouping, long firstResult, long maxResults);

    @Query("SELECT * FROM musictag WHERE audioEncoding IN ('alac','flac','aiff','wave','wav') AND audioBitsDepth >= 24 AND audioSampleRate >= 96000 AND qualityInd NOT LIKE 'MQA%' ORDER BY title ASC LIMIT :maxResults OFFSET :firstResult")
    List<TrackEntity> findHiRes(long firstResult, long maxResults);

    @Query("SELECT * FROM musictag WHERE audioEncoding IN ('alac','flac','aiff','wave','wav') AND audioBitsDepth >= 24 AND audioSampleRate < 96000 AND qualityInd NOT LIKE 'MQA%' ORDER BY title ASC LIMIT :maxResults OFFSET :firstResult")
    List<TrackEntity> findHiRes48(long firstResult, long maxResults);

    @Query("SELECT * FROM musictag WHERE audioEncoding IN ('aac', 'mpeg') ORDER BY title ASC LIMIT :maxResults OFFSET :firstResult")
    List<TrackEntity> findHighQuality(long firstResult, long maxResults);

    @Query("SELECT * FROM musictag WHERE qualityInd LIKE 'MQA%' ORDER BY title ASC LIMIT :maxResults OFFSET :firstResult")
    List<TrackEntity> findMQASongs(long firstResult, long maxResults);

    @Query("SELECT * FROM musictag WHERE audioEncoding IN ('dsd', 'dff') ORDER BY title ASC LIMIT :maxResults OFFSET :firstResult")
    List<TrackEntity> findDSDSongs(long firstResult, long maxResults);

    @Query("SELECT * FROM musictag WHERE publisher LIKE '%' || :keyword || '%' ORDER BY title ASC")
    List<TrackEntity> findByPublisher(String keyword);

    @Query("SELECT * FROM musictag WHERE audioEncoding IN ('flac','alac','aiff','wave','wav') AND audioBitsDepth = 16 AND qualityInd NOT LIKE 'MQA%' ORDER BY title ASC LIMIT :maxResults OFFSET :firstResult")
    List<TrackEntity> findCDQuality(long firstResult, long maxResults);

    @Query("SELECT * FROM musictag WHERE title LIKE '%' || :keyword || '%' OR artist LIKE '%' || :keyword || '%' OR album LIKE '%' || :keyword || '%' ORDER BY title ASC")
    List<TrackEntity> findByKeyword(String keyword);

    @Query("SELECT * FROM musictag WHERE title LIKE '%' || :keyword || '%' OR artist LIKE '%' || :keyword || '%' OR album LIKE '%' || :keyword || '%' ORDER BY title ASC LIMIT :maxResults OFFSET :firstResult")
    List<TrackEntity> findByKeyword(String keyword, long firstResult, long maxResults);

    @Query("SELECT DISTINCT genre FROM musictag WHERE genre IS NOT NULL AND genre != '' ORDER BY genre ASC")
    List<String> getGenres();

    @Query("SELECT DISTINCT publisher FROM musictag WHERE publisher IS NOT NULL AND publisher != '' ORDER BY publisher ASC")
    List<String> getPublishers();

    @Query("SELECT DISTINCT artist FROM musictag WHERE artist IS NOT NULL AND artist != '' ORDER BY artist ASC")
    List<String> getArtists();

    @Query("SELECT * FROM musictag WHERE (mood = :grouping OR style = :grouping OR origin = :grouping) AND artist = :artist ORDER BY title ASC")
    List<TrackEntity> findByGroupingAndArtist(String grouping, String artist);

    @Query("SELECT * FROM musictag WHERE id BETWEEN :idRange1 AND :idRange2 ORDER BY id ASC")
    List<TrackEntity> findByIdRanges(long idRange1, long idRange2);

    @Query("SELECT * FROM musictag WHERE albumArtFilename IS NULL OR albumArtFilename = '' ORDER BY title ASC")
    List<TrackEntity> findNoEmbedCoverArtSong();

    @Query("SELECT * FROM musictag WHERE artist = :name OR artist LIKE :name || ',%' OR artist LIKE '%, ' || :name OR artist LIKE '%,' || :name OR artist LIKE '%,' || :name || ',%' OR artist LIKE '%, ' || :name || ',%' ORDER BY title ASC LIMIT :maxResults OFFSET :firstResult")
    List<TrackEntity> findByArtist(String name, long firstResult, long maxResults);

    @Query("SELECT * FROM musictag WHERE album = :album AND (albumArtist = :albumArtist OR artist = :albumArtist) ORDER BY track ASC, title ASC LIMIT :maxResults OFFSET :firstResult")
    List<TrackEntity> findByAlbumAndAlbumArtist(String album, String albumArtist, long firstResult, long maxResults);

    @Query("SELECT * FROM musictag WHERE albumArtFilename = :albumUniqueKey LIMIT 1")
    TrackEntity findByAlbumArtFilename(String albumUniqueKey);

    @Query("SELECT * FROM musictag ORDER BY title ASC")
    List<TrackEntity> findForPlaylist();

    @Query("SELECT * FROM musictag ORDER BY title ASC LIMIT :maxResults OFFSET :firstResult")
    List<TrackEntity> findForPlaylist(long firstResult, long maxResults);

    @Query("SELECT * FROM musictag WHERE id IN (:ids)")
    List<TrackEntity> findByIds(long[] ids);

    @Query("SELECT COUNT(*) FROM musictag")
    long getTotalCount();

    @Query("SELECT SUM(fileSize) FROM musictag")
    long getTotalSize();

    @Query("SELECT SUM(audioDuration) FROM musictag")
    double getTotalDuration();

    // --- Similar songs ---
    @Query("SELECT * FROM musictag WHERE normalizedTitle IN (SELECT normalizedTitle FROM musictag GROUP BY normalizedTitle HAVING COUNT(*) > 1) ORDER BY normalizedTitle ASC")
    List<TrackEntity> findSimilarByTitle();

    @Query("SELECT * FROM musictag WHERE normalizedTitle IN (SELECT normalizedTitle FROM musictag GROUP BY normalizedTitle HAVING COUNT(*) > 1) ORDER BY normalizedTitle ASC LIMIT :maxResults OFFSET :firstResult")
    List<TrackEntity> findSimilarByTitle(long firstResult, long maxResults);

    @Query("SELECT * FROM musictag WHERE (normalizedTitle, normalizedArtist) IN (SELECT normalizedTitle, normalizedArtist FROM musictag GROUP BY normalizedTitle, normalizedArtist HAVING COUNT(*) > 1) ORDER BY normalizedTitle ASC")
    List<TrackEntity> findSimilarByTitleAndArtist();

    @Query("SELECT * FROM musictag WHERE (normalizedTitle, normalizedArtist) IN (SELECT normalizedTitle, normalizedArtist FROM musictag GROUP BY normalizedTitle, normalizedArtist HAVING COUNT(*) > 1) ORDER BY normalizedTitle ASC LIMIT :maxResults OFFSET :firstResult")
    List<TrackEntity> findSimilarByTitleAndArtist(long firstResult, long maxResults);

    // --- Aggregation stats for category lists ---
    @Query("SELECT genre, COUNT(*) as cnt, SUM(audioDuration) as dur FROM musictag GROUP BY genre ORDER BY genre ASC")
    List<GenreStats> getGenreStats();

    @Query("SELECT artist, COUNT(*) as cnt, SUM(audioDuration) as dur FROM musictag GROUP BY artist ORDER BY artist ASC")
    List<ArtistStats> getArtistStats();

    @Query("SELECT album, albumArtist, MAX(albumArtFilename) as albumArtFilename, COUNT(*) as cnt FROM musictag GROUP BY album, albumArtist ORDER BY album ASC")
    List<AlbumStats> getAlbumStats();

    // --- Sound grade aggregations ---
    @Query("SELECT COUNT(*) FROM musictag WHERE audioEncoding IN ('dsd', 'dff')")
    long countDSD();

    @Query("SELECT SUM(audioDuration) FROM musictag WHERE audioEncoding IN ('dsd', 'dff')")
    double durDSD();

    @Query("SELECT COUNT(*) FROM musictag WHERE qualityInd LIKE 'MQA%'")
    long countMQA();

    @Query("SELECT SUM(audioDuration) FROM musictag WHERE qualityInd LIKE 'MQA%'")
    double durMQA();

    @Query("SELECT COUNT(*) FROM musictag WHERE audioEncoding IN ('alac','flac','aiff','wave','wav') AND audioBitsDepth >= 24 AND audioSampleRate >= 96000 AND qualityInd NOT LIKE 'MQA%'")
    long countHiRes();

    @Query("SELECT SUM(audioDuration) FROM musictag WHERE audioEncoding IN ('alac','flac','aiff','wave','wav') AND audioBitsDepth >= 24 AND audioSampleRate >= 96000 AND qualityInd NOT LIKE 'MQA%'")
    double durHiRes();

    @Query("SELECT COUNT(*) FROM musictag WHERE audioEncoding IN ('alac','flac','aiff','wave','wav') AND audioBitsDepth >= 24 AND audioSampleRate < 96000 AND qualityInd NOT LIKE 'MQA%'")
    long countStudio();

    @Query("SELECT SUM(audioDuration) FROM musictag WHERE audioEncoding IN ('alac','flac','aiff','wave','wav') AND audioBitsDepth >= 24 AND audioSampleRate < 96000 AND qualityInd NOT LIKE 'MQA%'")
    double durStudio();

    @Query("SELECT COUNT(*) FROM musictag WHERE audioEncoding IN ('flac','alac','aiff','wave','wav') AND audioBitsDepth = 16 AND qualityInd NOT LIKE 'MQA%'")
    long countCD();

    @Query("SELECT SUM(audioDuration) FROM musictag WHERE audioEncoding IN ('flac','alac','aiff','wave','wav') AND audioBitsDepth = 16 AND qualityInd NOT LIKE 'MQA%'")
    double durCD();

    @Query("SELECT COUNT(*) FROM musictag WHERE audioEncoding IN ('aac', 'mpeg')")
    long countCompressed();

    @Query("SELECT SUM(audioDuration) FROM musictag WHERE audioEncoding IN ('aac', 'mpeg')")
    double durCompressed();

    @RawQuery
    SearchStats getStatsByRawQuery(SupportSQLiteQuery query);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    long insert(TrackEntity track);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertAll(List<TrackEntity> tracks);

    @Update
    void update(TrackEntity track);

    @Delete
    void delete(TrackEntity track);

    @Query("DELETE FROM musictag")
    void purgeDatabase();

    @Query("SELECT * FROM musictag")
    List<TrackEntity> getAllTracks();
}
