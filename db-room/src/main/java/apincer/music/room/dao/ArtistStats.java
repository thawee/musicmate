package apincer.music.room.dao;

import androidx.room.ColumnInfo;

public class ArtistStats {
    @ColumnInfo(name = "artist")
    public String artist;
    @ColumnInfo(name = "cnt")
    public long cnt;
    @ColumnInfo(name = "dur")
    public double dur;
}
