package apincer.android.mmate.objectbox;

import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Update;

import java.util.List;

import apincer.android.mmate.objectbox.MusicTag;

@Dao
public interface MusicTagDao {
    @Query("Select * from tag")
    List<MusicTag> getAllMusicTag();

    @Insert
    void insert(MusicTag tag);

    @Update
    void update(MusicTag tag);

    @Delete
    void delete(MusicTag tag);
}
