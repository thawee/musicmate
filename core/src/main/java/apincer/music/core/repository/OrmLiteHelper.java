package apincer.music.core.repository;

import static apincer.music.core.Constants.ARTIST_SEP;
import static apincer.music.core.Constants.ARTIST_SEP_SPACE;
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
import com.j256.ormlite.stmt.Where;
import com.j256.ormlite.support.ConnectionSource;
import com.j256.ormlite.table.TableUtils;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import apincer.music.core.Constants;
import apincer.music.core.database.MusicTag;
import apincer.music.core.database.Playlist;
import apincer.music.core.database.PlaylistItem;
import apincer.music.core.database.QueueItem;
import apincer.music.core.model.MusicFolder;
import apincer.music.core.utils.LogHelper;
import apincer.music.core.utils.StringUtils;

public class OrmLiteHelper extends OrmLiteSqliteOpenHelper {
    // DAO objects
    private Dao<MusicTag, Long> musicTagDao = null;
    private Dao<Playlist, Long> playlistDao = null;
    private Dao<PlaylistItem, Long> playlistItemDao = null;
    private Dao<QueueItem, Long> queueItemDao = null;

    public MusicTag findByUniqueKey(String uniqueKey) {
        try {
            Dao<MusicTag, ?> dao = getMusicTagDao();
            QueryBuilder<MusicTag, ?> builder = dao.queryBuilder();
            builder.where().eq("uniqueKey", uniqueKey);
            return dao.queryForFirst(builder.prepare());
        } catch (SQLException ex) {
            Log.e(TAG,"findByUniqueKey", ex);
        }
        return null;
    }

    public List<MusicTag> getAllMusicsForPlaylist() {
           // OrmLiteHelper.ORDERED_BY [] aristAlbum = {OrmLiteHelper.ORDERED_BY.TITLE, OrmLiteHelper.ORDERED_BY.ARTIST};
          //  return findMySongs(aristAlbum);
        List<MusicTag> list = findMySongs();
        list.sort(Comparator
                .comparing(MusicTag::getNormalizedTitle)
                .thenComparing(MusicTag::getNormalizedArtist)
        );
        return list;
    }

