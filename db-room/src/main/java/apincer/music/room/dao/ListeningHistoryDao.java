package apincer.music.room.dao;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Transaction;
import java.util.List;
import apincer.music.core.playback.ListeningHistoryTracker;
import apincer.music.room.entity.ListeningHistoryEntity;
import apincer.music.room.entity.TrackEntity;

@Dao
public abstract class ListeningHistoryDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    abstract void ensureTrack(ListeningHistoryEntity entry);

    @Query("UPDATE listening_history SET lastPlayedMs = MAX(lastPlayedMs, :wall), lastStartedSession = :session WHERE trackId = :id AND lastStartedSession != :session")
    abstract void started(long id, String session, long wall);

    @Query("UPDATE listening_history SET completedPlays = completedPlays + 1, lastCompletedSession = :session WHERE trackId = :id AND lastCompletedSession != :session")
    abstract void completed(long id, String session);

    @Query("UPDATE listening_history SET skips = skips + 1, lastSkippedSession = :session WHERE trackId = :id AND lastSkippedSession != :session")
    abstract void skipped(long id, String session);

    @Transaction
    public void record(ListeningHistoryTracker.Event event) {
        ensureTrack(new ListeningHistoryEntity(event.trackId()));
        switch (event.kind()) {
            case STARTED -> started(event.trackId(), event.sessionId(), event.occurredAtMs());
            case COMPLETED -> completed(event.trackId(), event.sessionId());
            case SKIPPED -> skipped(event.trackId(), event.sessionId());
        }
    }

    @Query("SELECT t.* FROM musictag t LEFT JOIN listening_history h ON h.trackId = t.id WHERE COALESCE(h.completedPlays, 0) = 0 ORDER BY t.title ASC, t.id ASC")
    public abstract List<TrackEntity> findUnplayed();

    @Query("SELECT t.* FROM musictag t JOIN listening_history h ON h.trackId = t.id WHERE h.completedPlays > 0 AND h.lastPlayedMs <= :cutoff ORDER BY h.lastPlayedMs ASC, t.id ASC")
    public abstract List<TrackEntity> findRediscover(long cutoff);
}
