package apincer.android.mmate.objectbox;

import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Update;

import java.util.List;

import apincer.android.mmate.objectbox.MusicTag;
import apincer.android.mmate.repository.SearchCriteria;

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

    @Query("Select * from tag order by title, artist")
    List<MusicTag> findMySongs();

    @Query("Select * from tag where mmManaged=false order by title, artist")
    List<MusicTag> findMyIncommingSongs();
    //@Query("Select * from tag where fileSizeRatioaudioBitRate/(audioBitsDepth*audioSampleRate*audioChannels) <= 0.36")
    @Query("Select * from tag where ((fileFormat ='aac' or fileFormat = 'mpeg') and audioBitRate < 32000) or (fileFormat not in ('aac','mpeg') and fileSizeRatio < 40) order by fileSize")
    List<MusicTag> findMyBrokenSongs();

   @Query("Select * from tag where path = :path")
   List<MusicTag> findByPath(String path);

    @Query("Select * from tag where title LIKE :title or path LIKE  :title ")
    List<MusicTag> findByTitle(String title);
}
