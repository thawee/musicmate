package apincer.music.ormlite;

import static apincer.music.core.Constants.ARTIST_SEP;
import static apincer.music.core.Constants.NONE;
import static apincer.music.core.utils.StringUtils.EMPTY;
import static apincer.music.core.utils.StringUtils.isEmpty;
import static apincer.music.core.utils.StringUtils.trimToEmpty;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.util.Log;

import com.j256.ormlite.android.apptools.OrmLiteSqliteOpenHelper;
import com.j256.ormlite.dao.Dao;
import com.j256.ormlite.dao.GenericRawResults;
import com.j256.ormlite.stmt.QueryBuilder;
import com.j256.ormlite.stmt.SelectArg;
import com.j256.ormlite.stmt.Where;
import com.j256.ormlite.support.ConnectionSource;
import com.j256.ormlite.table.TableUtils;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;

import apincer.music.core.Constants;
import apincer.music.core.model.AudioTag;
import apincer.music.core.model.Track;
import apincer.music.core.model.SearchCriteria;
import apincer.music.core.repository.spi.DbHelper;
import apincer.music.core.repository.FileRepository;
import apincer.music.core.utils.LogHelper;
import apincer.music.core.utils.StringUtils;
import apincer.music.ormlite.model.TrackEntity;
import apincer.music.ormlite.model.PlayingQueue;
import apincer.music.ormlite.model.Playlist;
import apincer.music.ormlite.model.PlaylistItem;

public class OrmLiteHelper extends OrmLiteSqliteOpenHelper implements DbHelper {
    // DAO objects
    private Dao<TrackEntity, Long> musicTagDao = null;
    private Dao<PlayingQueue, Long> queueItemDao = null;

    public TrackEntity findByUniqueKey(String uniqueKey) {
        try {
            Dao<TrackEntity, ?> dao = getMusicTagDao();
            QueryBuilder<TrackEntity, ?> builder = dao.queryBuilder();
            builder.where().eq("uniqueKey", uniqueKey);
            return dao.queryForFirst(builder.prepare());
        } catch (SQLException ex) {
            Log.e(TAG,"findByUniqueKey", ex);
        }
        return null;
    }

    public void purgeDatabase() throws SQLException {
        // delete all music tag table
        TableUtils.clearTable(getConnectionSource(), TrackEntity.class);

        // Reset the auto-increment counter in SQLite's internal sequence table
        getMusicTagDao().executeRaw("DELETE FROM sqlite_sequence WHERE name='musictag'");
    }

    public enum ORDERED_BY {ALBUM, ARTIST,TITLE}
    //Database name
    private static final String DATABASE_NAME = "apincer.musicmate.db";
    private static final String TAG = LogHelper.getTag(OrmLiteHelper.class);
    private static final int DATABASE_VERSION = 15;
    public static final List<Track> EMPTY_LIST = null;
    private static final List<String> EMPTY_STRING_LIST = null;
    private static final String LIKE_LITERAL = "%";

