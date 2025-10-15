package apincer.music.core.database;
import com.j256.ormlite.field.DatabaseField;
import com.j256.ormlite.table.DatabaseTable;

@DatabaseTable(tableName = "playlist_items")
public class PlaylistItem {

    @DatabaseField(generatedId = true)
    private long id;

    // Link back to the parent Playlist
    @DatabaseField(foreign = true, foreignAutoCreate = true, columnName = "playlist_id")
    private Playlist playlist;

    // Assuming you have a Track class
    @DatabaseField(foreign = true, foreignAutoRefresh = true, columnName = "track_id")
    private MusicTag track;

    @DatabaseField(canBeNull = true)
    private String description;

    // This field stores the order of the track in the playlist.
    @DatabaseField(canBeNull = false)
    private int position;

    public PlaylistItem() {}

    // Getters and setters...
}