package apincer.android.tripmate.database;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;

import java.util.List;

import apincer.android.tripmate.database.model.Place;

@Dao
public interface PlaceDao {
    @Insert
    void insert(Place place);

    @Query("DELETE FROM PLACE")
    void deleteAll();

    @Query("SELECT * from PLACE ORDER BY name ASC")
    List<Place> getAll();

    @Query("SELECT * from PLACE ORDER BY name ASC")
    List<Place> getByCategory(String category);
}