    public OrmLiteHelper(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION) ;
        //,
                // R.raw.ormlite_config is a reference to the ormlite_config2.txt file in the
                // /res/raw/ directory of this project
              //  R.raw.ormlite_config);
    }
    @Override
    public void onCreate(SQLiteDatabase database, ConnectionSource connectionSource) {
        try {
            // creates the database table
            TableUtils.createTable(connectionSource, TrackEntity.class);
            TableUtils.createTable(connectionSource, Playlist.class);
            TableUtils.createTable(connectionSource, PlaylistItem.class);
            TableUtils.createTable(connectionSource, PlayingQueue.class);
        } catch (SQLException e) {
            Log.e(TAG,"onCreate", e);
        }
    }

    @Override
    public void onUpgrade(SQLiteDatabase database, ConnectionSource connectionSource, int oldVersion, int newVersion) {
        try {
            // Recreates the database when onUpgrade is called by the framework
            TableUtils.dropTable(connectionSource, TrackEntity.class, true);
            TableUtils.dropTable(connectionSource, Playlist.class, true);
            TableUtils.dropTable(connectionSource, PlaylistItem.class, true);
            TableUtils.dropTable(connectionSource, PlayingQueue.class, true);
            onCreate(database, connectionSource);
        } catch (SQLException e) {
            Log.e(TAG,"onUpgrade", e);
        }
    }

    @Override
    public void onDowngrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        onUpgrade(db, oldVersion, newVersion);
    }

    public List<Track> findMySongs(ORDERED_BY[] orderedByList)  {
        try {
            Dao<TrackEntity, ?> dao = getMusicTagDao();
            QueryBuilder<TrackEntity, ?> builder = dao.queryBuilder();
            if(orderedByList != null) {
                for(ORDERED_BY orderBy: orderedByList) {
                    if (orderBy == ORDERED_BY.TITLE) {
                        builder.orderByNullsFirst("title", true);
                    }else if (orderBy == ORDERED_BY.ARTIST) {
                        builder.orderByNullsFirst("artist", true);
                    }else if (orderBy == ORDERED_BY.ALBUM) {
                        builder.orderByNullsFirst("album", true);
                    }
                }
            }
          //  return dao.queryBuilder().orderBy("title", true).orderByNullsFirst("artist", true).query();
            List<TrackEntity> results = builder.query();
            return new ArrayList<>(results);
        } catch (SQLException e) {
            return EMPTY_LIST;
        }
    }

    public List<Track> findMySongs()  {
        try {
            Dao<TrackEntity, ?> dao = getMusicTagDao();

            List<TrackEntity> results = dao.queryBuilder().orderBy("normalizedTitle", true).orderByNullsFirst("normalizedArtist", true).query();
            return new ArrayList<>(results);
        } catch (SQLException e) {
            return EMPTY_LIST;
        }
    }

    public List<Track> findByTitle(String title)  {
        try {
            Dao<TrackEntity, ?> dao =getMusicTagDao();

            QueryBuilder<TrackEntity, ?> builder = dao.queryBuilder();
            String keyword = LIKE_LITERAL+title.replace("'","''")+LIKE_LITERAL;
            builder.where().like("title",keyword).or().like("path", keyword);
            List<TrackEntity> results = builder.query();
            return new ArrayList<>(results);
        } catch (SQLException e) {
            return EMPTY_LIST;
        }
    }

    public List<Track> findByPath(String path)  {
        try {
            Dao<TrackEntity, ?> dao = getMusicTagDao();

            QueryBuilder<TrackEntity, ?> builder = dao.queryBuilder();
            builder.where().eq("path",escapeString(path));
            List<TrackEntity> results = builder.query();
            return new ArrayList<>(results);
        } catch (Exception e) {
            return EMPTY_LIST;
        }
    }

    public List<Track> findInPath(String path)  {
        try {
            Dao<TrackEntity, ?> dao = getMusicTagDao();

            QueryBuilder<TrackEntity, ?> builder = dao.queryBuilder();
            builder.where().like("path",escapeString(path)+LIKE_LITERAL);
            builder.orderBy("path", true);
            List<TrackEntity> results = builder.query();
            return new ArrayList<>(results);
        } catch (Exception e) {
            return EMPTY_LIST;
        }
    }

    public void save(Track track) {
        try {
            Dao<TrackEntity, Long> dao = getMusicTagDao();
            TrackEntity tag = new TrackEntity(track);
            dao.createOrUpdate(tag);
        } catch (SQLException e) {
            Log.e(TAG, "save", e);
        }
    }

    public List<Track> getByPath(String path) {
        try {
            Dao<TrackEntity, ?> dao = getMusicTagDao();

            // Escape single quotes by replacing ' with ''
            String escapedPath = escapeString(path);

            // Use a direct query that only fetches lastModified
            QueryBuilder<TrackEntity, ?> qb = dao.queryBuilder();
            qb.where().eq("path", escapedPath);

            List<TrackEntity> results = qb.query();
            return new ArrayList<>(results);

           // return dao.queryForFirst(qb.prepare());

        } catch (SQLException e) {
            Log.e(TAG, "getByPath", e);
            return EMPTY_LIST; // Assume outdated if we can't check
        }
    }

    @Override
    public boolean isOutdated(Track tag, long lastModified) {
        return tag == null || tag.getFileLastModified() < lastModified;
    }

    @Override
    public void delete(Track tag) {
        try {
            Dao<TrackEntity, ?> dao = getMusicTagDao();
            dao.delete((TrackEntity) tag);
        } catch (SQLException ignored) {
        }
    }

    // Add batch save method
    @Deprecated
    public void saveTagsBatch(List<Track> tags) {
        if (tags == null || tags.isEmpty()) return;

        try {
            Dao<TrackEntity, ?> dao = getMusicTagDao();

            // Use transaction for better performance
            dao.callBatchTasks(() -> {
                for (Track tag : tags) {
                    dao.createOrUpdate((TrackEntity) tag);
                }
                return null;
            });
        } catch (Exception e) {
            Log.e(TAG, "saveTagsBatch", e);
        }
    }

    private String escapeString(String text) {
        return text.replace("'","''");
    }

    public List<Track> findRecentlyAdded(long firstResult, long maxResults)  {
        try {
            Dao<TrackEntity, ?> dao = getMusicTagDao();
            QueryBuilder<TrackEntity, ?> builder = dao.queryBuilder();
            builder.where().eq("isManaged",false);
            if(firstResult>0) {
                builder.offset(firstResult);
            }
            if(maxResults>0) {
                builder.limit(maxResults);
            }

            //List<MusicTag> masterList = builder.orderByNullsFirst("title", true).orderByNullsFirst("artist", true).query();
            List<TrackEntity> masterList = builder.query();
            masterList.sort(Comparator
                    .comparing(TrackEntity::getNormalizedTitle)
                    .thenComparing(TrackEntity::getNormalizedArtist)
            );
            return new ArrayList<>(masterList);
        } catch (SQLException e) {
            return EMPTY_LIST;
        }
    }

    public List<Track> findMyNoDRMeterSongs()  {
        try {
            Dao<TrackEntity, ?> dao = getMusicTagDao();
            QueryBuilder<TrackEntity, ?> builder = dao.queryBuilder();
            builder.where().eq("drScore",0)
                    .or().eq("dynamicRange", 0);
            List<TrackEntity> queryResults = builder.orderByNullsFirst("title", true).orderByNullsFirst("artist", true).query();
            return new ArrayList<>(queryResults);
        } catch (SQLException e) {
            return EMPTY_LIST;
        }
    }

    public List<Track> findByGenre(String genre)   {
        try {
            Dao<TrackEntity, ?> dao = getMusicTagDao();
            QueryBuilder<TrackEntity, ?> builder = dao.queryBuilder();
            if(StringUtils.isEmpty(genre)) {
                builder.where().isNull("genre").or().eq("genre", "");
            }else {
                //builder.where().eq("genre", genre.replace("'", "''"));
                builder.where().eq("genre", new SelectArg(genre));
            }
            List<TrackEntity> queryResults = builder.groupBy("title").groupBy("artist").query();
            return new ArrayList<>(queryResults);
        } catch (SQLException e) {
            return EMPTY_LIST;
        }
    }

    public List<Track> findByGrouping(String grouping, long firstResult, long maxResults)   {
        try {
            Dao<TrackEntity, ?> dao = getMusicTagDao();
            QueryBuilder<TrackEntity, ?> builder = dao.queryBuilder();
            if(StringUtils.isEmpty(grouping)) {
                builder.where().isNull("grouping").or().eq("grouping", "");
            }else {
                //builder.where().eq("grouping", grouping.replace("'", "''"));
                builder.where().eq("grouping", new SelectArg(grouping));
            }
            if(firstResult>0) {
                builder.offset(firstResult);
            }
            if(maxResults>0) {
                builder.limit(maxResults);
            }
            List<TrackEntity> queryResults = builder.groupBy("title").groupBy("artist").query();

            return new ArrayList<>(queryResults);
        } catch (SQLException e) {
            return EMPTY_LIST;
        }

    }

    public List<Track> findHiRes(long firstResult, long maxResults)   {
        try {
            Dao<TrackEntity, ?> dao = getMusicTagDao();
            QueryBuilder<TrackEntity, ?> builder = dao.queryBuilder();
            builder.where().raw("audioEncoding in ('alac', 'flac','aiff', 'wave', 'wav') and audioBitsDepth >= 24 and audioSampleRate >= 96000 and qualityInd not like 'MQA%'");
            if(firstResult>0) {
                builder.offset(firstResult);
            }
            if(maxResults>0) {
                builder.limit(maxResults);
            }
            List<TrackEntity> queryResults = builder.query();

            return new ArrayList<>(queryResults);
        } catch (SQLException e) {
            return EMPTY_LIST;
        }

    }

    public List<Track> findHiRes48(long firstResult, long maxResults)   {
        try {
            Dao<TrackEntity, ?> dao = getMusicTagDao();
            QueryBuilder<TrackEntity, ?> builder = dao.queryBuilder();
            builder.where().raw("audioEncoding in ('alac', 'flac','aiff', 'wave', 'wav') and audioBitsDepth >= 24 and audioSampleRate < 96000 and qualityInd not like 'MQA%'");
            if(firstResult>0) {
                builder.offset(firstResult);
            }
            if(maxResults>0) {
                builder.limit(maxResults);
            }

            List<TrackEntity> results = builder.query();
            return new ArrayList<>(results);
        } catch (SQLException e) {
            return EMPTY_LIST;
        }

    }

    public List<Track> findHighQuality(long firstResult, long maxResults)   {
        try {
            Dao<TrackEntity, ?> dao = getMusicTagDao();
            QueryBuilder<TrackEntity, ?> builder = dao.queryBuilder();
            builder.where().raw(" audioEncoding in ('aac', 'mpeg') ");
            if(firstResult>0) {
                builder.offset(firstResult);
            }
            if(maxResults>0) {
                builder.limit(maxResults);
            }
            List<TrackEntity> results = builder.query();
            return new ArrayList<>(results);
        } catch (SQLException e) {
            return EMPTY_LIST;
        }

    }

    public List<Track> findMQASongs(long firstResult, long maxResults) {
        try {
            Dao<TrackEntity, ?> dao = getMusicTagDao();
            QueryBuilder<TrackEntity, ?> builder = dao.queryBuilder();
            builder.where().raw("qualityInd like 'MQA%' order by title, artist");
            if(firstResult>0) {
                builder.offset(firstResult);
            }
            if(maxResults>0) {
                builder.limit(maxResults);
            }
            List<TrackEntity> results = builder.query();
            return new ArrayList<>(results);
        } catch (SQLException e) {
            return EMPTY_LIST;
        }
    }

    public List<Track> findDSDSongs(long firstResult, long maxResults) {
        try {
            Dao<TrackEntity, ?> dao = getMusicTagDao();
            QueryBuilder<TrackEntity, ?> builder = dao.queryBuilder();
            builder.where().raw("audioEncoding in ('dsd', 'dff') order by title, artist");
            if(firstResult>0) {
                builder.offset(firstResult);
            }
            if(maxResults>0) {
                builder.limit(maxResults);
            }
            List<TrackEntity> results = builder.query();
            return new ArrayList<>(results);
        } catch (SQLException e) {
            return EMPTY_LIST;
        }
    }

    @Deprecated
    public List<Track> findByMediaQuality(String keyword) {
        try {
            Dao<TrackEntity, ?> dao = getMusicTagDao();
            QueryBuilder<TrackEntity, ?> builder = dao.queryBuilder();
            if (isEmpty(keyword)) {
                builder.where().raw("mediaQuality is null order by title, artist");
                List<TrackEntity> results = builder.query();
                return new ArrayList<>(results);
            } else {
                builder.where().raw("mediaQuality="+escapeString(keyword)+" order by title, artist");
                List<TrackEntity> results = builder.query();
                return new ArrayList<>(results);
            }
        } catch (SQLException e) {
            return EMPTY_LIST;
        }
    }

    public List<Track> findByPublisher(String keyword) {
        try {
            Dao<TrackEntity, ?> dao = getMusicTagDao();
            QueryBuilder<TrackEntity, ?> builder = dao.queryBuilder();
            if (isEmpty(keyword) || Constants.UNKNOWN.equals(keyword)) {
                builder.where().raw("publisher is null order by title, artist");
                List<TrackEntity> results = builder.query();
                return new ArrayList<>(results);
            } else {
                builder.where().raw("publisher="+escapeString(keyword)+" order by title, artist");
                List<TrackEntity> results = builder.query();
                return new ArrayList<>(results);
            }
        } catch (SQLException e) {
            return EMPTY_LIST;
        }
    }

    public List<Track> findCDQuality(long firstResult, long maxResults) {
        try {
            Dao<TrackEntity, ?> dao = getMusicTagDao();
            QueryBuilder<TrackEntity, ?> builder = dao.queryBuilder();
           // builder.where().raw("audioEncoding in ('flac','alac','aiff','wave','wav') and audioSampleRate < 96000 and qualityInd not like 'MQA%' order by title, artist");
            builder.where().raw("audioEncoding in ('flac','alac','aiff','wave','wav') and audioBitsDepth = 16 and audioSampleRate = 44100 order by title, artist");
            if(firstResult>0) {
                builder.offset(firstResult);
            }
            if(maxResults>0) {
                builder.limit(maxResults);
            }
            List<TrackEntity> results = builder.query();
            return new ArrayList<>(results);
        } catch (SQLException e) {
            return EMPTY_LIST;
        }
    }

    public List<Track> findByKeyword(String keyword) {
        try {
            Dao<TrackEntity, ?> dao = getMusicTagDao();
            keyword = "'"+LIKE_LITERAL+keyword.replace("'","''")+LIKE_LITERAL+"'";
            QueryBuilder<TrackEntity, ?> builder = dao.queryBuilder();
            builder.where().raw("title like "+keyword+" or artist like "+keyword +" or album like "+keyword);
            List<TrackEntity> results = builder.query();
            return new ArrayList<>(results);
        } catch (SQLException e) {
            return EMPTY_LIST;
        }
    }

    // In your OrmLiteHelper.java class
    public List<Track> findSimilarSongs(boolean artistAware) {
        try {
            Dao<TrackEntity, Long> dao = getMusicTagDao();

            if (artistAware) {
                // --- Case 1: Find songs with the same title AND artist ---
                // This is more complex and best handled with a single, clear raw query.

                String rawQuery = "SELECT * FROM musictag WHERE (normalizedTitle, normalizedArtist) " +
                        "IN (SELECT normalizedTitle, normalizedArtist FROM musictag " +
                        "GROUP BY normalizedTitle, normalizedArtist HAVING COUNT(*) > 1) " +
                        "ORDER BY normalizedTitle, normalizedArtist";

                try (GenericRawResults<TrackEntity> rawResults = dao.queryRaw(rawQuery, dao.getRawRowMapper())) {
                    // Use getResults() which is the correct method to get the list
                    List<TrackEntity> results =  rawResults.getResults();
                    return new ArrayList<>(results);
                }

            } else {
                // --- Case 2: Find songs with the same title, regardless of artist ---

                // Build the outer query
                QueryBuilder<TrackEntity, Long> outerQb = dao.queryBuilder();

                // Build the inner subquery to find titles that appear more than once
                QueryBuilder<TrackEntity, Long> subQb = dao.queryBuilder();
                subQb.selectRaw("normalizedTitle"); // Select the column for the IN clause
                subQb.groupBy("normalizedTitle");
                subQb.having("COUNT(*) > 1");

                // Use the SubQuery in the outer query's WHERE IN (...) clause
                outerQb.where().in("normalizedTitle", subQb);
                outerQb.orderBy("normalizedTitle", true).orderBy("normalizedArtist", true);

                List<TrackEntity> results =  outerQb.query();
                return new ArrayList<>(results);
            }

        } catch (Exception e) {
            Log.e(TAG, "findSimilarSongs Error: " + e.getMessage(), e);
            // Return an empty list instead of null to prevent NullPointerExceptions
            return new ArrayList<>();
        }
    }

    public List<String> getGenres() {
        try {
            List<String> list = new ArrayList<>();
            Dao<TrackEntity, ?> dao = getMusicTagDao();
            QueryBuilder<TrackEntity, ?> builder = dao.queryBuilder();
            builder.selectRaw("distinct genre");
            try (GenericRawResults<String[]> results = dao.queryRaw(builder.prepareStatementString())) {
                for (String[] vals : results.getResults()) {
                    list.add(vals[0]);
                }
            }
            return list;
        } catch (Exception e) {
            Log.e(TAG,"getGenres: "+e.getMessage());
            return EMPTY_STRING_LIST;
        }
    }

    public List<Track> getGenresWithChildrenCount() {
        try {
            //select grouping, count(id) from musictag group by grouping
            List<Track> list = new ArrayList<>();
            Dao<TrackEntity, ?> dao = getMusicTagDao();
            QueryBuilder<TrackEntity, ?> builder = dao.queryBuilder();
            builder.selectRaw("genre, count(id)");
            builder.groupBy("genre");
            try (GenericRawResults<String[]> results = dao.queryRaw(builder.prepareStatementString())) {
                for (String[] vals : results.getResults()) {
                    AudioTag group = new AudioTag(SearchCriteria.TYPE.GENRE, vals[0]);
                    //  group.setName(vals[0]);
                    group.setChildCount(StringUtils.toLong(vals[1]));
                    if (vals[0] == null) {
                        group.setTitle("_NULL");
                    } else if (StringUtils.isEmpty(vals[0])) {
                        group.setTitle("_EMPTY");
                    }
                    list.add(group);
                }
            }
            return list;
        } catch (Exception e) {
            Log.e(TAG,"genre: "+e.getMessage());
            return EMPTY_LIST;
        }
    }

    public List<String> getPublishers() {
        try {
            List<String> list = new ArrayList<>();
            Dao<TrackEntity, ?> dao = getMusicTagDao();
            QueryBuilder<TrackEntity, ?> builder = dao.queryBuilder();
            builder.selectRaw("distinct publisher");
            try (GenericRawResults<String[]> results = dao.queryRaw(builder.prepareStatementString())) {
                for (String[] vals : results.getResults()) {
                    list.add(vals[0]);
                }
            }
            return list;
        } catch (Exception e) {
            Log.e(TAG,"getPublishers: "+e.getMessage());
            return EMPTY_STRING_LIST;
        }
    }

    public List<String> getArtists() {
        try {
            List<String> list = new ArrayList<>();
            Dao<TrackEntity, ?> dao = getMusicTagDao();
            QueryBuilder<TrackEntity, ?> builder = dao.queryBuilder();
            builder.selectRaw("distinct artist");
            try (GenericRawResults<String[]> results = dao.queryRaw(builder.prepareStatementString())) {
                for (String[] vals : results.getResults()) {
                    list.add(vals[0]);
                }
            }
            return list;
        } catch (Exception e) {
            Log.e(TAG,"getArtists: "+e.getMessage());
            return EMPTY_STRING_LIST;
        }
    }

    public TrackEntity findById(long id) {
        try {
            Dao<TrackEntity, Long> dao = getMusicTagDao();
            return dao.queryForId(id);
        } catch (SQLException e) {
            return null;
        }
    }

    public List<Track> findByGroupingAndArtist(String grouping, String artist)  {
        try {
            Dao<TrackEntity, ?> dao = getMusicTagDao();
            QueryBuilder<TrackEntity, ?> builder = dao.queryBuilder();
            Where<TrackEntity, ?> where = builder.where();
            if(EMPTY.equals(grouping)) {
                where.isNull("grouping").or().eq("grouping", "");
            }else {
                where.eq("grouping", grouping);
            }
            if(EMPTY.equals(artist)) {
                where.and().isNull("artist").or().eq("artist", "");
            }else {
                where.and().eq("artist",artist);
            }
            List<TrackEntity> results = builder.groupBy("title").groupBy("artist").query();
            return new ArrayList<>(results);
        } catch (SQLException e) {
            return EMPTY_LIST;
        }
    }

    public List<Track> findByIdRanges(long idRange1, long idRange2) {
        // 1000 - 1100
        try {
            Dao<TrackEntity, ?> dao = getMusicTagDao();
            QueryBuilder<TrackEntity, ?> builder = dao.queryBuilder();
            builder.where().raw("id BETWEEN "+idRange1+" AND "+idRange2);
            List<TrackEntity> results = builder.query();
            return  new ArrayList<>(results);
        } catch (SQLException e) {
            return EMPTY_LIST;
        }
    }

    public List<Track> findNoEmbedCoverArtSong() {
        try {
            Dao<TrackEntity, ?> dao = getMusicTagDao();
            QueryBuilder<TrackEntity, ?> builder = dao.queryBuilder();
            builder.where().raw("coverartMime is null or coverartMime = '' order by title, artist");
            List<TrackEntity> results = builder.query();
            return  new ArrayList<>(results);
        } catch (SQLException e) {
            return EMPTY_LIST;
        }
    }

    public List<Track> getArtistWithChildrenCount() {
        try {
            //select grouping, count(id) from musictag group by grouping
            Map<String, AudioTag> list = new HashMap<>();
            Dao<TrackEntity, ?> dao = getMusicTagDao();
            QueryBuilder<TrackEntity, ?> builder = dao.queryBuilder();
            builder.selectRaw("artist, count(id)");
            builder.groupBy("artist");
            try (GenericRawResults<String[]> results = dao.queryRaw(builder.prepareStatementString())) {
                for (String[] vals : results.getResults()) {
                    String artists = trimToEmpty(vals[0]);
                    String[] artistArray = artists.split(ARTIST_SEP, -1);
                    for (String artist : artistArray) {
                        artist = trimToEmpty(artist);
                        artist = isEmpty(artist) ? NONE : artist;
                        AudioTag group;
                        if (list.containsKey(artist)) {
                            group = list.get(artist);
                        } else {
                            group = new AudioTag(SearchCriteria.TYPE.ARTIST, artist);
                        }
                        if (group != null) {
                            group.setChildCount(group.getChildCount() + StringUtils.toLong(vals[1]));
                            list.put(artist, group);
                        }
                    }
                }
            }
            return new ArrayList<>(list.values());
        } catch (Exception e) {
            Log.e(TAG,"artist: "+e.getMessage());
            return EMPTY_LIST;
        }
    }

    public List<Track> getAlbumAndArtistWithChildrenCount() {
        try {
            //select grouping, count(id) from musictag group by grouping
            List<Track> list = new ArrayList<>();
            Dao<TrackEntity, ?> dao = getMusicTagDao();
            QueryBuilder<TrackEntity, ?> builder = dao.queryBuilder();
            builder.selectRaw("album, albumartist, albumUniqueKey, count(id)");
            builder.groupByRaw("album, albumartist");
            try (GenericRawResults<String[]> results = dao.queryRaw(builder.prepareStatementString())) {
                for (String[] vals : results.getResults()) {
                    String albumArtist = vals[1];
                    String album = vals[0];
                    String count = vals[3];
                    String name;
                    if (StringUtils.isEmpty(album)) {
                        album = Constants.UNKNOWN;
                    }
                    if (StringUtils.isEmpty(albumArtist)) {
                        name = album;
                    } else if ("Various Artists".equalsIgnoreCase(albumArtist) ||
                            "Soundtrack".equalsIgnoreCase(albumArtist)) {
                        name = album;
                    } else {
                        name = album + " (by " + albumArtist + ")";
                    }
                    AudioTag group = new AudioTag(SearchCriteria.TYPE.ARTIST, name);
                    group.setUniqueKey(vals[2]);
                    group.setChildCount(StringUtils.toLong(count));
                    list.add(group);
                }
            }
            return list;
        } catch (Exception e) {
            Log.e(TAG,"album: "+e.getMessage());
            return EMPTY_LIST;
        }
    }

    public List<Track> findByArtist(String name, long firstResult, long maxResults) {
        try {
            Dao<TrackEntity, ?> dao = getMusicTagDao();
            QueryBuilder<TrackEntity, ?> builder = dao.queryBuilder();
            if(StringUtils.isEmpty(name)) {
                builder.where().isNull("artist").or().eq("artist", "");
            }else {
               // name = name.replace("'", "''");
                // Note: Use SelectArg for security instead of name.replace("'", "''")
                builder.where().like("artist", new SelectArg("%" + name + "%"));
            }
            if(firstResult>0) {
                builder.offset(firstResult);
            }
            if(maxResults>0) {
                builder.limit(maxResults);
            }
            List<TrackEntity> results = builder.groupBy("title").groupBy("artist").query();
            return  new ArrayList<>(results);
        } catch (SQLException e) {
            return EMPTY_LIST;
        }
    }

    public List<Track> findByAlbumAndAlbumArtist(String album, String albumArtist, long firstResult, long maxResults) {
        try {
            Dao<TrackEntity, ?> dao = getMusicTagDao();
            QueryBuilder<TrackEntity, ?> builder = dao.queryBuilder();
            Where<TrackEntity, ?> where = builder.where();
            if(StringUtils.isEmpty(album)) {
                where.isNull("album").or().eq("album", "");
            }else {
                where.eq("album", album.replace("'", "''"));
            }
            if(!StringUtils.isEmpty(albumArtist)) {
                where.and().eq("albumArtist", albumArtist.replace("'", "''"));
            }
            if(firstResult>0) {
                builder.offset(firstResult);
            }
            if(maxResults>0) {
                builder.limit(maxResults);
            }
            List<TrackEntity> results = builder.groupBy("title").groupBy("artist").query();
            return  new ArrayList<>(results);
        } catch (SQLException e) {
            return EMPTY_LIST;
        }
    }

    public TrackEntity findByAlbumArtFilename(String albumUniqueKey) {
        try {
            Dao<TrackEntity, ?> dao = getMusicTagDao();
            QueryBuilder<TrackEntity, ?> builder = dao.queryBuilder();
            builder.where().eq("albumArtFilename", albumUniqueKey);
            return dao.queryForFirst(builder.prepare());
        } catch (SQLException ex) {
            Log.e(TAG,"findByAlbumArtFilename", ex);
        }
        return null;
    }

    public List<Track> findByGenre(String name, long firstResult, long maxResults) {
        try {
            Dao<TrackEntity, ?> dao = getMusicTagDao();
            QueryBuilder<TrackEntity, ?> builder = dao.queryBuilder();
            if(StringUtils.isEmpty(name)) {
                builder.where().isNull("genre").or().eq("genre", "");
            }else {
                builder.where().eq("genre", name.replace("'", "''"));
            }
            if(firstResult>0) {
                builder.offset(firstResult);
            }
            if(maxResults>0) {
                builder.limit(maxResults);
            }
            List<TrackEntity> results = builder.groupBy("title").groupBy("artist").query();
            return  new ArrayList<>(results);
        } catch (SQLException e) {
            return EMPTY_LIST;
        }
    }

    @Override
    public void cleanInvalidTag() throws Exception {
        Dao<TrackEntity, Long> dao = getMusicTagDao();
        // Use an iterator to process one song at a time without loading the whole list.
        // This is extremely memory-efficient.
        try (com.j256.ormlite.dao.CloseableIterator<TrackEntity> iterator = dao.iterator()) {
            while (iterator.hasNext()) {
                TrackEntity mdata = iterator.next();
                if (!FileRepository.isMediaFileExist(mdata.getPath()) || mdata.getFileSize() == 0.0) {
                    // It's also more efficient to collect IDs and delete in a batch.
                    dao.delete(mdata);
                }
            }
        }
    }

    @Override
    public List<Track> findForPlaylist() {
        OrmLiteHelper.ORDERED_BY [] aristAlbum = {OrmLiteHelper.ORDERED_BY.TITLE, OrmLiteHelper.ORDERED_BY.ARTIST};
        return findMySongs(aristAlbum);
    }

    @Override
    public long getTotalSongs() throws SQLException {
        return getMusicTagDao().countOf();
    }

    @Override
    public long getTotalDuration() throws SQLException {
        return getMusicTagDao().countOf();
    }

    @Override
    public List<Track> findByIds(long[] ids) {
        try {
            Dao<TrackEntity, Long> musicTagDao = getMusicTagDao();
            // OrmLite's 'in' operator needs an array of Objects, not primitives
            Long[] idObjects = new Long[ids.length];
            for (int i = 0; i < ids.length; i++) {
                idObjects[i] = ids[i];
            }

            QueryBuilder<TrackEntity, Long> queryBuilder = musicTagDao.queryBuilder();
            queryBuilder.where().in("id", (Object[]) idObjects); // Cast is important
            List<TrackEntity> results = queryBuilder.query();
            return new ArrayList<>(results);
        } catch (SQLException ignored) {

        }
        return EMPTY_LIST;
    }

    @Override
    public void addToPlayingQueue(Track song) throws SQLException {
        Dao<PlayingQueue, Long> queueDao = getQueueItemDao();
        // Get the current size of the queue to determine the next position.
        // If the queue has 5 items (positions 0-4), countOf() returns 5, which is the correct next position.
        long nextPosition = queueDao.countOf();
        PlayingQueue qItem = new PlayingQueue((TrackEntity) song, nextPosition);
        queueDao.create(qItem);
    }

    @Override
    public void savePlayingQueue(List<Track> songsInContext) {
        try {
            // --- Get references to our Data Access Objects (DAOs) ---
            Dao<PlayingQueue, Long> queueDao = getQueueItemDao();

            // Use a batch task to perform all operations in a single transaction
            queueDao.callBatchTasks((Callable<Void>) () -> {
                // --- 1. Clear the entire table ---
                // This is the most direct and reliable way to clear a table.
                TableUtils.clearTable(queueDao.getConnectionSource(), PlayingQueue.class);

                // --- 2. Populate the table with the new queue items ---
                int queueIndex = 1;
                for (Track tag : songsInContext) {
                    PlayingQueue newItem = new PlayingQueue((TrackEntity) tag, queueIndex++);
                    // This create is now part of the batch transaction
                    queueDao.create(newItem);
                }
                return null;
            });
        } catch (Exception e) {
            // Handle the exception (e.g., log it)
            Log.e("DatabaseError", "Failed to rebuild queue", e);
        }
    }

    @Override
    public List<Track> getPlayingQueue() {
        try {
            // --- Get references to our Data Access Objects (DAOs) ---
            Dao<PlayingQueue, Long> queueDao = getQueueItemDao();
            List<PlayingQueue> queueList = queueDao.queryForAll();
            List<Track> list = new ArrayList<>();
            for (PlayingQueue que: queueList) {
                Track track = que.getTrack();
                if(track == null) continue;
                list.add(track);
            }
            return list;
        } catch (Exception e) {
            // Handle the exception (e.g., log it)
            Log.e("DatabaseError", "Failed to rebuild queue", e);
        }

        return Collections.emptyList();
    }

    @Override
    public void emptyPlayingQueue() {
        try {
            Dao<PlayingQueue, Long> queueDao = getQueueItemDao();

            TableUtils.clearTable(queueDao.getConnectionSource(), PlayingQueue.class);
        } catch (SQLException e) {
            // throw new RuntimeException(e);
        }
    }

    // --- Public DAO Getters ---

    public Dao<TrackEntity, Long> getMusicTagDao() throws SQLException {
        if (musicTagDao == null) {
            musicTagDao = getDao(TrackEntity.class);
        }
        return musicTagDao;
    }

    public Dao<PlayingQueue, Long> getQueueItemDao() throws SQLException {
        if (queueItemDao == null) {
            queueItemDao = getDao(PlayingQueue.class);
        }
        return queueItemDao;
    }

    @Override
    public void close() {
        super.close();
        musicTagDao = null;
        queueItemDao = null;
    }
}
