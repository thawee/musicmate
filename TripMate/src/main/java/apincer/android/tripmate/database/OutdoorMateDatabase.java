package apincer.android.tripmate.database;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.room.Database;
import androidx.room.DatabaseConfiguration;
import androidx.room.InvalidationTracker;
import androidx.room.Room;
import androidx.room.RoomDatabase;
import androidx.sqlite.db.SupportSQLiteOpenHelper;

import apincer.android.tripmate.database.model.Feature;
import apincer.android.tripmate.database.model.Place;

@Database(entities = {Place.class, Feature.class}, version = 1)
public class OutdoorMateDatabase extends RoomDatabase {
    private static volatile OutdoorMateDatabase INSTANCE;

    public static OutdoorMateDatabase getDatabase(final Context context) {
        if (INSTANCE == null) {
            synchronized (OutdoorMateDatabase.class) {
                if (INSTANCE == null) {
                    INSTANCE = Room.databaseBuilder(context.getApplicationContext(),
                            OutdoorMateDatabase.class, "outdoormate_database")
                            .build();
                }
            }
        }
        return INSTANCE;
    }

    @NonNull
    @Override
    protected SupportSQLiteOpenHelper createOpenHelper(DatabaseConfiguration config) {
        return null;
    }

    @NonNull
    @Override
    protected InvalidationTracker createInvalidationTracker() {
        return null;
    }

    @Override
    public void clearAllTables() {

    }
}
