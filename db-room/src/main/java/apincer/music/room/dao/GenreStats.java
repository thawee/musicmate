package apincer.music.room.dao;

import androidx.room.ColumnInfo;

public class GenreStats {
    @ColumnInfo(name = "genre")
    public String genre;
    @ColumnInfo(name = "cnt")
    public long cnt;
    @ColumnInfo(name = "dur")
    public double dur;
}
