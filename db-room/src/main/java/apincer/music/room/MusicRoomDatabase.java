package apincer.music.room;

import android.content.Context;

import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;

import apincer.music.room.dao.TrackDao;
import apincer.music.room.entity.TrackEntity;

@Database(entities = {TrackEntity.class}, version = 1, exportSchema = false)
public abstract class MusicRoomDatabase extends RoomDatabase {

    public abstract TrackDao trackDao();

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
                    .fallbackToDestructiveMigration()
                    .build();
                }
            }
        }
        return INSTANCE;
    }
}
