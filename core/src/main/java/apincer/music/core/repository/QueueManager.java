package apincer.music.core.repository;

import android.util.Log;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

import javax.inject.Inject;
import javax.inject.Singleton;

import apincer.music.core.model.Track;
import apincer.music.core.repository.spi.DbHelper;

/**
 * Manages the playback queue state, including track sequencing, shuffle logic,
 * and repeat modes for the MusicMate ecosystem.
 *
 * <p>This component acts as the "Source of Truth" for what should play next,
 * bridging the local database repository with the active playback engine (UPnP or Android).
 * It utilizes {@link CopyOnWriteArrayList} to ensure thread-safe iterations during
 * high-frequency UI updates and gapless priming.
 *
 * @author Thawee Prakaipetch
 * @version 2026.03.20
 */
@Singleton
public class QueueManager {
    private static final String TAG = "QueueManager";

    /**
     * Repository for accessing persistent queue items from the database.
     */
    private final TagRepository tagRepos;
    private final DbHelper dbHelper;

    /**
     * The master list of tracks in the current session.
     */
    private final List<Track> queueList = new CopyOnWriteArrayList<>();
    private final Map<Long, Integer> indexMap = new HashMap<>();
    private final Map<Integer, Integer> shuffleIndexMap = new HashMap<>();

    /**
     * The index of the currently active track within the {@code queueList}.
     * A value of -1 indicates no track is currently targeted.
     */
    private volatile int currentIndex = -1;

    private volatile int playbackIndex = -1;

    /**
     * The current repetition behavior for the queue.
     */
    private RepeatMode repeatMode = RepeatMode.OFF;

    /**
     * Whether the playback order should be randomized.
     */
    private boolean isShuffle = false;

    /**
     * Maps the visual queue order to physical indices when {@code isShuffle} is enabled.
     */
    private final List<Integer> shuffleOrder = new ArrayList<>();

    public void addPlayingQueue(long trackId) {
        Track song = tagRepos.findById(trackId);
          if (song != null) {
              addToPlayingQueue(song);
              queueList.add(song);
          }
    }

    public void savePlayingQueue(List<Track> songsInContext) {
        dbHelper.savePlayingQueue(songsInContext);
        queueList.clear();
        queueList.addAll(songsInContext);
    }

    /**
     * Defines supported behaviors for track repetition.
     */
    public enum RepeatMode {
        /** Play the queue once and stop. */
        OFF,
        /** Repeat the currently active track indefinitely. */
        ONE,
        /** Loop back to the start of the queue after the last track ends. */
        ALL
    }

    /**
     * Constructs the QueueManager with required metadata dependencies.
     * Initial queue state is loaded immediately upon injection.
     *
     * @param tagRepos The repository for queue persistence.
     */
    @Inject
    public QueueManager(TagRepository tagRepos) {
        this.tagRepos = tagRepos;
        this.dbHelper = tagRepos.getDbHelper();
        loadPlayingQueue();
    }

    /**
     * Retrieves an unmodifiable view of the current tracks in the queue.
     *
     * @return A list of {@link Track} objects.
     */
    public List<Track> getSongs() {
        return Collections.unmodifiableList(queueList);
    }

    /**
     * Synchronizes the internal memory list with the database state.
     * Should be invoked after UI operations that modify the playback queue
     * (e.g., "Add to Queue" or "Remove Track").
     */
    public synchronized void loadPlayingQueue() {
        try {
            List<Track> songs = dbHelper.getPlayingQueue();
            queueList.clear();
            indexMap.clear();
            for (int i = 0; i < songs.size(); i++) {
                Track track = songs.get(i);
                if(track==null) continue;
                queueList.add(track);
                indexMap.put(track.getId(), i);
            }

            updateShuffleOrder();
            Log.d(TAG, "Loaded queue from DB. Size: " + queueList.size());
        } catch (Exception e) {
            Log.e(TAG, "Error loading playing queue from database", e);
        }
    }

    /**
     * Re-anchors the internal pointer to a specific track.
     * Useful when playback is started from a specific item in the UI or when
     * external renderers report a track change.
     *
     * @param track The track to set as the current focal point.
     */
    public void setCurrentTrack(Track track) {
        if (track == null) return;


        Integer idx = indexMap.get(track.getId());
        if (idx != null) {
            currentIndex = idx;
        }

        if (isShuffle) {
            updateShuffleOrder();
        }
    }

    /*
    UPnP LastChange event fires
    Renderer switches track
     */
    public synchronized void setPlaybackTrack(Track track) {
        if (track == null) return;

        Integer idx = indexMap.get(track.getId());
        if (idx != null) {
            playbackIndex = idx;
            currentIndex = idx;

            if (isShuffle) {
                updateShuffleOrder(); // re-anchor shuffle
            }
        }
    }

