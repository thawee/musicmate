package apincer.music.core.repository.spi;

import java.sql.SQLException;
import java.util.List;

import apincer.music.core.model.Track;

public interface DbHelper {

    Track findByUniqueKey(String uniqueKey);

    void purgeDatabase() throws SQLException;

    List<Track> findMySongs();

    List<Track> findByTitle(String title);

    List<Track> findByPath(String path);

    List<Track> findInPath(String path);

    void save(Track tag);

    List<Track> getByPath(String path);

    boolean isOutdated(Track tag, long lastModified);

    // Add batch save method
    @Deprecated
    void saveTagsBatch(List<Track> tags);

    void delete(Track tag) ;

    List<Track> findRecentlyAdded(long firstResult, long maxResults) ;

    List<Track> findMyNoDRMeterSongs();

    List<Track> findByGenre(String genre) ;

    List<Track> findByGrouping(String grouping, long firstResult, long maxResults) ;

    List<Track> findHiRes(long firstResult, long maxResults);

    List<Track> findHiRes48(long firstResult, long maxResults)  ;

    List<Track> findHighQuality(long firstResult, long maxResults) ;

    List<Track> findMQASongs(long firstResult, long maxResults);

    List<Track> findDSDSongs(long firstResult, long maxResults) ;

    @Deprecated
    List<Track> findByMediaQuality(String keyword);

    List<Track> findByPublisher(String keyword) ;

    List<Track> findCDQuality(long firstResult, long maxResults);

    List<Track> findByKeyword(String keyword);

    // In your OrmLiteHelper.java class
    List<Track> findSimilarSongs(boolean artistAware);

    List<String> getGenres() ;

   // List<String> getGrouping();

    List<Track> getGenresWithChildrenCount() ;

    List<String> getPublishers();

    List<String> getArtists() ;

    Track findById(long id);

    List<Track> findByGroupingAndArtist(String grouping, String artist) ;

    List<Track> findByIdRanges(long idRange1, long idRange2) ;

    List<Track> findNoEmbedCoverArtSong() ;

    List<Track> getArtistWithChildrenCount() ;

    List<Track> getAlbumAndArtistWithChildrenCount() ;

    List<Track> findByArtist(String name, long firstResult, long maxResults) ;

    List<Track> findByAlbumAndAlbumArtist(String album, String albumArtist, long firstResult, long maxResults) ;

    Track findByAlbumArtFilename(String albumUniqueKey) ;

    List<Track> findByGenre(String name, long firstResult, long maxResults) ;

    void cleanInvalidTag() throws Exception;

    List<Track> findForPlaylist();

    long getTotalSongs() throws SQLException;

    long getTotalDuration() throws SQLException;

    List<Track> findByIds(long[] ids);

    void addToPlayingQueue(Track song) throws SQLException;

    void savePlayingQueue(List<Track> songsInContext);
    List<Track> getPlayingQueue();

    void emptyPlayingQueue();
}
