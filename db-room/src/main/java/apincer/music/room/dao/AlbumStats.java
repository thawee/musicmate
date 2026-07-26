package apincer.music.room.dao;

import androidx.room.ColumnInfo;

public class AlbumStats {
    @ColumnInfo(name = "album")
    public String album;
    @ColumnInfo(name = "albumArtist")
    public String albumArtist;
    @ColumnInfo(name = "albumArtFilename")
    public String albumArtFilename;
    @ColumnInfo(name = "cnt")
    public long cnt;
}
