package apincer.music.core.repository;

import android.util.Log;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ThreadLocalRandom;

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

    public synchronized void setPlayingQueue(List<Track> songs) {
        if (songs == null || songs.isEmpty()) return;
        
        queueList.clear();
        
        // Use a Set to prevent duplicates efficiently
        java.util.Set<Long> existingIds = new java.util.HashSet<>();
        
        for (Track song : songs) {
            if (song != null && !song.isContainer() && existingIds.add(song.getId())) {
                queueList.add(song);
            }
        }
        
        currentIndex = 0;
        playbackIndex = 0;
        
        rebuildIndexMap();
        
        // Save the entire list to the DB in one shot
        try {
            dbHelper.savePlayingQueue(queueList);
        } catch (Exception e) {
            Log.e(TAG, "Failed to persist playing queue batch", e);
        }
        
        updateShuffleOrder();
    }

    public synchronized void enqueuePlayingQueue(List<Track> songs) {
        if (songs == null || songs.isEmpty()) return;
        
        java.util.Set<Long> existingIds = new java.util.HashSet<>();
        for (Track existing : queueList) {
            existingIds.add(existing.getId());
        }
        
        boolean modified = false;
        for (Track song : songs) {
            if (song != null && !song.isContainer() && existingIds.add(song.getId())) {
                queueList.add(song);
                modified = true;
            }
        }
        
        if (!modified) return;
        
        if (currentIndex == -1) {
            currentIndex = 0;
            playbackIndex = 0;
        }
        
        rebuildIndexMap();
        
        try {
            dbHelper.savePlayingQueue(queueList);
        } catch (Exception e) {
            Log.e(TAG, "Failed to persist playing queue batch enqueue", e);
        }
        
        updateShuffleOrder();
    }

    public synchronized void addPlayingQueue(long trackId) {
        Track song = tagRepos.findById(trackId);
        if (song != null) {
            addPlayingQueue(song);
        }
    }

    public synchronized void addPlayingQueue(Track song) {
        if (song == null) return;
        try {
            addToPlayingQueue(song);
        } catch (Exception e) {
            Log.e(TAG, "Failed to persist track to playing queue", e);
        }

        // If track is already in queue, remove it from existing position first to prevent duplicates
        int existingIndex = -1;
        for (int i = 0; i < queueList.size(); i++) {
            if (queueList.get(i).getId() == song.getId()) {
                existingIndex = i;
                break;
            }
        }
        if (existingIndex != -1) {
            queueList.remove(existingIndex);
            if (existingIndex < currentIndex) {
                currentIndex--;
            }
            if (existingIndex < playbackIndex) {
                playbackIndex--;
            }
        }

        queueList.add(song);
        if (currentIndex == -1) {
            currentIndex = 0;
            playbackIndex = 0;
        }
        rebuildIndexMap();
        dbHelper.savePlayingQueue(queueList);
        updateShuffleOrder();
    }

    public synchronized void addPlayNext(Track song) {
        if (song == null) return;
        try {
            addToPlayingQueue(song);
        } catch (Exception e) {
            Log.e(TAG, "Failed to persist track to playing queue", e);
        }

        // If track is already in queue, remove it from existing position first to prevent duplicates
        int existingIndex = -1;
        for (int i = 0; i < queueList.size(); i++) {
            if (queueList.get(i).getId() == song.getId()) {
                existingIndex = i;
                break;
            }
        }
        if (existingIndex != -1) {
            queueList.remove(existingIndex);
            if (existingIndex < currentIndex) {
                currentIndex--;
            }
            if (existingIndex < playbackIndex) {
                playbackIndex--;
            }
        }

        if (queueList.isEmpty()) {
            queueList.add(song);
            currentIndex = 0;
            playbackIndex = 0;
        } else {
            int insertPos = (currentIndex != -1 && currentIndex < queueList.size()) ? currentIndex + 1 : queueList.size();
            queueList.add(insertPos, song);
        }
        rebuildIndexMap();
        dbHelper.savePlayingQueue(queueList);
        updateShuffleOrder();
    }

    public synchronized void savePlayingQueue(List<Track> songsInContext) {
        queueList.clear();
        indexMap.clear();
        if (songsInContext != null) {
            for (int i = 0; i < songsInContext.size(); i++) {
                Track track = songsInContext.get(i);
                if (track == null) continue;
                if (!indexMap.containsKey(track.getId())) {
                    queueList.add(track);
                    indexMap.put(track.getId(), queueList.size() - 1);
                }
            }
        }
        dbHelper.savePlayingQueue(queueList);
        currentIndex = queueList.isEmpty() ? -1 : 0;
        playbackIndex = currentIndex;
        updateShuffleOrder();
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
        loadPlayingQueue(false);
    }

    public synchronized void loadPlayingQueue(boolean force) {
        if (!force && !queueList.isEmpty()) {
            return;
        }
        try {
            this.isShuffle = dbHelper.getShuffleMode();
            try {
                this.repeatMode = RepeatMode.valueOf(dbHelper.getRepeatMode());
            } catch (Exception e) {
                this.repeatMode = RepeatMode.OFF;
            }

            List<Track> songs = dbHelper.getPlayingQueue();
            queueList.clear();
            indexMap.clear();
            for (int i = 0; i < songs.size(); i++) {
                Track track = songs.get(i);
                if(track == null) continue;
                if (!indexMap.containsKey(track.getId())) {
                    queueList.add(track);
                    indexMap.put(track.getId(), queueList.size() - 1);
                }
            }

            if (!queueList.isEmpty()) {
                currentIndex = 0;
                playbackIndex = 0;
            } else {
                currentIndex = -1;
                playbackIndex = -1;
            }

            updateShuffleOrder();
            Log.d(TAG, "Loaded queue from DB. Size: " + queueList.size() + ", Shuffle: " + isShuffle + ", Repeat: " + repeatMode);
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
    public synchronized void setCurrentTrack(Track track) {
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
    public synchronized Track getNextTrack() {
        if (queueList.isEmpty()) return null;

        int baseIndex = (playbackIndex != -1) ? playbackIndex : currentIndex;
        if (baseIndex == -1) {
            baseIndex = 0;
            currentIndex = 0;
        }

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

    public synchronized Track getPreviousTrack() {
        int baseIndex = (playbackIndex != -1) ? playbackIndex : currentIndex;
        int prevIndex = getPreviousIndex(baseIndex);
        return prevIndex != -1 ? queueList.get(prevIndex) : null;
    }

    /**
     * Picks a random track from the current playback queue.
     *
     * @return A randomly selected {@link Track}, or {@code null} if the queue is empty.
     */
    public synchronized Track getRandomTrack() {
        if (queueList.isEmpty()) return null;
        int randomIndex = ThreadLocalRandom.current().nextInt(queueList.size());
        return queueList.get(randomIndex);
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

    /**
     * Enables or disables shuffle mode and rebuilds the shuffle order.
     *
     * @param enabled true to enable shuffle, false to disable
     */
    public synchronized void setShuffle(boolean enabled) {
        if (this.isShuffle == enabled) return;
        this.isShuffle = enabled;
        dbHelper.saveShuffleMode(enabled);
        updateShuffleOrder();
        Log.d(TAG, "Shuffle mode set to: " + enabled);
    }

    /**
     * Returns whether shuffle mode is currently enabled.
     */
    public boolean isShuffle() {
        return isShuffle;
    }

    /**
     * Sets the repeat mode and logs the change.
     *
     * @param mode The new repeat mode (OFF, ONE, ALL)
     */
    public synchronized void setRepeatMode(RepeatMode mode) {
        if (this.repeatMode == mode) return;
        this.repeatMode = mode;
        dbHelper.saveRepeatMode(mode != null ? mode.name() : RepeatMode.OFF.name());
        Log.d(TAG, "Repeat mode set to: " + mode);
    }

    /**
     * Returns the current repeat mode.
     */
    public RepeatMode getRepeatMode() {
        return repeatMode;
    }

    public void addToPlayingQueue(Track song) {
        try {
            dbHelper.addToPlayingQueue(song);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public synchronized void moveTrack(int fromPos, int toPos) {
        if (fromPos < 0 || fromPos >= queueList.size() || toPos < 0 || toPos >= queueList.size()) return;
        Track moved = queueList.remove(fromPos);
        queueList.add(toPos, moved);
        rebuildIndexMap();
        dbHelper.savePlayingQueue(queueList);
        updateShuffleOrder();
    }

    public synchronized void removeTrack(int position) {
        if (position < 0 || position >= queueList.size()) return;
        queueList.remove(position);
        rebuildIndexMap();

        if (position < currentIndex) {
            currentIndex--;
        }
        if (position < playbackIndex) {
            playbackIndex--;
        }

        if (currentIndex >= queueList.size()) {
            currentIndex = queueList.size() - 1;
        }
        if (playbackIndex >= queueList.size()) {
            playbackIndex = queueList.size() - 1;
        }

        dbHelper.savePlayingQueue(queueList);
        updateShuffleOrder();
    }

    public synchronized boolean removeTrackById(long id) {
        Integer idx = indexMap.get(id);
        if (idx != null && idx >= 0 && idx < queueList.size()) {
            removeTrack(idx);
            return true;
        }
        return false;
    }

    private void rebuildIndexMap() {
        indexMap.clear();
        for (int i = 0; i < queueList.size(); i++) {
            Track t = queueList.get(i);
            if (t != null) {
                indexMap.put(t.getId(), i);
            }
        }
    }

    public void emptyPlayingQueue() {
        dbHelper.emptyPlayingQueue();
        queueList.clear();
        indexMap.clear();
        currentIndex = -1;
        playbackIndex = -1;
    }
}