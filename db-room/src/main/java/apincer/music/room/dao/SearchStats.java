package apincer.music.room.dao;

import androidx.room.ColumnInfo;

public class SearchStats {
    @ColumnInfo(name = "cnt")
    public long cnt;
    @ColumnInfo(name = "totalSize")
    public long totalSize;
    @ColumnInfo(name = "totalDuration")
    public double totalDuration;
}
