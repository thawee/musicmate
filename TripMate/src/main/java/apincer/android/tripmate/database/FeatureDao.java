package apincer.android.tripmate.database;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import java.util.List;

import apincer.android.tripmate.database.model.Feature;

@Dao
public interface FeatureDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insert(Feature word);

    @Query("DELETE FROM Feature")
    void deleteAll();

    @Query("SELECT * from Feature ORDER BY name ASC")
    List<Feature> getAll();

    @Query("SELECT * from Feature ORDER BY name ASC")
    List<Feature> getByCategory(String category);
}
