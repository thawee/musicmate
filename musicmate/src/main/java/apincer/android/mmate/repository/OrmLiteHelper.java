package apincer.android.mmate.repository;

import static apincer.android.mmate.Constants.IND_RESAMPLED_BAD;
import static apincer.android.mmate.Constants.IND_UPSCALED_BAD;
import static apincer.android.mmate.utils.StringUtils.EMPTY;
import static apincer.android.mmate.utils.StringUtils.isEmpty;

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
import java.util.List;

import apincer.android.mmate.Constants;
import apincer.android.mmate.utils.LogHelper;
import apincer.android.mmate.utils.StringUtils;

public class OrmLiteHelper extends OrmLiteSqliteOpenHelper {
    //Database name
    private static final String DATABASE_NAME = "apincer.musicmate.db";
    private static final String TAG = LogHelper.getTag(OrmLiteHelper.class);
    //Version of the database. Changing the version will call {@Link OrmLite.onUpgrade}
    private static final int DATABASE_VERSION = 6;
    private static final List<MusicFolder> EMPTY_FOLDER_LIST = null;
    public static final List<MusicTag> EMPTY_LIST = null;
    private static final List<String> EMPTY_STRING_LIST = null;

    public OrmLiteHelper(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION) ;//,
                // R.raw.ormlite_config is a reference to the ormlite_config2.txt file in the
                // /res/raw/ directory of this project
              //  R.raw.ormlite_config);
    }
    @Override
    public void onCreate(SQLiteDatabase database, ConnectionSource connectionSource) {
        try {
            // creates the database table
            TableUtils.createTable(connectionSource, MusicTag.class);
        } catch (SQLException e) {
            Log.e(TAG,"onCreate", e);
        }
    }

    @Override
    public void onUpgrade(SQLiteDatabase database, ConnectionSource connectionSource, int oldVersion, int newVersion) {
        try {
            // Recreates the database when onUpgrade is called by the framework
            TableUtils.dropTable(connectionSource, MusicTag.class, false);
            onCreate(database, connectionSource);

        } catch (java.sql.SQLException e) {
            Log.e(TAG,"onUpgrade", e);
        }
    }

    public List<MusicTag> findMySongs()  {
        try {
            Dao<MusicTag, ?> dao = getDao(MusicTag.class);

            return dao.queryBuilder().orderBy("title", true).orderByNullsFirst("artist", true).query();
        } catch (SQLException e) {
            return EMPTY_LIST;
        }
    }

    public List<MusicTag> findByTitle(String title)  {
        try {
            Dao<MusicTag, ?> dao = getDao(MusicTag.class);

            QueryBuilder<MusicTag, ?> builder = dao.queryBuilder();
            String keyword = "%"+title.replace("'","''")+"%";
            builder.where().like("title",keyword).or().like("path", keyword);
            return builder.query();
        } catch (SQLException e) {
            return EMPTY_LIST;
        }
    }

    public List<MusicTag> findByPath(String path)  {
        try {
            Dao<MusicTag, ?> dao = getDao(MusicTag.class);

            QueryBuilder<MusicTag, ?> builder = dao.queryBuilder();
            builder.where().eq("path",escapeString(path));
            return builder.query();
        } catch (Exception e) {
            return EMPTY_LIST;
        }
    }

    public List<MusicTag> findInPath(String path)  {
        try {
            Dao<MusicTag, ?> dao = getDao(MusicTag.class);

            QueryBuilder<MusicTag, ?> builder = dao.queryBuilder();
            builder.where().like("path",escapeString(path)+"%");
            return builder.query();
        } catch (Exception e) {
            return EMPTY_LIST;
        }
    }

    public void save(MusicTag tag)   {
        try {
            Dao<MusicTag, ?> dao = getDao(MusicTag.class);
            //if(dao.queryForEq("uniqueKey", tag.getUniqueKey()).isEmpty()) {
            if(tag.id ==0) {
                dao.create(tag);
            }else {
                dao.update(tag);
            }
        } catch (SQLException e) {
            Log.e(TAG,"save", e);
        }
    }

    private String escapeString(String text) {
        return text.replace("'","''");
    }

    public void delete(MusicTag tag)   {
        try {
            Dao<MusicTag, ?> dao = getDao(MusicTag.class);
            dao.delete(tag);
        } catch (SQLException ignored) {
        }
    }

    public List<MusicTag> findMyIncomingSongs()  {
        try {
            Dao<MusicTag, ?> dao = getDao(MusicTag.class);
            QueryBuilder<MusicTag, ?> builder = dao.queryBuilder();
            builder.where().eq("mmManaged",false);
            return builder.orderByNullsFirst("title", true).orderByNullsFirst("artist", true).query();
        } catch (SQLException e) {
            return EMPTY_LIST;
        }
    }

    public List<MusicTag> findMyNoDRMeterSongs()  {
        try {
            Dao<MusicTag, ?> dao = getDao(MusicTag.class);
            QueryBuilder<MusicTag, ?> builder = dao.queryBuilder();
            builder.where().eq("drScore",0).or()
                    .eq("upscaledInd", 0).or()
                    .eq("resampledInd", 0);
            return builder.orderByNullsFirst("title", true).orderByNullsFirst("artist", true).query();
        } catch (SQLException e) {
            return EMPTY_LIST;
        }
    }

    //@Query("Select * from tag where fileSizeRatioaudioBitRate/(audioBitsDepth*audioSampleRate*audioChannels) <= 0.36")
    public List<MusicTag> findMyBrokenSongs()   {
        try {
            Dao<MusicTag, ?> dao = getDao(MusicTag.class);
            QueryBuilder<MusicTag, ?> builder = dao.queryBuilder();
            builder.where().eq("mediaQuality", Constants.QUALITY_BAD).or()
                    .eq("upscaledInd", IND_UPSCALED_BAD).or()
                    .eq("resampledInd", IND_RESAMPLED_BAD);
            builder.orderBy("title", true);
          //  String bad = "mediaQuality = '"+Constants.QUALITY_BAD+"' ";
         //   builder.where().raw(bad+" OR (fileSize < 5120 or (dynamicRange>0 AND (dynamicRange <= "+MIN_SPL_16BIT_IN_DB+" AND audioBitsDepth<=16) OR (dynamicRange <= "+MIN_SPL_24BIT_IN_DB+" AND audioBitsDepth >= 24))) order by title");

            //  builder.where().raw(bad+" OR (fileSize < 5120 or (dynamicRange>0 AND (dynamicRange <= "+MIN_SPL_16BIT_IN_DB+" AND audioBitsDepth<=16) OR (dynamicRange <= "+MIN_SPL_24BIT_IN_DB+" AND audioBitsDepth >= 24))) order by title");
            return builder.query();
        } catch (SQLException e) {
            return EMPTY_LIST;
        }
    }

    public List<MusicTag> findByGenre(String genre)   {
        try {
            Dao<MusicTag, ?> dao = getDao(MusicTag.class);
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

    public List<MusicTag> findByGrouping(String grouping)   {
        try {
            Dao<MusicTag, ?> dao = getDao(MusicTag.class);
            QueryBuilder<MusicTag, ?> builder = dao.queryBuilder();
            if(StringUtils.isEmpty(grouping)) {
                builder.where().isNull("grouping").or().eq("grouping", "");
            }else {
                builder.where().eq("grouping", grouping.replace("'", "''"));
            }
            return builder.groupBy("title").groupBy("artist").query();
        } catch (SQLException e) {
            return EMPTY_LIST;
        }

    }

    public List<MusicTag> findHiRes()   {
        try {
            Dao<MusicTag, ?> dao = getDao(MusicTag.class);
            QueryBuilder<MusicTag, ?> builder = dao.queryBuilder();
            builder.where().raw("audioEncoding in ('alac', 'flac','aiff', 'wave', 'wav') and audioBitsDepth >= 24 and audioSampleRate >= 96000 and mqaInd not like 'MQA%'");
            return builder.query();
        } catch (SQLException e) {
            return EMPTY_LIST;
        }

    }

    public List<MusicTag> findHighQuality()   {
        try {
            Dao<MusicTag, ?> dao = getDao(MusicTag.class);
            QueryBuilder<MusicTag, ?> builder = dao.queryBuilder();
            builder.where().raw(" audioEncoding in ('aac', 'mpeg') ");
            return builder.query();
        } catch (SQLException e) {
            return EMPTY_LIST;
        }

    }

    public List<MusicTag> findMQASongs() {
        try {
            Dao<MusicTag, ?> dao = getDao(MusicTag.class);
            QueryBuilder<MusicTag, ?> builder = dao.queryBuilder();
            builder.where().raw("mqaInd like 'MQA%' order by title, artist");
            return builder.query();
        } catch (SQLException e) {
            return EMPTY_LIST;
        }
    }

    public List<MusicTag> findDSDSongs() {
        try {
            Dao<MusicTag, ?> dao = getDao(MusicTag.class);
            QueryBuilder<MusicTag, ?> builder = dao.queryBuilder();
           // builder.where().raw("audioBitsDepth=1 order by title, artist");
            builder.where().raw("audioEncoding in ('dsd', 'dff') order by title, artist");
            return builder.query();
        } catch (SQLException e) {
            return EMPTY_LIST;
        }
    }

    public List<MusicTag> findByMediaQuality(String keyword) {
        try {
            Dao<MusicTag, ?> dao = getDao(MusicTag.class);
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
            Dao<MusicTag, ?> dao = getDao(MusicTag.class);
            QueryBuilder<MusicTag, ?> builder = dao.queryBuilder();
            if (isEmpty(keyword) || Constants.UNKNOWN_PUBLISHER.equals(keyword)) {
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

    public List<MusicTag> findLosslessSong() {
        try {
            Dao<MusicTag, ?> dao = getDao(MusicTag.class);
            QueryBuilder<MusicTag, ?> builder = dao.queryBuilder();
            builder.where().raw("audioEncoding in ('flac','alac','aiff','wave','wav') and audioSampleRate < 96000 and mqaInd not like 'MQA%' order by title, artist");
            return builder.query();
        } catch (SQLException e) {
            return EMPTY_LIST;
        }
    }

    public List<MusicTag> findByKeyword(String keyword) {
        try {
            Dao<MusicTag, ?> dao = getDao(MusicTag.class);
            keyword = "'%"+keyword.replace("'","''")+"%'";
            QueryBuilder<MusicTag, ?> builder = dao.queryBuilder();
            builder.where().raw("title like "+keyword+" or artist like "+keyword +" or album like "+keyword);
            return builder.query();
        } catch (SQLException e) {
            return EMPTY_LIST;
        }
    }

    public List<MusicTag> findDuplicateSong() {
        try {
            List<MusicTag> list = new ArrayList<>();
            List<MusicTag> audioTags = findMySongs(); // getAllMusicTag();
            String title = "";
            String artist = "";
            MusicTag prvTag = null;
            for (MusicTag tag : audioTags) {
                if (StringUtils.isEmpty(title)) {
                    title = tag.getTitle();
                } else if ((StringUtils.similarity(title, tag.getTitle()) > Constants.MIN_TITLE)) {// ||
                    // found similar title
                    // check artist
                    if ((StringUtils.similarity(artist, tag.getArtist()) > Constants.MIN_ARTIST) ||
                            StringUtils.contains(artist, tag.getArtist())) {
                        if (prvTag != null && !list.contains(prvTag)) {
                            list.add(prvTag);
                        }
                        list.add(tag);
                    } else {
                        // found different artist
                        title = tag.getTitle();
                        artist = tag.getArtist();
                    }
                } else {
                    // found different title
                    title = tag.getTitle();
                    artist = tag.getArtist();
                }
                prvTag = tag;
            }
            return list;
        } catch (Exception e) {
            Log.e(TAG,"findDuplicateSong: "+e.getMessage());
            return EMPTY_LIST;
        }
    }

    public List<String> getGenres() {
        try {
            List<String> list = new ArrayList<>();
            Dao<MusicTag, ?> dao = getDao(MusicTag.class);
            QueryBuilder<MusicTag, ?> builder = dao.queryBuilder();
            builder.selectRaw("distinct genre");
            GenericRawResults<String[]> results = dao.queryRaw(builder.prepareStatementString());
            for(String[] vals : results.getResults()) {
                list.add(vals[0]);
            }
            return list;
        } catch (SQLException e) {
            Log.e(TAG,"getGenres: "+e.getMessage());
            return EMPTY_STRING_LIST;
        }
    }

    public List<String> getGrouping() {
        try {
            List<String> list = new ArrayList<>();
            Dao<MusicTag, ?> dao = getDao(MusicTag.class);
            QueryBuilder<MusicTag, ?> builder = dao.queryBuilder();
            builder.selectRaw("distinct grouping");
            GenericRawResults<String[]> results = dao.queryRaw(builder.prepareStatementString());
            //return Arrays.asList(result);
            for(String[] vals : results.getResults()) {
                list.add(vals[0]);
            }
            return list;
        } catch (SQLException e) {
            Log.e(TAG,"getGrouping: "+e.getMessage());
            return EMPTY_STRING_LIST;
        }
    }

    public List<MusicFolder> getGroupingWithChildrenCount() {
        try {
            //select grouping, count(id) from musictag group by grouping
            List<MusicFolder> list = new ArrayList<>();
            Dao<MusicTag, ?> dao = getDao(MusicTag.class);
            QueryBuilder<MusicTag, ?> builder = dao.queryBuilder();
            builder.selectRaw("grouping, count(id)");
            builder.groupBy("grouping");
            GenericRawResults<String[]> results = dao.queryRaw(builder.prepareStatementString());
            //return Arrays.asList(result);
            for(String[] vals : results.getResults()) {
                MusicFolder group = new MusicFolder();
                group.setName(vals[0]);
                group.setChildCount(StringUtils.toLong(vals[1]));
                if(vals[0] == null) {
                    group.setName("_NULL");
                }else if(StringUtils.isEmpty(vals[0])) {
                    group.setName("_EMPTY");
                }
                list.add(group);
            }
            return list;
        } catch (SQLException e) {
            Log.e(TAG,"getGrouping: "+e.getMessage());
            return EMPTY_FOLDER_LIST;
        }
    }

    public List<MusicFolder> getGenresWithChildrenCount() {
        try {
            //select grouping, count(id) from musictag group by grouping
            List<MusicFolder> list = new ArrayList<>();
            Dao<MusicTag, ?> dao = getDao(MusicTag.class);
            QueryBuilder<MusicTag, ?> builder = dao.queryBuilder();
            builder.selectRaw("genre, count(id)");
            builder.groupBy("genre");
            GenericRawResults<String[]> results = dao.queryRaw(builder.prepareStatementString());
            //return Arrays.asList(result);
            for(String[] vals : results.getResults()) {
                MusicFolder group = new MusicFolder();
                group.setName(vals[0]);
                group.setChildCount(StringUtils.toLong(vals[1]));
                if(vals[0] == null) {
                    group.setName("_NULL");
                }else if(StringUtils.isEmpty(vals[0])) {
                    group.setName("_EMPTY");
                }
                list.add(group);
            }
            return list;
        } catch (SQLException e) {
            Log.e(TAG,"genre: "+e.getMessage());
            return EMPTY_FOLDER_LIST;
        }
    }

    public List<String> getPublishers() {
        try {
            List<String> list = new ArrayList<>();
            Dao<MusicTag, ?> dao = getDao(MusicTag.class);
            QueryBuilder<MusicTag, ?> builder = dao.queryBuilder();
            builder.selectRaw("distinct publisher");
            GenericRawResults<String[]> results = dao.queryRaw(builder.prepareStatementString());
            for(String[] vals : results.getResults()) {
                list.add(vals[0]);
            }
            return list;
        } catch (SQLException e) {
            Log.e(TAG,"getPublishers: "+e.getMessage());
            return EMPTY_STRING_LIST;
        }
    }

    public List<String> getArtists() {
        try {
            List<String> list = new ArrayList<>();
            Dao<MusicTag, ?> dao = getDao(MusicTag.class);
            QueryBuilder<MusicTag, ?> builder = dao.queryBuilder();
            builder.selectRaw("distinct artist");
            GenericRawResults<String[]> results = dao.queryRaw(builder.prepareStatementString());
            for(String[] vals : results.getResults()) {
                list.add(vals[0]);
            }
            return list;
        } catch (SQLException e) {
            Log.e(TAG,"getArtits: "+e.getMessage());
            return EMPTY_STRING_LIST;
        }
    }

    public  MusicTag findById(long id) {
        try {
            Dao<MusicTag, Long> dao = getDao(MusicTag.class);
            return dao.queryForId(id);
        } catch (SQLException e) {
            return null;
        }
    }

    public List<String> getArtistForGrouping(String grouping) {
        try {
            List<String> list = new ArrayList<>();
            Dao<MusicTag, ?> dao = getDao(MusicTag.class);
            QueryBuilder<MusicTag, ?> builder = dao.queryBuilder();
            builder.selectRaw("distinct artist");
            if(EMPTY.equalsIgnoreCase(grouping)) {
                builder.where().isNull("grouping");
            }else {
                builder.where().eq("grouping", grouping);
            }
            GenericRawResults<String[]> results = dao.queryRaw(builder.prepareStatementString());
            for(String[] vals : results.getResults()) {
                list.add(vals[0]);
            }
            return list;
        } catch (SQLException e) {
            Log.e(TAG,"getArtistForGrouping: "+e.getMessage());
            return EMPTY_STRING_LIST;
        }
    }

    public List<MusicTag> findByGroupingAndArtist(String grouping, String artist)  {
        try {
            Dao<MusicTag, ?> dao = getDao(MusicTag.class);
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

    public long getMaxId() {
        long id= 0;
        try {
            Dao<MusicTag, ?> dao = getDao(MusicTag.class);
            QueryBuilder<MusicTag, ?> builder = dao.queryBuilder();
            builder.selectRaw("max(id)");
            GenericRawResults<String[]> results = dao.queryRaw(builder.prepareStatementString());
            for(String[] vals : results.getResults()) {
                id = StringUtils.toLong(vals[0]);
            }
        } catch (SQLException e) {
            id =0;
        }
        return id;
    }

    public List<MusicTag> findByIdRanges(long idRange1, long idRange2) {
        // 1000 - 1100
        try {
            Dao<MusicTag, ?> dao = getDao(MusicTag.class);
            QueryBuilder<MusicTag, ?> builder = dao.queryBuilder();
            builder.where().raw("id BETWEEN "+idRange1+" AND "+idRange2);
            return builder.query();
        } catch (SQLException e) {
            return EMPTY_LIST;
        }
    }

    public long getMinId() {
        long id= 0;
        try {
            Dao<MusicTag, ?> dao = getDao(MusicTag.class);
            QueryBuilder<MusicTag, ?> builder = dao.queryBuilder();
            builder.selectRaw("min(id)");
            GenericRawResults<String[]> results = dao.queryRaw(builder.prepareStatementString());
            for(String[] vals : results.getResults()) {
                id = StringUtils.toLong(vals[0]);
            }
        } catch (SQLException e) {
            id =0;
        }
        return id;
    }

    public List<MusicTag> findNoEmbedCoverArtSong() {
        try {
            Dao<MusicTag, ?> dao = getDao(MusicTag.class);
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
            List<MusicFolder> list = new ArrayList<>();
            Dao<MusicTag, ?> dao = getDao(MusicTag.class);
            QueryBuilder<MusicTag, ?> builder = dao.queryBuilder();
            builder.selectRaw("artist, count(id)");
            builder.groupBy("artist");
            GenericRawResults<String[]> results = dao.queryRaw(builder.prepareStatementString());
            //return Arrays.asList(result);
            for(String[] vals : results.getResults()) {
                MusicFolder group = new MusicFolder();
                group.setName(vals[0]);
                group.setChildCount(StringUtils.toLong(vals[1]));
                if(vals[0] == null) {
                    group.setName("_NULL");
                }else if(StringUtils.isEmpty(vals[0])) {
                    group.setName("_EMPTY");
                }
                list.add(group);
            }
            return list;
        } catch (SQLException e) {
            Log.e(TAG,"artist: "+e.getMessage());
            return EMPTY_FOLDER_LIST;
        }
    }

    public List<MusicFolder> getAlbumAndArtistWithChildrenCount() {
        try {
            //select grouping, count(id) from musictag group by grouping
            List<MusicFolder> list = new ArrayList<>();
            Dao<MusicTag, ?> dao = getDao(MusicTag.class);
            QueryBuilder<MusicTag, ?> builder = dao.queryBuilder();
            builder.selectRaw("album, albumartist, albumUniqueKey, count(id)");
            builder.groupByRaw("album, albumartist");
            GenericRawResults<String[]> results = dao.queryRaw(builder.prepareStatementString());
            //return Arrays.asList(result);
            for(String[] vals : results.getResults()) {
                MusicFolder group = new MusicFolder();
                String albumArtist = vals[1];
                String album = vals[0];
                String count = vals[3];
                group.setUniqueKey(vals[2]);
                group.setChildCount(StringUtils.toLong(count));
                if(StringUtils.isEmpty(album)) {
                    album = Constants.UNKNOWN;
                }
                if(StringUtils.isEmpty(albumArtist)) {
                    group.setName(album);
                }else if ("Various Artists".equalsIgnoreCase(albumArtist) ||
                         "Soundtrack".equalsIgnoreCase(albumArtist)) {
                    group.setName(album);
                }else {
                    group.setName(album +" (by "+albumArtist+")");
                }
                list.add(group);
            }
            return list;
        } catch (SQLException e) {
            Log.e(TAG,"album: "+e.getMessage());
            return EMPTY_FOLDER_LIST;
        }
    }

    public List<MusicTag> findByArtist(String name) {
        try {
            Dao<MusicTag, ?> dao = getDao(MusicTag.class);
            QueryBuilder<MusicTag, ?> builder = dao.queryBuilder();
            if(StringUtils.isEmpty(name)) {
                builder.where().isNull("artist").or().eq("artist", "");
            }else {
                builder.where().eq("artist", name.replace("'", "''"));
            }
            return builder.groupBy("title").groupBy("artist").query();
        } catch (SQLException e) {
            return EMPTY_LIST;
        }
    }

    public List<MusicTag> findByAlbumAndAlbumArtist(String album, String albumArtist) {
        try {
            Dao<MusicTag, ?> dao = getDao(MusicTag.class);
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
            return builder.groupBy("title").groupBy("artist").query();
        } catch (SQLException e) {
            return EMPTY_LIST;
        }
    }

    public MusicTag findByAlbumUniqueKey(String albumUniqueKey) {
        try {
            Dao<MusicTag, ?> dao = getDao(MusicTag.class);
            QueryBuilder<MusicTag, ?> builder = dao.queryBuilder();
            builder.where().eq("albumUniqueKey", albumUniqueKey);
            return dao.queryForFirst(builder.prepare());
        } catch (SQLException ex) {
            Log.e(TAG,"findByAlbumUniqueKey", ex);
        }
        return null;
    }

    public List<MusicTag> findPLSFUNSong() {
        try {
            Dao<MusicTag, ?> dao = getDao(MusicTag.class);
            QueryBuilder<MusicTag, ?> builder = dao.queryBuilder();
            Where<MusicTag, ?> where = builder.where();
            where.eq("genre", "Luk Thung").or().eq("genre", "Mor Lum");
            return builder.groupBy("title").groupBy("artist").query();
        } catch (SQLException e) {
            return EMPTY_LIST;
        }
    }

    public List<MusicTag> findPLSHappySong() {
        try {
            Dao<MusicTag, ?> dao = getDao(MusicTag.class);
            QueryBuilder<MusicTag, ?> builder = dao.queryBuilder();
            Where<MusicTag, ?> where = builder.where();
            where.ne("genre", "Luk Thung").and().ne("genre", "Mor Lum");
            where.and().ne("grouping", "Lounge").and().ne("grouping", "Acoustic");
            where.and().ne("grouping", "Thai Lounge").and().ne("grouping", "Thai Acoustic");
          //  where.raw("genre <> 'Luk Thung' or genre <> 'Mor Lum') and (grouping = 'Thai' or grouping = 'English')");
            return builder.groupBy("title").groupBy("artist").query();
        } catch (SQLException e) {
            return EMPTY_LIST;
        }
    }

    public List<MusicTag> findPLSChilloutSong() {
        try {
            Dao<MusicTag, ?> dao = getDao(MusicTag.class);
            QueryBuilder<MusicTag, ?> builder = dao.queryBuilder();
            Where<MusicTag, ?> where = builder.where();
            where.ne("genre", "Luk Thung").and().ne("genre", "Mor Lum");
            where.and().ne("grouping", "Lounge").and().ne("grouping", "Acoustic");
            where.and().ne("grouping", "Thai Lounge").and().ne("grouping", "Thai Acoustic");
            //  where.raw("genre <> 'Luk Thung' or genre <> 'Mor Lum') and (grouping = 'Thai' or grouping = 'English')");
            return builder.groupBy("title").groupBy("artist").query();
        } catch (SQLException e) {
            return EMPTY_LIST;
        }
    }

    public List<MusicTag> findPLSRelaxedSong() {
        try {
            Dao<MusicTag, ?> dao = getDao(MusicTag.class);
            QueryBuilder<MusicTag, ?> builder = dao.queryBuilder();
            Where<MusicTag, ?> where = builder.where();
            where.eq("grouping", "Lounge").or().eq("grouping", "Acoustic");
            where.or().eq("grouping", "Thai Lounge").or().eq("grouping", "Thai Acoustic");
            return builder.groupBy("title").groupBy("artist").query();
        } catch (SQLException e) {
            return EMPTY_LIST;
        }
    }
}
