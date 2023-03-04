package apincer.android.mmate.repository;

import static apincer.android.mmate.utils.StringUtils.isEmpty;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;

import com.j256.ormlite.android.apptools.OrmLiteSqliteOpenHelper;
import com.j256.ormlite.dao.Dao;
import com.j256.ormlite.dao.GenericRawResults;
import com.j256.ormlite.stmt.QueryBuilder;
import com.j256.ormlite.support.ConnectionSource;
import com.j256.ormlite.table.TableUtils;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import apincer.android.mmate.Constants;
import apincer.android.mmate.objectbox.MusicTag;
import apincer.android.mmate.utils.StringUtils;

public class MusicMateOrmLite extends OrmLiteSqliteOpenHelper {
    //Database name
    private static final String DATABASE_NAME = "apincer.android.musicmate.db";
    //Version of the database. Changing the version will call {@Link OrmLite.onUpgrade}
    private static final int DATABASE_VERSION = 2;

    /**
     * The data access object used to interact with the Sqlite database to do C.R.U.D operations.
     */
    private Dao<MusicTag, Long> tagDao;

    public MusicMateOrmLite(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION) ;//,
                /**
                 * R.raw.ormlite_config is a reference to the ormlite_config2.txt file in the
                 * /res/raw/ directory of this project
                 * */
              //  R.raw.ormlite_config);
    }
    @Override
    public void onCreate(SQLiteDatabase database, ConnectionSource connectionSource) {
        try {

            /**
             * creates the database table
             */
            TableUtils.createTable(connectionSource, MusicTag.class);

        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void onUpgrade(SQLiteDatabase database, ConnectionSource connectionSource, int oldVersion, int newVersion) {
        try {
            /**
             * Recreates the database when onUpgrade is called by the framework
             */
            TableUtils.dropTable(connectionSource, MusicTag.class, false);
            onCreate(database, connectionSource);

        } catch (java.sql.SQLException e) {
            e.printStackTrace();
        }
    }

    /**
     * Returns an instance of the data access object
     * @return
     * @throws SQLException
     */
    public Dao<MusicTag, Long> getDao() throws SQLException {
        if(tagDao == null) {
            try {
                tagDao = getDao(MusicTag.class);
            } catch (java.sql.SQLException e) {
                e.printStackTrace();
            }
        }
        return tagDao;
    }

    /*
    public List<MusicTag> getAllMusicTag() {
        Dao<MusicTag, ?> dao = null;
        try {
            dao = getDao(MusicTag.class);

            return dao.queryForAll();
        } catch (SQLException e) {
            return Collections.EMPTY_LIST;
        }
    } */

    public List<MusicTag> findMySongs()  {
        Dao<MusicTag, ?> dao = null;
        try {
            dao = getDao(MusicTag.class);

            return dao.queryBuilder().groupBy("title").groupBy("artist").query();
        } catch (SQLException e) {
            return Collections.EMPTY_LIST;
        }
    }

    public List<MusicTag> findByTitle(String title)  {
        Dao<MusicTag, ?> dao = null;
        try {
            dao = getDao(MusicTag.class);

            QueryBuilder builder = dao.queryBuilder();
            String keyword = "%"+title.replace("'","''")+"%";
            builder.where().like("title",keyword).or().like("path", keyword);
            return builder.query();
        } catch (SQLException e) {
            return Collections.EMPTY_LIST;
        }
    }

    public List<MusicTag> findByPath(String path)  {
        Dao<MusicTag, ?> dao = null;
        try {
            dao = getDao(MusicTag.class);

            QueryBuilder builder = dao.queryBuilder();
            builder.where().eq("path",escapeString(path));
            return builder.query();
        } catch (SQLException e) {
            return Collections.EMPTY_LIST;
        }
    }

    public void save(MusicTag tag)   {
        Dao<MusicTag, ?> dao = null;
        try {
            dao = getDao(MusicTag.class);
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
        Dao<MusicTag, ?> dao = null;
        try {
            dao = getDao(MusicTag.class);
            dao.delete(tag);
        } catch (SQLException e) {
        }
    }

    public List<MusicTag> findMyIncommingSongs()  {
        Dao<MusicTag, ?> dao = null;
        try {
            dao = getDao(MusicTag.class);
            QueryBuilder builder = dao.queryBuilder();
            builder.where().eq("mmManaged",false);
            return builder.groupBy("title").groupBy("artist").query();
        } catch (SQLException e) {
            return Collections.EMPTY_LIST;
        }
    }

    //@Query("Select * from tag where fileSizeRatioaudioBitRate/(audioBitsDepth*audioSampleRate*audioChannels) <= 0.36")
    public List<MusicTag> findMyBrokenSongs()   {
        Dao<MusicTag, ?> dao = null;
        try {
            dao = getDao(MusicTag.class);
            QueryBuilder builder = dao.queryBuilder();
            builder.where().raw("fileSize = 0 or ((fileFormat ='aac' or fileFormat = 'mpeg') and audioBitRate < 32000) or (fileFormat not in ('aac','mpeg') and fileSizeRatio < 40) order by fileSize");
            return builder.query();
        } catch (SQLException e) {
            return Collections.EMPTY_LIST;
        }
    }

    public List<MusicTag> findByGenre(String genre)   {
        Dao<MusicTag, ?> dao = null;
        try {
            dao = getDao(MusicTag.class);
            QueryBuilder builder = dao.queryBuilder();
            builder.where().eq("genre",genre.replace("'","''"));
            return builder.groupBy("title").groupBy("artist").query();
        } catch (SQLException e) {
            return Collections.EMPTY_LIST;
        }

    }

    public List<MusicTag> findByGrouping(String grouping)   {
        Dao<MusicTag, ?> dao = null;
        try {
            dao = getDao(MusicTag.class);
            QueryBuilder builder = dao.queryBuilder();
            builder.where().eq("grouping",grouping.replace("'","''"));
            return builder.groupBy("title").groupBy("artist").query();
        } catch (SQLException e) {
            return Collections.EMPTY_LIST;
        }

    }

    public List<MusicTag> findHiRes()   {
        Dao<MusicTag, ?> dao = null;
        try {
            dao = getDao(MusicTag.class);
            QueryBuilder builder = dao.queryBuilder();
            builder.where().raw("fileFormat in ('alac', 'flac','aiff', 'wav') and audioBitsDepth >= 24 and audioSampleRate>= 48000");
            return builder.query();
        } catch (SQLException e) {
            return Collections.EMPTY_LIST;
        }

    }

    public List<MusicTag> findHighQuality()   {
        Dao<MusicTag, ?> dao = null;
        try {
            dao = getDao(MusicTag.class);
            QueryBuilder builder = dao.queryBuilder();
            builder.where().raw("fileFormat in ('aac', 'mpeg') and (audioBitsDepth < 16 or audioSampleRate < 44100)");
            return builder.query();
        } catch (SQLException e) {
            return Collections.EMPTY_LIST;
        }

    }

    public List<MusicTag> findMQASongs() {
        Dao<MusicTag, ?> dao = null;
        try {
            dao = getDao(MusicTag.class);
            QueryBuilder builder = dao.queryBuilder();
            builder.where().raw("mqaInd like 'MQA%' order by title, artist");
            return builder.query();
        } catch (SQLException e) {
            return Collections.EMPTY_LIST;
        }
    }

    public List<MusicTag> findDSDSongs() {
        Dao<MusicTag, ?> dao = null;
        try {
            dao = getDao(MusicTag.class);
            QueryBuilder builder = dao.queryBuilder();
            builder.where().raw("audiobitrate=1 order by title, artist");
            return builder.query();
        } catch (SQLException e) {
            return Collections.EMPTY_LIST;
        }
    }

    public List<MusicTag> findByMediaQuality(String keyword) {
        Dao<MusicTag, ?> dao = null;
        try {
            dao = getDao(MusicTag.class);
            QueryBuilder builder = dao.queryBuilder();
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
        Dao<MusicTag, ?> dao = null;
        try {
            dao = getDao(MusicTag.class);
            QueryBuilder builder = dao.queryBuilder();
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
        Dao<MusicTag, ?> dao = null;
        try {
            dao = getDao(MusicTag.class);
            QueryBuilder builder = dao.queryBuilder();

                builder.where().raw("fileFormat in ('flac','alac','aiff','wave') and mqaInd not like 'MQA%'  order by title, artist");
                return builder.query();
        } catch (SQLException e) {
            return Collections.EMPTY_LIST;
        }
    }

    public List<MusicTag> findByKeyword(String keyword) {
        Dao<MusicTag, ?> dao = null;
        try {
            dao = getDao(MusicTag.class);
            keyword = "%"+keyword.replace("'","''")+"%";
            QueryBuilder builder = dao.queryBuilder();
            builder.where().raw("title like "+keyword+" or artist like "+keyword);
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
            return Collections.EMPTY_LIST;
        }
    }

    public List<String> getGeners() {
        Dao<MusicTag, ?> dao = null;
        try {
            dao = getDao(MusicTag.class);
            QueryBuilder builder = dao.queryBuilder();
            builder.selectRaw("distinct genre");
            GenericRawResults<String[]> results = dao.queryRaw(builder.prepareStatementString());
            String[] result = results.getFirstResult();
            return Arrays.asList(result);
        } catch (SQLException e) {
            return Collections.EMPTY_LIST;
        }
    }

    public List<String> getGrouping() {
        Dao<MusicTag, ?> dao = null;
        try {
            dao = getDao(MusicTag.class);
            QueryBuilder builder = dao.queryBuilder();
            builder.selectRaw("distinct grouping");
            GenericRawResults<String[]> results = dao.queryRaw(builder.prepareStatementString());
            String[] result = results.getFirstResult();
            return Arrays.asList(result);
        } catch (SQLException e) {
            return Collections.EMPTY_LIST;
        }
    }
    public List<String> getPublishers() {
        Dao<MusicTag, ?> dao = null;
        try {
            dao = getDao(MusicTag.class);
            QueryBuilder builder = dao.queryBuilder();
            builder.selectRaw("distinct publisher");
            GenericRawResults<String[]> results = dao.queryRaw(builder.prepareStatementString());
            String[] result = results.getFirstResult();
            return Arrays.asList(result);
        } catch (SQLException e) {
            return Collections.EMPTY_LIST;
        }
    }

    public List<String> getArtits() {
        Dao<MusicTag, ?> dao = null;
        try {
            dao = getDao(MusicTag.class);
            QueryBuilder builder = dao.queryBuilder();
            builder.selectRaw("distinct artist");
            GenericRawResults<String[]> results = dao.queryRaw(builder.prepareStatementString());
            String[] result = results.getFirstResult();
            return Arrays.asList(result);
        } catch (SQLException e) {
            return Collections.EMPTY_LIST;
        }
    }

    public  MusicTag findById(long id) {
        Dao<MusicTag, Long> dao = null;
        try {
            dao = getDao(MusicTag.class);
            return dao.queryForId(id);
        } catch (SQLException e) {
            return null;
        }
    }
}
