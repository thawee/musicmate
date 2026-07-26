package apincer.music.room;

import android.content.Context;

import javax.inject.Singleton;

import apincer.music.core.repository.spi.DbHelper;
import dagger.Module;
import dagger.Provides;
import dagger.hilt.InstallIn;
import dagger.hilt.android.qualifiers.ApplicationContext;
import dagger.hilt.components.SingletonComponent;

@Module
@InstallIn(SingletonComponent.class)
public class DatabaseModule {

    @Provides
    @Singleton
    public DbHelper provideDbHelper(@ApplicationContext Context context) {
        return new RoomDbHelperStub();
    }

    private static class RoomDbHelperStub implements DbHelper {
        @Override
        public apincer.music.core.model.SearchResultStats getSearchStats(apincer.music.core.model.SearchCriteria criteria) { return null; }
        @Override
        public apincer.music.core.model.SearchResultStats getSimilarSongsStats(boolean artistAware) { return null; }
        @Override
        public apincer.music.core.model.Track findByUniqueKey(String uniqueKey) { return null; }
        @Override
        public void purgeDatabase() {}
        @Override
        public void processAllMusics(apincer.music.core.repository.spi.TrackProcessor processor) {}
        @Override
        public java.util.List<apincer.music.core.model.Track> findMySongs() { return java.util.Collections.emptyList(); }
        @Override
        public java.util.List<apincer.music.core.model.Track> findMySongs(long firstResult, long maxResults) { return java.util.Collections.emptyList(); }
        @Override
        public java.util.List<apincer.music.core.model.Track> findByTitle(String title) { return java.util.Collections.emptyList(); }
        @Override
        public java.util.List<apincer.music.core.model.Track> findByPath(String path) { return java.util.Collections.emptyList(); }
        @Override
        public java.util.List<apincer.music.core.model.Track> findInPath(String path) { return java.util.Collections.emptyList(); }
        @Override
        public void save(apincer.music.core.model.Track tag) {}
        @Override
        public java.util.List<apincer.music.core.model.Track> getByPath(String path) { return java.util.Collections.emptyList(); }
        @Override
        public boolean isOutdated(apincer.music.core.model.Track tag, long lastModified) { return false; }
        @Override
        public void saveTagsBatch(java.util.List<apincer.music.core.model.Track> tags) {}
        @Override
        public void delete(apincer.music.core.model.Track tag) {}
        @Override
        public java.util.List<apincer.music.core.model.Track> findRecentlyAdded(long firstResult, long maxResults) { return java.util.Collections.emptyList(); }
        @Override
        public java.util.List<apincer.music.core.model.Track> findMyNoDRMeterSongs() { return java.util.Collections.emptyList(); }
        @Override
        public java.util.List<apincer.music.core.model.Track> findByGenre(String genre) { return java.util.Collections.emptyList(); }
        @Override
        public java.util.List<apincer.music.core.model.Track> findByGrouping(String grouping, long firstResult, long maxResults) { return java.util.Collections.emptyList(); }
        @Override
        public java.util.List<apincer.music.core.model.Track> findHiRes(long firstResult, long maxResults) { return java.util.Collections.emptyList(); }
        @Override
        public java.util.List<apincer.music.core.model.Track> findHiRes48(long firstResult, long maxResults) { return java.util.Collections.emptyList(); }
        @Override
        public java.util.List<apincer.music.core.model.Track> findHighQuality(long firstResult, long maxResults) { return java.util.Collections.emptyList(); }
        @Override
        public java.util.List<apincer.music.core.model.Track> findMQASongs(long firstResult, long maxResults) { return java.util.Collections.emptyList(); }
        @Override
        public java.util.List<apincer.music.core.model.Track> findDSDSongs(long firstResult, long maxResults) { return java.util.Collections.emptyList(); }
        @Override
        public java.util.List<apincer.music.core.model.Track> findByMediaQuality(String keyword) { return java.util.Collections.emptyList(); }
        @Override
        public java.util.List<apincer.music.core.model.Track> findByPublisher(String keyword) { return java.util.Collections.emptyList(); }
        @Override
        public java.util.List<apincer.music.core.model.Track> findCDQuality(long firstResult, long maxResults) { return java.util.Collections.emptyList(); }
        @Override
        public java.util.List<apincer.music.core.model.Track> findByKeyword(String keyword) { return java.util.Collections.emptyList(); }
        @Override
        public java.util.List<apincer.music.core.model.Track> findByKeyword(String keyword, long firstResult, long maxResults) { return java.util.Collections.emptyList(); }
        @Override
        public java.util.List<apincer.music.core.model.Track> findSimilarSongs(boolean artistAware) { return java.util.Collections.emptyList(); }
        @Override
        public java.util.List<apincer.music.core.model.Track> findSimilarSongs(boolean artistAware, long firstResult, long maxResults) { return java.util.Collections.emptyList(); }
        @Override
        public java.util.List<String> getGenres() { return java.util.Collections.emptyList(); }
        @Override
        public java.util.List<apincer.music.core.model.Track> getGenresWithChildrenCount() { return java.util.Collections.emptyList(); }
        @Override
        public java.util.List<String> getPublishers() { return java.util.Collections.emptyList(); }
        @Override
        public java.util.List<String> getArtists() { return java.util.Collections.emptyList(); }
        @Override
        public apincer.music.core.model.Track findById(long id) { return null; }
        @Override
        public java.util.List<apincer.music.core.model.Track> findByGroupingAndArtist(String grouping, String artist) { return java.util.Collections.emptyList(); }
        @Override
        public java.util.List<apincer.music.core.model.Track> findByIdRanges(long idRange1, long idRange2) { return java.util.Collections.emptyList(); }
        @Override
        public java.util.List<apincer.music.core.model.Track> findNoEmbedCoverArtSong() { return java.util.Collections.emptyList(); }
        @Override
        public java.util.List<apincer.music.core.model.Track> getArtistWithChildrenCount() { return java.util.Collections.emptyList(); }
        @Override
        public java.util.List<apincer.music.core.model.Track> getAlbumAndArtistWithChildrenCount() { return java.util.Collections.emptyList(); }
        @Override
        public java.util.List<apincer.music.core.model.Track> findByArtist(String name, long firstResult, long maxResults) { return java.util.Collections.emptyList(); }
        @Override
        public java.util.List<apincer.music.core.model.Track> findByAlbumAndAlbumArtist(String album, String albumArtist, long firstResult, long maxResults) { return java.util.Collections.emptyList(); }
        @Override
        public apincer.music.core.model.Track findByAlbumArtFilename(String albumUniqueKey) { return null; }
        @Override
        public java.util.List<apincer.music.core.model.Track> findByGenre(String name, long firstResult, long maxResults) { return java.util.Collections.emptyList(); }
        @Override
        public void cleanInvalidTag() throws Exception {}
        @Override
        public java.util.List<apincer.music.core.model.Track> findForPlaylist() { return java.util.Collections.emptyList(); }
        @Override
        public java.util.List<apincer.music.core.model.Track> findForPlaylist(long firstResult, long maxResults) { return java.util.Collections.emptyList(); }
        @Override
        public long getTotalSongs() { return 0; }
        @Override
        public long getTotalDuration() { return 0; }
        @Override
        public java.util.List<apincer.music.core.model.Track> findByIds(long[] ids) { return java.util.Collections.emptyList(); }
        @Override
        public void addToPlayingQueue(apincer.music.core.model.Track song) {}
        @Override
        public void savePlayingQueue(java.util.List<apincer.music.core.model.Track> songsInContext) {}
        @Override
        public java.util.List<apincer.music.core.model.Track> getPlayingQueue() { return java.util.Collections.emptyList(); }
        @Override
        public void emptyPlayingQueue() {}
        @Override
        public java.util.List<apincer.music.core.model.Track> getSoundGradeWithStats() { return java.util.Collections.emptyList(); }
        @Override
        public java.util.List<apincer.music.core.model.Track> getGenreWithStats() { return java.util.Collections.emptyList(); }
        @Override
        public java.util.List<apincer.music.core.model.Track> getArtistWithStats() { return java.util.Collections.emptyList(); }
    }
}
