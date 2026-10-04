package apincer.music.room;

import androidx.annotation.NonNull;
import androidx.room.InvalidationTracker;
import androidx.room.RoomOpenDelegate;
import androidx.room.migration.AutoMigrationSpec;
import androidx.room.migration.Migration;
import androidx.room.util.DBUtil;
import androidx.room.util.TableInfo;
import androidx.sqlite.SQLite;
import androidx.sqlite.SQLiteConnection;
import apincer.music.room.dao.ListeningHistoryDao;
import apincer.music.room.dao.ListeningHistoryDao_Impl;
import apincer.music.room.dao.TrackDao;
import apincer.music.room.dao.TrackDao_Impl;
import java.lang.Class;
import java.lang.Override;
import java.lang.String;
import java.lang.SuppressWarnings;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.annotation.processing.Generated;

@Generated("androidx.room.RoomProcessor")
@SuppressWarnings({"unchecked", "deprecation", "removal"})
public final class MusicRoomDatabase_Impl extends MusicRoomDatabase {
  private volatile TrackDao _trackDao;

  private volatile ListeningHistoryDao _listeningHistoryDao;

  @Override
  @NonNull
  protected RoomOpenDelegate createOpenDelegate() {
    final RoomOpenDelegate _openDelegate = new RoomOpenDelegate(2, "2856760d86a4790db8f60091f9750240", "d639246a59625fc959fce88d0a506527") {
      @Override
      public void createAllTables(@NonNull final SQLiteConnection connection) {
        SQLite.execSQL(connection, "CREATE TABLE IF NOT EXISTS `musictag` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `uniqueKey` TEXT NOT NULL, `albumArtFilename` TEXT, `path` TEXT, `fileType` TEXT, `fileLastModified` INTEGER NOT NULL, `fileSize` INTEGER NOT NULL, `isManaged` INTEGER NOT NULL, `storageId` TEXT, `simpleName` TEXT, `audioEncoding` TEXT, `qualityInd` TEXT, `mqaSampleRate` INTEGER NOT NULL, `audioChannels` TEXT, `audioBitsDepth` INTEGER NOT NULL, `audioSampleRate` INTEGER NOT NULL, `audioBitRate` INTEGER NOT NULL, `audioDuration` REAL NOT NULL, `audioStartTime` REAL NOT NULL, `title` TEXT, `normalizedTitle` TEXT, `artist` TEXT, `normalizedArtist` TEXT, `album` TEXT, `year` TEXT, `genre` TEXT, `mood` TEXT, `style` TEXT, `origin` TEXT, `track` TEXT, `comment` TEXT, `composer` TEXT, `albumArtist` TEXT, `compilation` INTEGER NOT NULL, `publisher` TEXT, `drScore` REAL NOT NULL, `dynamicRange` REAL NOT NULL, `bpm` REAL NOT NULL)");
        SQLite.execSQL(connection, "CREATE UNIQUE INDEX IF NOT EXISTS `index_musictag_uniqueKey` ON `musictag` (`uniqueKey`)");
        SQLite.execSQL(connection, "CREATE INDEX IF NOT EXISTS `index_musictag_qualityInd` ON `musictag` (`qualityInd`)");
        SQLite.execSQL(connection, "CREATE INDEX IF NOT EXISTS `index_musictag_audioBitsDepth` ON `musictag` (`audioBitsDepth`)");
        SQLite.execSQL(connection, "CREATE INDEX IF NOT EXISTS `index_musictag_title` ON `musictag` (`title`)");
        SQLite.execSQL(connection, "CREATE INDEX IF NOT EXISTS `index_musictag_artist` ON `musictag` (`artist`)");
        SQLite.execSQL(connection, "CREATE INDEX IF NOT EXISTS `index_musictag_normalizedTitle` ON `musictag` (`normalizedTitle`)");
        SQLite.execSQL(connection, "CREATE INDEX IF NOT EXISTS `index_musictag_normalizedArtist` ON `musictag` (`normalizedArtist`)");
        SQLite.execSQL(connection, "CREATE INDEX IF NOT EXISTS `index_musictag_album` ON `musictag` (`album`)");
        SQLite.execSQL(connection, "CREATE INDEX IF NOT EXISTS `index_musictag_genre` ON `musictag` (`genre`)");
        SQLite.execSQL(connection, "CREATE INDEX IF NOT EXISTS `index_musictag_publisher` ON `musictag` (`publisher`)");
        SQLite.execSQL(connection, "CREATE TABLE IF NOT EXISTS `listening_history` (`trackId` INTEGER NOT NULL, `lastPlayedMs` INTEGER NOT NULL, `completedPlays` INTEGER NOT NULL, `skips` INTEGER NOT NULL, `lastStartedSession` TEXT NOT NULL, `lastCompletedSession` TEXT NOT NULL, `lastSkippedSession` TEXT NOT NULL, PRIMARY KEY(`trackId`))");
        SQLite.execSQL(connection, "CREATE TABLE IF NOT EXISTS room_master_table (id INTEGER PRIMARY KEY,identity_hash TEXT)");
        SQLite.execSQL(connection, "INSERT OR REPLACE INTO room_master_table (id,identity_hash) VALUES(42, '2856760d86a4790db8f60091f9750240')");
      }

      @Override
      public void dropAllTables(@NonNull final SQLiteConnection connection) {
        SQLite.execSQL(connection, "DROP TABLE IF EXISTS `musictag`");
        SQLite.execSQL(connection, "DROP TABLE IF EXISTS `listening_history`");
      }

      @Override
      public void onCreate(@NonNull final SQLiteConnection connection) {
      }

      @Override
      public void onOpen(@NonNull final SQLiteConnection connection) {
        internalInitInvalidationTracker(connection);
      }

      @Override
      public void onPreMigrate(@NonNull final SQLiteConnection connection) {
        DBUtil.dropFtsSyncTriggers(connection);
      }

      @Override
      public void onPostMigrate(@NonNull final SQLiteConnection connection) {
      }

      @Override
      @NonNull
      public RoomOpenDelegate.ValidationResult onValidateSchema(
          @NonNull final SQLiteConnection connection) {
        final Map<String, TableInfo.Column> _columnsMusictag = new HashMap<String, TableInfo.Column>(38);
        _columnsMusictag.put("id", new TableInfo.Column("id", "INTEGER", true, 1, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsMusictag.put("uniqueKey", new TableInfo.Column("uniqueKey", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsMusictag.put("albumArtFilename", new TableInfo.Column("albumArtFilename", "TEXT", false, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsMusictag.put("path", new TableInfo.Column("path", "TEXT", false, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsMusictag.put("fileType", new TableInfo.Column("fileType", "TEXT", false, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsMusictag.put("fileLastModified", new TableInfo.Column("fileLastModified", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsMusictag.put("fileSize", new TableInfo.Column("fileSize", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsMusictag.put("isManaged", new TableInfo.Column("isManaged", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsMusictag.put("storageId", new TableInfo.Column("storageId", "TEXT", false, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsMusictag.put("simpleName", new TableInfo.Column("simpleName", "TEXT", false, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsMusictag.put("audioEncoding", new TableInfo.Column("audioEncoding", "TEXT", false, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsMusictag.put("qualityInd", new TableInfo.Column("qualityInd", "TEXT", false, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsMusictag.put("mqaSampleRate", new TableInfo.Column("mqaSampleRate", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsMusictag.put("audioChannels", new TableInfo.Column("audioChannels", "TEXT", false, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsMusictag.put("audioBitsDepth", new TableInfo.Column("audioBitsDepth", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsMusictag.put("audioSampleRate", new TableInfo.Column("audioSampleRate", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsMusictag.put("audioBitRate", new TableInfo.Column("audioBitRate", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsMusictag.put("audioDuration", new TableInfo.Column("audioDuration", "REAL", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsMusictag.put("audioStartTime", new TableInfo.Column("audioStartTime", "REAL", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsMusictag.put("title", new TableInfo.Column("title", "TEXT", false, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsMusictag.put("normalizedTitle", new TableInfo.Column("normalizedTitle", "TEXT", false, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsMusictag.put("artist", new TableInfo.Column("artist", "TEXT", false, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsMusictag.put("normalizedArtist", new TableInfo.Column("normalizedArtist", "TEXT", false, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsMusictag.put("album", new TableInfo.Column("album", "TEXT", false, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsMusictag.put("year", new TableInfo.Column("year", "TEXT", false, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsMusictag.put("genre", new TableInfo.Column("genre", "TEXT", false, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsMusictag.put("mood", new TableInfo.Column("mood", "TEXT", false, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsMusictag.put("style", new TableInfo.Column("style", "TEXT", false, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsMusictag.put("origin", new TableInfo.Column("origin", "TEXT", false, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsMusictag.put("track", new TableInfo.Column("track", "TEXT", false, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsMusictag.put("comment", new TableInfo.Column("comment", "TEXT", false, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsMusictag.put("composer", new TableInfo.Column("composer", "TEXT", false, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsMusictag.put("albumArtist", new TableInfo.Column("albumArtist", "TEXT", false, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsMusictag.put("compilation", new TableInfo.Column("compilation", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsMusictag.put("publisher", new TableInfo.Column("publisher", "TEXT", false, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsMusictag.put("drScore", new TableInfo.Column("drScore", "REAL", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsMusictag.put("dynamicRange", new TableInfo.Column("dynamicRange", "REAL", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsMusictag.put("bpm", new TableInfo.Column("bpm", "REAL", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        final Set<TableInfo.ForeignKey> _foreignKeysMusictag = new HashSet<TableInfo.ForeignKey>(0);
        final Set<TableInfo.Index> _indicesMusictag = new HashSet<TableInfo.Index>(10);
        _indicesMusictag.add(new TableInfo.Index("index_musictag_uniqueKey", true, Arrays.asList("uniqueKey"), Arrays.asList("ASC")));
        _indicesMusictag.add(new TableInfo.Index("index_musictag_qualityInd", false, Arrays.asList("qualityInd"), Arrays.asList("ASC")));
        _indicesMusictag.add(new TableInfo.Index("index_musictag_audioBitsDepth", false, Arrays.asList("audioBitsDepth"), Arrays.asList("ASC")));
        _indicesMusictag.add(new TableInfo.Index("index_musictag_title", false, Arrays.asList("title"), Arrays.asList("ASC")));
        _indicesMusictag.add(new TableInfo.Index("index_musictag_artist", false, Arrays.asList("artist"), Arrays.asList("ASC")));
        _indicesMusictag.add(new TableInfo.Index("index_musictag_normalizedTitle", false, Arrays.asList("normalizedTitle"), Arrays.asList("ASC")));
        _indicesMusictag.add(new TableInfo.Index("index_musictag_normalizedArtist", false, Arrays.asList("normalizedArtist"), Arrays.asList("ASC")));
        _indicesMusictag.add(new TableInfo.Index("index_musictag_album", false, Arrays.asList("album"), Arrays.asList("ASC")));
        _indicesMusictag.add(new TableInfo.Index("index_musictag_genre", false, Arrays.asList("genre"), Arrays.asList("ASC")));
        _indicesMusictag.add(new TableInfo.Index("index_musictag_publisher", false, Arrays.asList("publisher"), Arrays.asList("ASC")));
        final TableInfo _infoMusictag = new TableInfo("musictag", _columnsMusictag, _foreignKeysMusictag, _indicesMusictag);
        final TableInfo _existingMusictag = TableInfo.read(connection, "musictag");
        if (!_infoMusictag.equals(_existingMusictag)) {
          return new RoomOpenDelegate.ValidationResult(false, "musictag(apincer.music.room.entity.TrackEntity).\n"
                  + " Expected:\n" + _infoMusictag + "\n"
                  + " Found:\n" + _existingMusictag);
        }
        final Map<String, TableInfo.Column> _columnsListeningHistory = new HashMap<String, TableInfo.Column>(7);
        _columnsListeningHistory.put("trackId", new TableInfo.Column("trackId", "INTEGER", true, 1, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsListeningHistory.put("lastPlayedMs", new TableInfo.Column("lastPlayedMs", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsListeningHistory.put("completedPlays", new TableInfo.Column("completedPlays", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsListeningHistory.put("skips", new TableInfo.Column("skips", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsListeningHistory.put("lastStartedSession", new TableInfo.Column("lastStartedSession", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsListeningHistory.put("lastCompletedSession", new TableInfo.Column("lastCompletedSession", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsListeningHistory.put("lastSkippedSession", new TableInfo.Column("lastSkippedSession", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        final Set<TableInfo.ForeignKey> _foreignKeysListeningHistory = new HashSet<TableInfo.ForeignKey>(0);
        final Set<TableInfo.Index> _indicesListeningHistory = new HashSet<TableInfo.Index>(0);
        final TableInfo _infoListeningHistory = new TableInfo("listening_history", _columnsListeningHistory, _foreignKeysListeningHistory, _indicesListeningHistory);
        final TableInfo _existingListeningHistory = TableInfo.read(connection, "listening_history");
        if (!_infoListeningHistory.equals(_existingListeningHistory)) {
          return new RoomOpenDelegate.ValidationResult(false, "listening_history(apincer.music.room.entity.ListeningHistoryEntity).\n"
                  + " Expected:\n" + _infoListeningHistory + "\n"
                  + " Found:\n" + _existingListeningHistory);
        }
        return new RoomOpenDelegate.ValidationResult(true, null);
      }
    };
    return _openDelegate;
  }

  @Override
  @NonNull
  protected InvalidationTracker createInvalidationTracker() {
    final Map<String, String> _shadowTablesMap = new HashMap<String, String>(0);
    final Map<String, Set<String>> _viewTables = new HashMap<String, Set<String>>(0);
    return new InvalidationTracker(this, _shadowTablesMap, _viewTables, "musictag", "listening_history");
  }

  @Override
  public void clearAllTables() {
    super.performClear(false, "musictag", "listening_history");
  }

  @Override
  @NonNull
  protected Map<Class<?>, List<Class<?>>> getRequiredTypeConverters() {
    final Map<Class<?>, List<Class<?>>> _typeConvertersMap = new HashMap<Class<?>, List<Class<?>>>();
    _typeConvertersMap.put(TrackDao.class, TrackDao_Impl.getRequiredConverters());
    _typeConvertersMap.put(ListeningHistoryDao.class, ListeningHistoryDao_Impl.getRequiredConverters());
    return _typeConvertersMap;
  }

  @Override
  @NonNull
  public Set<Class<? extends AutoMigrationSpec>> getRequiredAutoMigrationSpecs() {
    final Set<Class<? extends AutoMigrationSpec>> _autoMigrationSpecsSet = new HashSet<Class<? extends AutoMigrationSpec>>();
    return _autoMigrationSpecsSet;
  }

  @Override
  @NonNull
  public List<Migration> getAutoMigrations(
      @NonNull final Map<Class<? extends AutoMigrationSpec>, AutoMigrationSpec> autoMigrationSpecs) {
    final List<Migration> _autoMigrations = new ArrayList<Migration>();
    return _autoMigrations;
  }

  @Override
  public TrackDao trackDao() {
    if (_trackDao != null) {
      return _trackDao;
    } else {
      synchronized(this) {
        if(_trackDao == null) {
          _trackDao = new TrackDao_Impl(this);
        }
        return _trackDao;
      }
    }
  }

  @Override
  public ListeningHistoryDao listeningHistoryDao() {
    if (_listeningHistoryDao != null) {
      return _listeningHistoryDao;
    } else {
      synchronized(this) {
        if(_listeningHistoryDao == null) {
          _listeningHistoryDao = new ListeningHistoryDao_Impl(this);
        }
        return _listeningHistoryDao;
      }
    }
  }
}
