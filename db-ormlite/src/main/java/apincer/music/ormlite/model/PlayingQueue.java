package apincer.music.ormlite.model;
import com.j256.ormlite.field.DatabaseField;
import com.j256.ormlite.table.DatabaseTable;

@DatabaseTable(tableName = "playing_queue")
public class PlayingQueue {

    @DatabaseField(id = true, canBeNull = false)
    private long id;

    @DatabaseField(foreign = true, foreignAutoRefresh = true, columnName = "track_id")
    private TrackEntity track;

    public long getPosition() {
        return position;
    }

    @DatabaseField(canBeNull = false)
    private long position;

    public PlayingQueue(TrackEntity track, long position) {
        this.track = track;
        this.id = track.getId();
        this.position = position;
    }

    public TrackEntity getTrack() {
        return track;
    }

    public PlayingQueue() {
    }
}