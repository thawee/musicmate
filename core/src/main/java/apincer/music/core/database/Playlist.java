package apincer.music.core.database;

import com.j256.ormlite.field.DatabaseField;
import com.j256.ormlite.field.ForeignCollectionField;
import com.j256.ormlite.table.DatabaseTable;

import java.util.Collection;

@DatabaseTable(tableName = "playlists")
public class Playlist {
    public enum PlaylistType {
        STATIC,  // A manually curated list of songs
        DYNAMIC  // A list generated from a set of rules
    }

    public enum RuleType {
        SONG,
        ALBUM,
        GENRE,
        GROUPING,
        RATING
    }

    @DatabaseField(generatedId = true)
    private long id;

    @DatabaseField(canBeNull = false)
    private String name;

    @DatabaseField(canBeNull = true)
    private String description;

    // This is the magic link to the collection of items in the playlist.
    // 'orderColumnName' is crucial for retrieving tracks in the correct order.
    @ForeignCollectionField(eager = false, orderColumnName = "position")
    private Collection<PlaylistItem> items;

    // ORMLite requires a no-argument constructor
    public Playlist() {}

    // Getters and setters...
}