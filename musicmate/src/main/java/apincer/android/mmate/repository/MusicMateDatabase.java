package apincer.android.mmate.repository;

import android.content.Context;

import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;

import apincer.android.mmate.objectbox.MusicTag;
import apincer.android.mmate.objectbox.MusicTagDao;

@Database(entities = MusicTag.class,exportSchema = false, version = 2)
public abstract class MusicMateDatabase extends RoomDatabase {
    private static volatile MusicMateDatabase INSTANCE;

    public static MusicMateDatabase getDatabase(final Context context) {
        if (INSTANCE == null) {
            synchronized (MusicMateDatabase.class) {
                if (INSTANCE == null) {
                    INSTANCE = Room.databaseBuilder(context.getApplicationContext(),
                                    MusicMateDatabase.class, "apincer.android.musicmatedb")
                            .build();
                }
            }
        }
        return INSTANCE;
    }
    public abstract MusicTagDao musicTagDao();
}
