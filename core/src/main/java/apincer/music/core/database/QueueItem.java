package apincer.music.core.database;
import com.j256.ormlite.field.DatabaseField;
import com.j256.ormlite.table.DatabaseTable;

@DatabaseTable(tableName = "queue_items")
public class QueueItem {

    @DatabaseField(id = true, canBeNull = false)
    private long id;

    @DatabaseField(foreign = true, foreignAutoRefresh = true, columnName = "track_id")
    private MusicTag track;

    public long getPosition() {
        return position;
    }

    @DatabaseField(canBeNull = false)
    private long position;

    public QueueItem(MusicTag track, long position) {
        this.track = track;
        this.id = track.getId();
        this.position = position;
    }

    public MusicTag getTrack() {
        return track;
    }

    public QueueItem() {
    }
}