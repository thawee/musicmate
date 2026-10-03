package apincer.music.room;

import android.content.Context;

import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;

import apincer.music.room.dao.TrackDao;
import apincer.music.room.entity.TrackEntity;

@Database(entities = {TrackEntity.class, apincer.music.room.entity.ListeningHistoryEntity.class}, version = 3, exportSchema = false)
public abstract class MusicRoomDatabase extends RoomDatabase {

    public abstract TrackDao trackDao();
    public abstract apincer.music.room.dao.ListeningHistoryDao listeningHistoryDao();

    public static final androidx.room.migration.Migration MIGRATION_1_2 =
            new androidx.room.migration.Migration(1, 2) {
                @Override
                public void migrate(androidx.sqlite.db.SupportSQLiteDatabase db) {
                    db.execSQL("CREATE TABLE IF NOT EXISTS listening_history (trackId INTEGER NOT NULL PRIMARY KEY, lastPlayedMs INTEGER NOT NULL, completedPlays INTEGER NOT NULL, skips INTEGER NOT NULL, lastStartedSession TEXT NOT NULL, lastCompletedSession TEXT NOT NULL, lastSkippedSession TEXT NOT NULL)");
                }
            };

    public static final androidx.room.migration.Migration MIGRATION_2_3 =
            new androidx.room.migration.Migration(2, 3) {
                @Override
                public void migrate(androidx.sqlite.db.SupportSQLiteDatabase db) {
                    db.execSQL("CREATE INDEX IF NOT EXISTS `index_musictag_path` ON `musictag` (`path`)");
                }
            };

    private static volatile MusicRoomDatabase INSTANCE;

    public static MusicRoomDatabase getInstance(Context context) {
        if (INSTANCE == null) {
            synchronized (MusicRoomDatabase.class) {
                if (INSTANCE == null) {
                    INSTANCE = Room.databaseBuilder(
                            context.getApplicationContext(),
                            MusicRoomDatabase.class,
                            "musixmate_room.db"
                    )
                    .allowMainThreadQueries()
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                    .fallbackToDestructiveMigration()
                    .build();
                }
            }
        }
        return INSTANCE;
    }
}
