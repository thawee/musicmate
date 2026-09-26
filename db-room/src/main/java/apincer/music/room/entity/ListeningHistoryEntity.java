package apincer.music.room.entity;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

/** Independent of the playing queue: clearing a queue never clears listening history. */
@Entity(tableName = "listening_history")
public class ListeningHistoryEntity {
    @PrimaryKey public long trackId;
    public long lastPlayedMs;
    public long completedPlays;
    public long skips;
    @NonNull public String lastStartedSession = "";
    @NonNull public String lastCompletedSession = "";
    @NonNull public String lastSkippedSession = "";

    public ListeningHistoryEntity(long trackId) { this.trackId = trackId; }
}