    public enum ORDERED_BY {ALBUM, ARTIST,TITLE}
    //Database name
    private static final String DATABASE_NAME = "apincer.musicmate.db";
    private static final String TAG = LogHelper.getTag(OrmLiteHelper.class);
    private static final int DATABASE_VERSION = 10;
    private static final List<MusicFolder> EMPTY_FOLDER_LIST = null;
    public static final List<MusicTag> EMPTY_LIST = null;
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
            TableUtils.createTable(connectionSource, MusicTag.class);
            TableUtils.createTable(connectionSource, Playlist.class);
            TableUtils.createTable(connectionSource, PlaylistItem.class);
            TableUtils.createTable(connectionSource, QueueItem.class);
        } catch (SQLException e) {
            Log.e(TAG,"onCreate", e);
        }
    }

    @Override
    public void onUpgrade(SQLiteDatabase database, ConnectionSource connectionSource, int oldVersion, int newVersion) {
        try {
            // Recreates the database when onUpgrade is called by the framework
            TableUtils.dropTable(connectionSource, MusicTag.class, true);
            TableUtils.dropTable(connectionSource, Playlist.class, true);
            TableUtils.dropTable(connectionSource, PlaylistItem.class, true);
            TableUtils.dropTable(connectionSource, QueueItem.class, true);
            onCreate(database, connectionSource);

            //clean cached directory
           // FileRepository.newInstance(context).cleanCacheCovers();
           // FileRepository.newInstance(context).cleanCacheCovers();
        } catch (SQLException e) {
            Log.e(TAG,"onUpgrade", e);
        }
    }

    @Override
    public void onDowngrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        onUpgrade(db, oldVersion, newVersion);
    }

    public List<MusicTag> findMySongs(ORDERED_BY[] orderedByList)  {
        try {
            Dao<MusicTag, ?> dao = getMusicTagDao();
            QueryBuilder<MusicTag, ?> builder = dao.queryBuilder();
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
            return builder.query();
        } catch (SQLException e) {
            return EMPTY_LIST;
        }
    }

    public List<MusicTag> findMySongs()  {
        try {
            Dao<MusicTag, ?> dao = getMusicTagDao();

            return dao.queryBuilder().orderBy("normalizedTitle", true).orderByNullsFirst("normalizedArtist", true).query();
        } catch (SQLException e) {
            return EMPTY_LIST;
        }
    }

    public List<MusicTag> findByTitle(String title)  {
        try {
            Dao<MusicTag, ?> dao =getMusicTagDao();

            QueryBuilder<MusicTag, ?> builder = dao.queryBuilder();
            String keyword = LIKE_LITERAL+title.replace("'","''")+LIKE_LITERAL;
            builder.where().like("title",keyword).or().like("path", keyword);
            return builder.query();
        } catch (SQLException e) {
            return EMPTY_LIST;
        }
    }

    public List<MusicTag> findByPath(String path)  {
        try {
            Dao<MusicTag, ?> dao = getMusicTagDao();

            QueryBuilder<MusicTag, ?> builder = dao.queryBuilder();
            builder.where().eq("path",escapeString(path));
            return builder.query();
        } catch (Exception e) {
            return EMPTY_LIST;
        }
    }

    public List<MusicTag> findInPath(String path)  {
        try {
            Dao<MusicTag, ?> dao = getMusicTagDao();

            QueryBuilder<MusicTag, ?> builder = dao.queryBuilder();
            builder.where().like("path",escapeString(path)+LIKE_LITERAL);
            builder.orderBy("path", true);
            return builder.query();
        } catch (Exception e) {
            return EMPTY_LIST;
        }
    }

    public void save(MusicTag tag)   {
        try {
            Dao<MusicTag, ?> dao = getMusicTagDao();
            dao.createOrUpdate(tag);
        } catch (SQLException e) {
            Log.e(TAG,"save", e);
        }
    }

    public List<MusicTag> getByPath(String path) {
        try {
            Dao<MusicTag, ?> dao = getMusicTagDao();

            // Escape single quotes by replacing ' with ''
            String escapedPath = escapeString(path);

            // Use a direct query that only fetches lastModified
            QueryBuilder<MusicTag, ?> qb = dao.queryBuilder();
            qb.where().eq("path", escapedPath);

            return qb.query();

           // return dao.queryForFirst(qb.prepare());

        } catch (SQLException e) {
            Log.e(TAG, "getByPath", e);
            return EMPTY_LIST; // Assume outdated if we can't check
        }
    }

    public boolean isOutdated(MusicTag tag, long lastModified) {
        return tag == null || tag.getFileLastModified() < lastModified;
    }

    // Add batch save method
    public void saveTagsBatch(List<MusicTag> tags) {
        if (tags == null || tags.isEmpty()) return;

        try {
            Dao<MusicTag, ?> dao = getMusicTagDao();

            // Use transaction for better performance
            dao.callBatchTasks(() -> {
                for (MusicTag tag : tags) {
                    dao.createOrUpdate(tag);
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

    public void delete(MusicTag tag)   {
        try {
            Dao<MusicTag, ?> dao = getMusicTagDao();
            dao.delete(tag);
        } catch (SQLException ignored) {
        }
    }

    public List<MusicTag> findRecentlyAdded(long firstResult, long maxResults)  {
        try {
            Dao<MusicTag, ?> dao = getMusicTagDao();
            QueryBuilder<MusicTag, ?> builder = dao.queryBuilder();
            builder.where().eq("mmManaged",false);
            if(firstResult>0) {
                builder.offset(firstResult);
            }
            if(maxResults>0) {
                builder.limit(maxResults);
            }

            //List<MusicTag> masterList = builder.orderByNullsFirst("title", true).orderByNullsFirst("artist", true).query();
            List<MusicTag> masterList = builder.query();
            masterList.sort(Comparator
                    .comparing(MusicTag::getNormalizedTitle)
                    .thenComparing(MusicTag::getNormalizedArtist)
            );
            return masterList;
        } catch (SQLException e) {
            return EMPTY_LIST;
        }
    }

    public List<MusicTag> findMyNoDRMeterSongs()  {
        try {
            Dao<MusicTag, ?> dao = getMusicTagDao();
            QueryBuilder<MusicTag, ?> builder = dao.queryBuilder();
            builder.where().eq("drScore",0)
                    .or().eq("dynamicRange", 0);
            return builder.orderByNullsFirst("title", true).orderByNullsFirst("artist", true).query();
        } catch (SQLException e) {
            return EMPTY_LIST;
        }
    }

    public List<MusicTag> findByGenre(String genre)   {
        try {
            Dao<MusicTag, ?> dao = getMusicTagDao();
            QueryBuilder<MusicTag, ?> builder = dao.queryBuilder();
            if(StringUtils.isEmpty(genre)) {
                builder.where().isNull("genre").or().eq("genre", "");
            }else {
                builder.where().eq("genre", genre.replace("'", "''"));
            }
            return builder.groupBy("title").groupBy("artist").query();
        } catch (SQLException e) {
            return EMPTY_LIST;
        }
    }

    public List<MusicTag> findByGrouping(String grouping, long firstResult, long maxResults)   {
        try {
            Dao<MusicTag, ?> dao = getMusicTagDao();
            QueryBuilder<MusicTag, ?> builder = dao.queryBuilder();
            if(StringUtils.isEmpty(grouping)) {
                builder.where().isNull("grouping").or().eq("grouping", "");
            }else {
                builder.where().eq("grouping", grouping.replace("'", "''"));
            }
            if(firstResult>0) {
                builder.offset(firstResult);
            }
            if(maxResults>0) {
                builder.limit(maxResults);
            }
            return builder.groupBy("title").groupBy("artist").query();
        } catch (SQLException e) {
            return EMPTY_LIST;
        }

    }

    public List<MusicTag> findHiRes(long firstResult, long maxResults)   {
        try {
            Dao<MusicTag, ?> dao = getMusicTagDao();
            QueryBuilder<MusicTag, ?> builder = dao.queryBuilder();
            builder.where().raw("audioEncoding in ('alac', 'flac','aiff', 'wave', 'wav') and audioBitsDepth >= 24 and audioSampleRate >= 96000 and qualityInd not like 'MQA%'");
            if(firstResult>0) {
                builder.offset(firstResult);
            }
            if(maxResults>0) {
                builder.limit(maxResults);
            }
            return builder.query();
        } catch (SQLException e) {
            return EMPTY_LIST;
        }

    }

    public List<MusicTag> findHighQuality(long firstResult, long maxResults)   {
        try {
            Dao<MusicTag, ?> dao = getMusicTagDao();
            QueryBuilder<MusicTag, ?> builder = dao.queryBuilder();
            builder.where().raw(" audioEncoding in ('aac', 'mpeg') ");
            if(firstResult>0) {
                builder.offset(firstResult);
            }
            if(maxResults>0) {
                builder.limit(maxResults);
            }
            return builder.query();
        } catch (SQLException e) {
            return EMPTY_LIST;
        }

    }

    public List<MusicTag> findMQASongs(long firstResult, long maxResults) {
        try {
            Dao<MusicTag, ?> dao = getMusicTagDao();
            QueryBuilder<MusicTag, ?> builder = dao.queryBuilder();
            builder.where().raw("qualityInd like 'MQA%' order by title, artist");
            if(firstResult>0) {
                builder.offset(firstResult);
            }
            if(maxResults>0) {
                builder.limit(maxResults);
            }
            return builder.query();
        } catch (SQLException e) {
            return EMPTY_LIST;
        }
    }

    public List<MusicTag> findDSDSongs(long firstResult, long maxResults) {
        try {
            Dao<MusicTag, ?> dao = getMusicTagDao();
            QueryBuilder<MusicTag, ?> builder = dao.queryBuilder();
            builder.where().raw("audioEncoding in ('dsd', 'dff') order by title, artist");
            if(firstResult>0) {
                builder.offset(firstResult);
            }
            if(maxResults>0) {
                builder.limit(maxResults);
            }
            return builder.query();
        } catch (SQLException e) {
            return EMPTY_LIST;
        }
    }

    public List<MusicTag> findByMediaQuality(String keyword) {
        try {
            Dao<MusicTag, ?> dao = getMusicTagDao();
            QueryBuilder<MusicTag, ?> builder = dao.queryBuilder();
            if (isEmpty(keyword)) {
                builder.where().raw("mediaQuality is null order by title, artist");
                return builder.query();
            } else {
                builder.where().raw("mediaQuality="+escapeString(keyword)+" order by title, artist");
                return builder.query();
            }
        } catch (SQLException e) {
            return EMPTY_LIST;
        }
    }

    public List<MusicTag> findByPublisher(String keyword) {
        try {
            Dao<MusicTag, ?> dao = getMusicTagDao();
            QueryBuilder<MusicTag, ?> builder = dao.queryBuilder();
            if (isEmpty(keyword) || Constants.UNKNOWN.equals(keyword)) {
                builder.where().raw("publisher is null order by title, artist");
                return builder.query();
            } else {
                builder.where().raw("publisher="+escapeString(keyword)+" order by title, artist");
                return builder.query();
            }
        } catch (SQLException e) {
            return EMPTY_LIST;
        }
    }

    public List<MusicTag> findLosslessSong(long firstResult, long maxResults) {
        try {
            Dao<MusicTag, ?> dao = getMusicTagDao();
            QueryBuilder<MusicTag, ?> builder = dao.queryBuilder();
            builder.where().raw("audioEncoding in ('flac','alac','aiff','wave','wav') and audioSampleRate < 96000 and qualityInd not like 'MQA%' order by title, artist");
            if(firstResult>0) {
                builder.offset(firstResult);
            }
            if(maxResults>0) {
                builder.limit(maxResults);
            }
            return builder.query();
        } catch (SQLException e) {
            return EMPTY_LIST;
        }
    }

    public List<MusicTag> findByKeyword(String keyword) {
        try {
            Dao<MusicTag, ?> dao = getMusicTagDao();
            keyword = "'"+LIKE_LITERAL+keyword.replace("'","''")+LIKE_LITERAL+"'";
            QueryBuilder<MusicTag, ?> builder = dao.queryBuilder();
            builder.where().raw("title like "+keyword+" or artist like "+keyword +" or album like "+keyword);
            return builder.query();
        } catch (SQLException e) {
            return EMPTY_LIST;
        }
    }

    // In your OrmLiteHelper.java class
    public List<MusicTag> findSimilarSongs(boolean excludeArtist) {
        try {
            Dao<MusicTag, Long> dao = getMusicTagDao();

            if (excludeArtist) {
                // --- Case 1: Find songs with the same title, regardless of artist ---

                // Build the outer query
                QueryBuilder<MusicTag, Long> outerQb = dao.queryBuilder();

                // Build the inner subquery to find titles that appear more than once
                QueryBuilder<MusicTag, Long> subQb = dao.queryBuilder();
                subQb.selectRaw("normalizedTitle"); // Select the column for the IN clause
                subQb.groupBy("normalizedTitle");
                subQb.having("COUNT(*) > 1");

                // Use the SubQuery in the outer query's WHERE IN (...) clause
                outerQb.where().in("normalizedTitle", subQb);
                outerQb.orderBy("normalizedTitle", true).orderBy("normalizedArtist", true);

                return outerQb.query();

            } else {
                // --- Case 2: Find songs with the same title AND artist ---
                // This is more complex and best handled with a single, clear raw query.

                String rawQuery = "SELECT * FROM musictag WHERE (normalizedTitle, normalizedArtist) " +
                        "IN (SELECT normalizedTitle, normalizedArtist FROM musictag " +
                        "GROUP BY normalizedTitle, normalizedArtist HAVING COUNT(*) > 1) " +
                        "ORDER BY normalizedTitle, normalizedArtist";

                GenericRawResults<MusicTag> rawResults = dao.queryRaw(rawQuery, dao.getRawRowMapper());
                // Use getResults() which is the correct method to get the list
                return rawResults.getResults();
            }

        } catch (Exception e) {
            Log.e(TAG, "findSimilarSongs Error: " + e.getMessage(), e);
            // Return an empty list instead of null to prevent NullPointerExceptions
            return new ArrayList<>();
        }
    }

    public List<MusicTag> findSimilarSongsOld(boolean excludeArtist) {
        try {
            List<MusicTag> list =  getMusicTagDao().queryForAll();

            Map<String, List<MusicTag>> consolidatedMap = new HashMap<>();
            for (MusicTag song : list) {
                String key = song.getNormalizedTitle();// + "||" + song.getNormalizedTitle();
                if(!excludeArtist) {
                    key = key  + "||" + song.getNormalizedArtist();
                }

                // This is a shorter way to get the list for a key or create a new one
                List<MusicTag> group = consolidatedMap.computeIfAbsent(key, k -> new ArrayList<>());
                group.add(song);
            }

            List<MusicTag> masterList = new ArrayList<>();
            for (List<MusicTag> duplicateGroup : consolidatedMap.values()) {
                if(duplicateGroup.size()>1) {
                    masterList.addAll(duplicateGroup);
                }
            }

            // --- 2. Sort the final list of master records ---
            // This sorts first by artist (A-Z), and then by title (A-Z) for artists with the same name.
            masterList.sort(Comparator
                    .comparing(MusicTag::getNormalizedTitle)
                    .thenComparing(MusicTag::getNormalizedArtist)
            );

            return masterList;
        } catch (Exception e) {
            Log.e(TAG, "findSimilarSongs: " + e.getMessage());
            return EMPTY_LIST;
        }
    }

    public List<String> getGenres() {
        try {
            List<String> list = new ArrayList<>();
            Dao<MusicTag, ?> dao = getMusicTagDao();
            QueryBuilder<MusicTag, ?> builder = dao.queryBuilder();
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

    public List<String> getGrouping() {
        try {
            List<String> list = new ArrayList<>();
            Dao<MusicTag, ?> dao = getMusicTagDao();
            QueryBuilder<MusicTag, ?> builder = dao.queryBuilder();
            builder.selectRaw("distinct grouping");
            try (GenericRawResults<String[]> results = dao.queryRaw(builder.prepareStatementString())) {
                //return Arrays.asList(result);
                for (String[] vals : results.getResults()) {
                    list.add(vals[0]);
                }
            }
            return list;
        } catch (Exception e) {
            Log.e(TAG,"getGrouping: "+e.getMessage());
            return EMPTY_STRING_LIST;
        }
    }

    public List<MusicFolder> getGroupingWithChildrenCount() {
        try {
            //select grouping, count(id) from musictag group by grouping
            List<MusicFolder> list = new ArrayList<>();
            Dao<MusicTag, ?> dao = getMusicTagDao();
            QueryBuilder<MusicTag, ?> builder = dao.queryBuilder();
            builder.selectRaw("grouping, count(id)");
            builder.groupBy("grouping");
            try (GenericRawResults<String[]> results = dao.queryRaw(builder.prepareStatementString())) {
                for (String[] vals : results.getResults()) {
                    MusicFolder group = new MusicFolder(vals[0]);
                    group.setChildCount(StringUtils.toLong(vals[1]));
                    if (vals[0] == null) {
                        group.setName("_NULL");
                    } else if (StringUtils.isEmpty(vals[0])) {
                        group.setName("_EMPTY");
                    }
                    list.add(group);
                }
            }
            return list;
        } catch (Exception e) {
            Log.e(TAG,"getGrouping: "+e.getMessage());
            return EMPTY_FOLDER_LIST;
        }
    }

    public List<MusicFolder> getGenresWithChildrenCount() {
        try {
            //select grouping, count(id) from musictag group by grouping
            List<MusicFolder> list = new ArrayList<>();
            Dao<MusicTag, ?> dao = getMusicTagDao();
            QueryBuilder<MusicTag, ?> builder = dao.queryBuilder();
            builder.selectRaw("genre, count(id)");
            builder.groupBy("genre");
            try (GenericRawResults<String[]> results = dao.queryRaw(builder.prepareStatementString())) {
                for (String[] vals : results.getResults()) {
                    MusicFolder group = new MusicFolder(vals[0]);
                    //  group.setName(vals[0]);
                    group.setChildCount(StringUtils.toLong(vals[1]));
                    if (vals[0] == null) {
                        group.setName("_NULL");
                    } else if (StringUtils.isEmpty(vals[0])) {
                        group.setName("_EMPTY");
                    }
                    list.add(group);
                }
            }
            return list;
        } catch (Exception e) {
            Log.e(TAG,"genre: "+e.getMessage());
            return EMPTY_FOLDER_LIST;
        }
    }

    public List<String> getPublishers() {
        try {
            List<String> list = new ArrayList<>();
            Dao<MusicTag, ?> dao = getMusicTagDao();
            QueryBuilder<MusicTag, ?> builder = dao.queryBuilder();
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
            Dao<MusicTag, ?> dao = getMusicTagDao();
            QueryBuilder<MusicTag, ?> builder = dao.queryBuilder();
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

    public  MusicTag findById(long id) {
        try {
            Dao<MusicTag, Long> dao = getMusicTagDao();
            return dao.queryForId(id);
        } catch (SQLException e) {
            return null;
        }
    }

    public List<MusicTag> findByGroupingAndArtist(String grouping, String artist)  {
        try {
            Dao<MusicTag, ?> dao = getMusicTagDao();
            QueryBuilder<MusicTag, ?> builder = dao.queryBuilder();
            Where<MusicTag, ?> where = builder.where();
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
            return builder.groupBy("title").groupBy("artist").query();
        } catch (SQLException e) {
            return EMPTY_LIST;
        }
    }

    public List<MusicTag> findByIdRanges(long idRange1, long idRange2) {
        // 1000 - 1100
        try {
            Dao<MusicTag, ?> dao = getMusicTagDao();
            QueryBuilder<MusicTag, ?> builder = dao.queryBuilder();
            builder.where().raw("id BETWEEN "+idRange1+" AND "+idRange2);
            return builder.query();
        } catch (SQLException e) {
            return EMPTY_LIST;
        }
    }

    public List<MusicTag> findNoEmbedCoverArtSong() {
        try {
            Dao<MusicTag, ?> dao = getMusicTagDao();
            QueryBuilder<MusicTag, ?> builder = dao.queryBuilder();
            builder.where().raw("coverartMime is null or coverartMime = '' order by title, artist");
            return builder.query();
        } catch (SQLException e) {
            return EMPTY_LIST;
        }
    }

    public List<MusicFolder> getArtistWithChildrenCount() {
        try {
            //select grouping, count(id) from musictag group by grouping
            Map<String, MusicFolder> list = new HashMap<>();
            Dao<MusicTag, ?> dao = getMusicTagDao();
            QueryBuilder<MusicTag, ?> builder = dao.queryBuilder();
            builder.selectRaw("artist, count(id)");
            builder.groupBy("artist");
            try (GenericRawResults<String[]> results = dao.queryRaw(builder.prepareStatementString())) {
                for (String[] vals : results.getResults()) {
                    String artists = trimToEmpty(vals[0]);
                    String[] artistArray = artists.split(ARTIST_SEP, -1);
                    for (String artist : artistArray) {
                        artist = trimToEmpty(artist);
                        artist = isEmpty(artist) ? NONE : artist;
                        MusicFolder group;
                        if (list.containsKey(artist)) {
                            group = list.get(artist);
                        } else {
                            group = new MusicFolder(artist);
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
            return EMPTY_FOLDER_LIST;
        }
    }

    public List<MusicFolder> getAlbumAndArtistWithChildrenCount() {
        try {
            //select grouping, count(id) from musictag group by grouping
            List<MusicFolder> list = new ArrayList<>();
            Dao<MusicTag, ?> dao = getMusicTagDao();
            QueryBuilder<MusicTag, ?> builder = dao.queryBuilder();
            builder.selectRaw("album, albumartist, albumUniqueKey, count(id)");
            builder.groupByRaw("album, albumartist");
            try (GenericRawResults<String[]> results = dao.queryRaw(builder.prepareStatementString())) {
                for (String[] vals : results.getResults()) {
                    String albumArtist = vals[1];
                    String album = vals[0];
                    String count = vals[3];
                    String name = album;
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
                    MusicFolder group = new MusicFolder(name);
                    group.setUniqueKey(vals[2]);
                    group.setChildCount(StringUtils.toLong(count));
                    list.add(group);
                }
            }
            return list;
        } catch (Exception e) {
            Log.e(TAG,"album: "+e.getMessage());
            return EMPTY_FOLDER_LIST;
        }
    }

    public List<MusicTag> findByArtist(String name, long firstResult, long maxResults) {
        try {
            Dao<MusicTag, ?> dao = getMusicTagDao();
            QueryBuilder<MusicTag, ?> builder = dao.queryBuilder();
            if(StringUtils.isEmpty(name)) {
                builder.where().isNull("artist").or().eq("artist", "");
            }else {
                name = name.replace("'", "''");
                builder.where().eq("artist", name).or()
                        .like("artist",name+ARTIST_SEP+LIKE_LITERAL).or()
                        .like("artist",name+ARTIST_SEP_SPACE+LIKE_LITERAL).or()
                        .like("artist", LIKE_LITERAL+ARTIST_SEP+name).or()
                        .like("artist", LIKE_LITERAL+ARTIST_SEP_SPACE+name);
            }
            if(firstResult>0) {
                builder.offset(firstResult);
            }
            if(maxResults>0) {
                builder.limit(maxResults);
            }
            return builder.groupBy("title").groupBy("artist").query();
        } catch (SQLException e) {
            return EMPTY_LIST;
        }
    }

    public List<MusicTag> findByAlbumAndAlbumArtist(String album, String albumArtist, long firstResult, long maxResults) {
        try {
            Dao<MusicTag, ?> dao = getMusicTagDao();
            QueryBuilder<MusicTag, ?> builder = dao.queryBuilder();
            Where<MusicTag, ?> where = builder.where();
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
            return builder.groupBy("title").groupBy("artist").query();
        } catch (SQLException e) {
            return EMPTY_LIST;
        }
    }

    public MusicTag findByAlbumArtFilename(String albumUniqueKey) {
        try {
            Dao<MusicTag, ?> dao = getMusicTagDao();
            QueryBuilder<MusicTag, ?> builder = dao.queryBuilder();
            builder.where().eq("albumArtFilename", albumUniqueKey);
            return dao.queryForFirst(builder.prepare());
        } catch (SQLException ex) {
            Log.e(TAG,"findByAlbumArtFilename", ex);
        }
        return null;
    }

    public List<MusicTag> findByGenre(String name, long firstResult, long maxResults) {
        try {
            Dao<MusicTag, ?> dao = getMusicTagDao();
            QueryBuilder<MusicTag, ?> builder = dao.queryBuilder();
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
            return builder.groupBy("title").groupBy("artist").query();
        } catch (SQLException e) {
            return EMPTY_LIST;
        }
    }

    // --- Public DAO Getters ---

    public Dao<MusicTag, Long> getMusicTagDao() throws SQLException {
        if (musicTagDao == null) {
            musicTagDao = getDao(MusicTag.class);
        }
        return musicTagDao;
    }

    public Dao<Playlist, Long> getPlaylistDao() throws SQLException {
        if (playlistDao == null) {
            playlistDao = getDao(Playlist.class);
        }
        return playlistDao;
    }

    public Dao<PlaylistItem, Long> getPlaylistItemDao() throws SQLException {
        if (playlistItemDao == null) {
            playlistItemDao = getDao(PlaylistItem.class);
        }
        return playlistItemDao;
    }

    public Dao<QueueItem, Long> getQueueItemDao() throws SQLException {
        if (queueItemDao == null) {
            queueItemDao = getDao(QueueItem.class);
        }
        return queueItemDao;
    }

    @Override
    public void close() {
        super.close();
        musicTagDao = null;
        playlistDao = null;
        playlistItemDao = null;
        queueItemDao = null;
    }
}
