package apincer.android.mmate.repository;

import static apincer.android.mmate.Constants.MIN_SPL_16BIT_IN_DB;
import static apincer.android.mmate.Constants.MIN_SPL_24BIT_IN_DB;
import static apincer.android.mmate.Constants.SPL_16BIT_IN_DB;
import static apincer.android.mmate.Constants.SPL_8BIT_IN_DB;
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
import java.util.Collections;
import java.util.List;

import apincer.android.mmate.Constants;
import apincer.android.mmate.utils.StringUtils;

public class MusicMateOrmLite extends OrmLiteSqliteOpenHelper {
    //Database name
    private static final String DATABASE_NAME = "apincer.android.musicmate.db";
    //Version of the database. Changing the version will call {@Link OrmLite.onUpgrade}
    private static final int DATABASE_VERSION = 2;

    public MusicMateOrmLite(Context context) {
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
            e.printStackTrace();
        }
    }

    @Override
    public void onUpgrade(SQLiteDatabase database, ConnectionSource connectionSource, int oldVersion, int newVersion) {
        try {
            // Recreates the database when onUpgrade is called by the framework
            TableUtils.dropTable(connectionSource, MusicTag.class, false);
            onCreate(database, connectionSource);

        } catch (java.sql.SQLException e) {
            e.printStackTrace();
        }
    }

    public List<MusicTag> findMySongs()  {
        try {
            Dao<MusicTag, ?> dao = getDao(MusicTag.class);

            return dao.queryBuilder().orderBy("title", true).orderByNullsFirst("artist", true).query();
        } catch (SQLException e) {
            return Collections.EMPTY_LIST;
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
            return Collections.EMPTY_LIST;
        }
    }

    public List<MusicTag> findByPath(String path)  {
        try {
            Dao<MusicTag, ?> dao = getDao(MusicTag.class);

            QueryBuilder<MusicTag, ?> builder = dao.queryBuilder();
            builder.where().eq("path",escapeString(path));
            return builder.query();
        } catch (Exception e) {
            return Collections.EMPTY_LIST;
        }
    }

    public void save(MusicTag tag)   {
        try {
            Dao<MusicTag, ?> dao = getDao(MusicTag.class);
            String keyword = escapeString(tag.getUniqueKey());

            if(dao.queryForEq("uniqueKey", keyword).size()>0) {
                dao.update(tag);
            }else {
                dao.create(tag);
            }
        } catch (SQLException e) {

        }
    }

    private String escapeString(String text) {
        return text.replace("'","''");
    }

    public void delete(MusicTag tag)   {
        try {
            Dao<MusicTag, ?> dao = getDao(MusicTag.class);
            dao.delete(tag);
        } catch (SQLException e) {
        }
    }

    public List<MusicTag> findMyIncomingSongs()  {
        try {
            Dao<MusicTag, ?> dao = getDao(MusicTag.class);
            QueryBuilder<MusicTag, ?> builder = dao.queryBuilder();
            builder.where().eq("mmManaged",false);
            return builder.orderByNullsFirst("title", true).orderByNullsFirst("artist", true).query();
        } catch (SQLException e) {
            return Collections.EMPTY_LIST;
        }
    }

    public List<MusicTag> findMyNoneDRSongs()  {
        try {
            Dao<MusicTag, ?> dao = getDao(MusicTag.class);
            QueryBuilder<MusicTag, ?> builder = dao.queryBuilder();
            builder.where().eq("dynamicRange",0);
            return builder.orderByNullsFirst("title", true).orderByNullsFirst("artist", true).query();
        } catch (SQLException e) {
            return Collections.EMPTY_LIST;
        }
    }

    //@Query("Select * from tag where fileSizeRatioaudioBitRate/(audioBitsDepth*audioSampleRate*audioChannels) <= 0.36")
    public List<MusicTag> findMyBrokenSongs()   {
        try {
            Dao<MusicTag, ?> dao = getDao(MusicTag.class);
            QueryBuilder<MusicTag, ?> builder = dao.queryBuilder();
            builder.where().raw("fileSize < 5120 or ((dynamicRange>0 AND dynamicRange <= "+MIN_SPL_16BIT_IN_DB+" AND audioBitsDepth<=16) OR (dynamicRange>0 AND dynamicRange <= "+MIN_SPL_24BIT_IN_DB+" AND audioBitsDepth >= 24)) order by title");
            return builder.query();
        } catch (SQLException e) {
            return Collections.EMPTY_LIST;
        }
    }

    public List<MusicTag> findByGenre(String genre)   {
        try {
            Dao<MusicTag, ?> dao = getDao(MusicTag.class);
            QueryBuilder<MusicTag, ?> builder = dao.queryBuilder();
            builder.where().eq("genre",genre.replace("'","''"));
            return builder.groupBy("title").groupBy("artist").query();
        } catch (SQLException e) {
            return Collections.EMPTY_LIST;
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
            return Collections.EMPTY_LIST;
        }

    }

    public List<MusicTag> findHiRes()   {
        try {
            Dao<MusicTag, ?> dao = getDao(MusicTag.class);
            QueryBuilder<MusicTag, ?> builder = dao.queryBuilder();
            builder.where().raw("audioEncoding in ('alac', 'flac','aiff', 'wav') and audioBitsDepth >= 24 and audioSampleRate >= 48000 and mqaInd not like 'MQA%'");
            return builder.query();
        } catch (SQLException e) {
            return Collections.EMPTY_LIST;
        }

    }

    public List<MusicTag> findHighQuality()   {
        try {
            Dao<MusicTag, ?> dao = getDao(MusicTag.class);
            QueryBuilder<MusicTag, ?> builder = dao.queryBuilder();
            builder.where().raw(" audioEncoding in ('aac', 'mpeg') ");
            return builder.query();
        } catch (SQLException e) {
            return Collections.EMPTY_LIST;
        }

    }

    public List<MusicTag> findMQASongs() {
        try {
            Dao<MusicTag, ?> dao = getDao(MusicTag.class);
            QueryBuilder<MusicTag, ?> builder = dao.queryBuilder();
            builder.where().raw("mqaInd like 'MQA%' order by title, artist");
            return builder.query();
        } catch (SQLException e) {
            return Collections.EMPTY_LIST;
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
            return Collections.EMPTY_LIST;
        }
    }

    public List<MusicTag> findByMediaQuality(String keyword) {
        try {
            Dao<MusicTag, ?> dao = getDao(MusicTag.class);
            QueryBuilder<MusicTag, ?> builder = dao.queryBuilder();
            if (isEmpty(keyword) || Constants.QUALITY_NORMAL.equals(keyword)) {
                builder.where().raw("mediaQuality is null order by title, artist");
                return builder.query();
            } else {
                builder.where().raw("mediaQuality="+escapeString(keyword)+" order by title, artist");
                return builder.query();
            }
        } catch (SQLException e) {
            return Collections.EMPTY_LIST;
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
            return Collections.EMPTY_LIST;
        }
    }

    public List<MusicTag> findLosslessSong() {
        try {
            Dao<MusicTag, ?> dao = getDao(MusicTag.class);
            QueryBuilder<MusicTag, ?> builder = dao.queryBuilder();

                builder.where().raw("audioEncoding in ('flac','alac','aiff','wave') and mqaInd not like 'MQA%'  order by title, artist");
                return builder.query();
        } catch (SQLException e) {
            return Collections.EMPTY_LIST;
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
            return Collections.EMPTY_LIST;
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
            Log.e("MusicMateORMLite","findDuplicateSong: "+e.getMessage());
            return Collections.EMPTY_LIST;
        }
    }

    public List<String> getGeners() {
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
            Log.e("MusicMateORMLite","getGeners: "+e.getMessage());
            return Collections.EMPTY_LIST;
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
            Log.e("MusicMateORMLite","getGrouping: "+e.getMessage());
            return Collections.EMPTY_LIST;
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
            Log.e("MusicMateORMLite","getPublishers: "+e.getMessage());
            return Collections.EMPTY_LIST;
        }
    }

    public List<String> getArtits() {
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
            Log.e("MusicMateORMLite","getArtits: "+e.getMessage());
            return Collections.EMPTY_LIST;
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
            Log.e("MusicMateORMLite","getArtistForGrouping: "+e.getMessage());
            return Collections.EMPTY_LIST;
        }
    }

    public List<MusicTag> findByGroupingAndArtist(String grouping, String artist)  {
        try {
            Dao<MusicTag, ?> dao = getDao(MusicTag.class);
            QueryBuilder<MusicTag, ?> builder = dao.queryBuilder();
            Where where = builder.where();
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
            return Collections.EMPTY_LIST;
        }
    }

    public long getTotalSongCont() {
        try {
            Dao<MusicTag, ?> dao = getDao(MusicTag.class);
            return dao.countOf();
        } catch (SQLException e) {

        }
        return 0;
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
            return Collections.EMPTY_LIST;
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
}