    /**
     * Identifies the next logical track based on current {@link RepeatMode} and shuffle state.
     * This is primarily used for pre-loading the next URI for gapless UPnP playback.
     *
     * @return The next {@link Track}, or {@code null} if the end of the queue is reached.
     */
    public Track getNextTrack() {
        if (queueList.isEmpty()) return null;

        int baseIndex = (playbackIndex != -1) ? playbackIndex : currentIndex;
        if (repeatMode == RepeatMode.ONE && baseIndex != -1) {
            return queueList.get(baseIndex);
        }

        int nextIndex = getNextIndex(baseIndex);
        if (nextIndex != -1) {
            return queueList.get(nextIndex);
        }
        return null;
    }

    private int getPreviousIndex(int baseIndex) {
        if (queueList.isEmpty() || baseIndex == -1) return -1;

        if (isShuffle) {
            int pos = shuffleIndexMap.getOrDefault(baseIndex, -1);
            if (pos == -1) return -1;

            if (pos == 0) {
                return (repeatMode == RepeatMode.ALL)
                        ? shuffleOrder.get(shuffleOrder.size() - 1)
                        : -1;
            }

            return shuffleOrder.get(pos - 1);
        } else {
            int prev = baseIndex - 1;
            if (prev < 0) {
                return (repeatMode == RepeatMode.ALL)
                        ? queueList.size() - 1
                        : -1;
            }
            return prev;
        }
    }

    public Track getPreviousTrack() {
        int baseIndex = (playbackIndex != -1) ? playbackIndex : currentIndex;
        int prevIndex = getPreviousIndex(baseIndex);
        return prevIndex != -1 ? queueList.get(prevIndex) : null;
    }

    /**
     * Calculates the index of the next track according to queue logic.
     *
     * @return The next valid index, or -1 if no further tracks exist.
     */
    private int getNextIndex(int baseIndex) {
        if (queueList.isEmpty() || baseIndex == -1) return -1;

        if (isShuffle) {
            int currentShufflePos = shuffleIndexMap.getOrDefault(baseIndex, -1);
            if (currentShufflePos == -1) return -1;

            int nextShufflePos = currentShufflePos + 1;

            if (nextShufflePos >= shuffleOrder.size()) {
                return (repeatMode == RepeatMode.ALL)
                        ? shuffleOrder.get(0)
                        : -1;
            }

            return shuffleOrder.get(nextShufflePos);
        } else {
            int nextIdx = baseIndex + 1;

            if (nextIdx >= queueList.size()) {
                return (repeatMode == RepeatMode.ALL) ? 0 : -1;
            }
            return nextIdx;
        }
    }

    /**
     * Updates the shuffle mapping when the queue size or shuffle state changes.
     *
     * Behavior:
     * - If shuffle OFF → sequential order
     * - If shuffle ON:
     *   - Keep current playing track at front
     *   - Shuffle remaining tracks
     * - Builds reverse index map for O(1) lookup
     */
    private synchronized void updateShuffleOrder() {
        shuffleOrder.clear();
        shuffleIndexMap.clear();
        currentIndex = -1;
        playbackIndex = -1;

        int size = queueList.size();
        if (size == 0) return;

        // Build base order
        for (int i = 0; i < size; i++) {
            shuffleOrder.add(i);
        }

        if (isShuffle) {
            // Prefer playbackIndex (DLNA), fallback to currentIndex
            int baseIndex = (playbackIndex != -1) ? playbackIndex : currentIndex;

            if (baseIndex >= 0 && baseIndex < size) {
                // Remove current track
                shuffleOrder.remove((Integer) baseIndex);

                // Shuffle remaining
                Collections.shuffle(shuffleOrder);

                // Put current track at front
                shuffleOrder.add(0, baseIndex);
            } else {
                // No active track → shuffle all
                Collections.shuffle(shuffleOrder);
            }
        }

        // Build reverse lookup map (physical index → shuffle position)
        for (int i = 0; i < shuffleOrder.size(); i++) {
            shuffleIndexMap.put(shuffleOrder.get(i), i);
        }
    }

    /*
    public Dao<PlayingQueue, Long> getQueueItemDao() throws SQLException {
        return dbHelper.getQueueItemDao();
    } */

    public void addToPlayingQueue(Track song) {
        try {
            dbHelper.addToPlayingQueue(song);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /*
    public List<PlayingQueue> getQueueItems() {
        try {
            return dbHelper.getQueueItemDao().queryForAll();
        } catch (SQLException ignored) { }

        return Collections.EMPTY_LIST;
    } */

    public void emptyPlayingQueue() {
        dbHelper.emptyPlayingQueue();
        queueList.clear();
    }
}