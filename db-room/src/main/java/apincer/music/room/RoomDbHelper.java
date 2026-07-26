package apincer.music.room;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import apincer.music.core.model.AudioTag;
import apincer.music.core.model.SearchCriteria;
import apincer.music.core.model.SearchResultStats;
import apincer.music.core.model.Track;
import apincer.music.core.repository.spi.DbHelper;
import apincer.music.core.repository.spi.TrackProcessor;
import apincer.music.room.dao.AlbumStats;
import apincer.music.room.dao.ArtistStats;
import apincer.music.room.dao.GenreStats;
import apincer.music.room.dao.TrackDao;
import apincer.music.room.entity.TrackEntity;

public class RoomDbHelper implements DbHelper {

    private static final Pattern ARTIST_SPLIT = Pattern.compile("[;,]");

    private final MusicRoomDatabase database;
    private final TrackDao trackDao;

    public RoomDbHelper(MusicRoomDatabase database) {
        this.database = database;
        this.trackDao = database.trackDao();
    }

    @Override
    public SearchResultStats getSearchStats(SearchCriteria criteria) {
        int count = (int) trackDao.getTotalCount();
        long size = trackDao.getTotalSize();
        double duration = trackDao.getTotalDuration();
        return new SearchResultStats(count, size, duration);
    }

    @Override
    public SearchResultStats getSimilarSongsStats(boolean artistAware) {
        return new SearchResultStats(0, 0, 0);
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
        return new ArrayList<>(trackDao.findMySongs(firstResult, maxResults));
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
        return new ArrayList<>(trackDao.findRecentlyAdded(firstResult, maxResults));
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
        return new ArrayList<>(trackDao.findByGenre(genre, firstResult, maxResults));
    }

    @Override
    public List<Track> findByGrouping(String grouping, long firstResult, long maxResults) {
        return new ArrayList<>(trackDao.findByGrouping(grouping, firstResult, maxResults));
    }

    @Override
    public List<Track> findHiRes(long firstResult, long maxResults) {
        return new ArrayList<>(trackDao.findHiRes(firstResult, maxResults));
    }

    @Override
    public List<Track> findHiRes48(long firstResult, long maxResults) {
        return new ArrayList<>(trackDao.findHiRes48(firstResult, maxResults));
    }

    @Override
    public List<Track> findHighQuality(long firstResult, long maxResults) {
        return new ArrayList<>(trackDao.findHighQuality(firstResult, maxResults));
    }

    @Override
    public List<Track> findMQASongs(long firstResult, long maxResults) {
        return new ArrayList<>(trackDao.findMQASongs(firstResult, maxResults));
    }

    @Override
    public List<Track> findDSDSongs(long firstResult, long maxResults) {
        return new ArrayList<>(trackDao.findDSDSongs(firstResult, maxResults));
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
        return new ArrayList<>(trackDao.findCDQuality(firstResult, maxResults));
    }

    @Override
    public List<Track> findByKeyword(String keyword) {
        return new ArrayList<>(trackDao.findByKeyword(keyword));
    }

    @Override
    public List<Track> findByKeyword(String keyword, long firstResult, long maxResults) {
        return new ArrayList<>(trackDao.findByKeyword(keyword, firstResult, maxResults));
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
        if (artistAware) {
            return new ArrayList<>(trackDao.findSimilarByTitleAndArtist(firstResult, maxResults));
        }
        return new ArrayList<>(trackDao.findSimilarByTitle(firstResult, maxResults));
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
                if (part.isEmpty()) part = "[Unknown]";  
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
            String album = s.album != null ? s.album : "[Unknown]";
            String albumArtist = s.albumArtist;
            String name;
            if (albumArtist == null || albumArtist.isEmpty() ||
                    "Various Artists".equalsIgnoreCase(albumArtist) ||
                    "Soundtrack".equalsIgnoreCase(albumArtist)) {
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
        return new ArrayList<>(trackDao.findByArtist(name, firstResult, maxResults));
    }

    @Override
    public List<Track> findByAlbumAndAlbumArtist(String album, String albumArtist, long firstResult, long maxResults) {
        return new ArrayList<>(trackDao.findByAlbumAndAlbumArtist(album, albumArtist, firstResult, maxResults));
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
        return new ArrayList<>(trackDao.findForPlaylist(firstResult, maxResults));
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

    @Override
    public void addToPlayingQueue(Track song) throws SQLException {}

    @Override
    public void savePlayingQueue(List<Track> songsInContext) {}

    @Override
    public List<Track> getPlayingQueue() {
        return Collections.emptyList();
    }

    @Override
    public void emptyPlayingQueue() {}

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
        return entity;
    }
}
