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
     * Set when the playing track is removed from the queue while it keeps playing.
     * Next then continues with the track that followed it ({@code null} = queue ended),
     * until playback moves to another track.
     */
    private boolean anchorRemoved = false;
    private long removedAnchorId = -1;
    private Long anchorSuccessorId = null;

    /** Notified after edits that can change the next track (queue contents, order, Repeat, Shuffle). */
    private volatile Runnable queueChangeListener;

    public void setQueueChangeListener(Runnable listener) {
        this.queueChangeListener = listener;
    }

    private void notifyQueueChanged() {
        Runnable listener = queueChangeListener;
        if (listener != null) listener.run();
    }

    private void clearRemovedAnchor() {
        anchorRemoved = false;
        removedAnchorId = -1;
        anchorSuccessorId = null;
    }

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

    public enum Source { MANUAL, NEW, DOWNLOADS, UNPLAYED, REDISCOVER, PLAYLIST }
    private Source source = Source.MANUAL;
    private String activePlaylistName;
    private final java.util.Set<Long> sessionIds = new java.util.HashSet<>();
    private final java.util.Set<Long> smartTrackIds = new java.util.HashSet<>();
    private long sourceRevision;
    private boolean autoFilling;
    private boolean sourceExhausted;
    private static final int SMART_LOOKAHEAD = 20;

    public synchronized Source getSource() { return source; }
    public synchronized String getActivePlaylistName() { return activePlaylistName; }

    public synchronized void setActivePlaylist(String playlistName) {
        if (playlistName == null || playlistName.isEmpty()) return;
        this.activePlaylistName = playlistName;
        this.source = Source.PLAYLIST;
        this.sourceExhausted = false;
        this.sourceRevision++;
        for (Track track : queueList) sessionIds.add(track.getId());
        setShuffle(false);
        setRepeatMode(RepeatMode.OFF);
        persistSmartState();
    }

    public synchronized boolean isSmartSuggested(long trackId) {
        return source != Source.MANUAL && smartTrackIds.contains(trackId);
    }
    public synchronized boolean isSmartQueueCaughtUp() {
        return source != Source.MANUAL && sourceExhausted && currentIndex >= queueList.size() - 1;
    }

    /** Switching to Manual freezes the current list. Smart sources never replace it. */
    public synchronized void setSource(Source selected) {
        if (selected == null || selected == source) return;
        if (selected == Source.PLAYLIST && (activePlaylistName == null || activePlaylistName.isEmpty())) return;
        source = selected;
        sourceExhausted = false;
        sourceRevision++;
        for (Track track : queueList) sessionIds.add(track.getId());
        if (source != Source.MANUAL) {
            setShuffle(false);
            setRepeatMode(RepeatMode.OFF);
        } else {
            smartTrackIds.clear();
        }
        persistSmartState();
    }

    /** Called on a worker, never on the audio/UI thread. Queries are outside the lock. */
    public boolean refreshSmartQueue() {
        Source requested;
        long revision;
        int requestedSlots;
        java.util.Set<Long> excluded;
        String playlistName;
        synchronized (this) {
            requested = source;
            revision = sourceRevision;
            playlistName = activePlaylistName;
            if (requested == Source.MANUAL || remainingSmartSlots() == 0) return false;
            requestedSlots = remainingSmartSlots();
            excluded = new java.util.HashSet<>(sessionIds);
            excluded.addAll(indexMap.keySet());
        }
        List<Track> candidates = switch (requested) {
            case NEW -> dbHelper.findRecentlyAdded(0, 0);
            case UNPLAYED -> dbHelper.findUnplayed();
            case REDISCOVER -> dbHelper.findRediscover(System.currentTimeMillis() - java.util.concurrent.TimeUnit.DAYS.toMillis(30));
            case PLAYLIST -> {
                if (tagRepos != null && playlistName != null && !playlistName.isEmpty()) {
                    apincer.music.core.model.SearchCriteria criteria = new apincer.music.core.model.SearchCriteria(apincer.music.core.model.SearchCriteria.TYPE.PLAYLIST);
                    criteria.setKeyword(playlistName);
                    yield tagRepos.findPlaylist(criteria);
                }
                yield Collections.emptyList();
            }
            default -> dbHelper.findMySongs();
        };
        if (candidates == null) return false;
        List<Track> available = new ArrayList<>();
        for (Track track : candidates) {
            if (track == null || track.isContainer() || track.getPath() == null) continue;
            if (excluded.contains(track.getId())) continue;
            if (requested == Source.DOWNLOADS && !apincer.music.core.utils.TagUtils.isOnDownloadDir(track)) continue;
            if (new java.io.File(track.getPath()).isFile()) available.add(track);
            if (available.size() >= requestedSlots) break;
        }
        synchronized (this) {
            if (source != requested || sourceRevision != revision) return false;
            sourceExhausted = available.size() < requestedSlots;
            int slots = remainingSmartSlots();
            List<Track> additions = new ArrayList<>();
            for (Track track : available) {
                if (slots == 0) break;
                if (sessionIds.add(track.getId()) && !indexMap.containsKey(track.getId())) {
                    additions.add(track);
                    slots--;
                }
            }
            if (additions.isEmpty()) return false;
            for (Track track : additions) smartTrackIds.add(track.getId());
            autoFilling = true;
            try { enqueuePlayingQueue(additions); }
            finally { autoFilling = false; }
            persistSmartState();
            return true;
        }
    }

    private int remainingSmartSlots() {
        return Math.max(0, SMART_LOOKAHEAD - Math.max(0, queueList.size() - Math.max(currentIndex, 0) - 1));
    }

    private void persistSmartState() {
        Track current = getCurrentTrack();
        StringBuilder state = new StringBuilder(source.name()).append('|')
                .append(current == null ? -1 : current.getId()).append('|');
        for (Long id : sessionIds) state.append(id).append(',');
        if (activePlaylistName != null) {
            state.append('|').append(activePlaylistName);
        }
        dbHelper.saveSmartQueueState(state.toString());
    }

    private void restoreSmartState() {
        String state = dbHelper.getSmartQueueState();
        if (state == null || state.isEmpty()) return;
        try {
            String[] parts = state.split("\\|", -1);
            source = Source.valueOf(parts[0]);
            Integer anchor = indexMap.get(Long.parseLong(parts[1]));
            if (anchor != null) currentIndex = playbackIndex = anchor;
            sessionIds.clear();
            for (String id : parts[2].split(",")) if (!id.isEmpty()) sessionIds.add(Long.parseLong(id));
            for (Track track : queueList) sessionIds.add(track.getId());
            if (parts.length > 3 && !parts[3].isEmpty()) {
                activePlaylistName = parts[3];
            }
            if (source == Source.PLAYLIST && (activePlaylistName == null || activePlaylistName.isEmpty())) {
                source = Source.MANUAL;
            }
            if (source != Source.MANUAL) { isShuffle = false; repeatMode = RepeatMode.OFF; }
        } catch (RuntimeException e) {
            source = Source.MANUAL;
            sessionIds.clear();
            activePlaylistName = null;
            Log.w(TAG, "Ignoring invalid smart queue state", e);
        }
    }

    public synchronized void setPlayingQueue(List<Track> songs) {
        if (songs == null || songs.isEmpty()) return;
        clearRemovedAnchor();
        source = Source.MANUAL;
        sourceRevision++;
        sessionIds.clear();
        smartTrackIds.clear();
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
        
        reshuffle();
        persistSmartState();
    }

    public synchronized void enqueuePlayingQueue(List<Track> songs) {
        if (songs == null || songs.isEmpty()) return;
        if (source != Source.MANUAL && !autoFilling) {
            for (int i = songs.size() - 1; i >= 0; i--) addPlayNext(songs.get(i));
            return;
        }
        
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
        if (source != Source.MANUAL) for (Track track : queueList) sessionIds.add(track.getId());
        
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
        persistSmartState();
        notifyQueueChanged();
    }

    public synchronized void addPlayingQueue(long trackId) {
        Track song = tagRepos.findById(trackId);
        if (song != null) {
            addPlayingQueue(song);
        }
    }

    public synchronized void addPlayingQueue(Track song) {
        if (song == null) return;
        smartTrackIds.remove(song.getId());
        if (source != Source.MANUAL) {
            addPlayNext(song);
            return;
        }

        // If track is already in queue, remove it from existing position first to prevent duplicates
        int existingIndex = -1;
        for (int i = 0; i < queueList.size(); i++) {
            if (queueList.get(i).getId() == song.getId()) {
                existingIndex = i;
                break;
            }
        }
        boolean wasCurrent = (existingIndex != -1 && existingIndex == currentIndex);
        boolean wasPlayback = (existingIndex != -1 && existingIndex == playbackIndex);
        if (existingIndex != -1) {
            shuffleIds.remove(song.getId()); // re-added: slot it in again among upcoming tracks
            queueList.remove(existingIndex);
            if (existingIndex < currentIndex) {
                currentIndex--;
            }
            if (existingIndex < playbackIndex) {
                playbackIndex--;
            }
        }

        queueList.add(song);
        int newIndex = queueList.size() - 1;
        if (wasCurrent) {
            currentIndex = newIndex;
        }
        if (wasPlayback) {
            playbackIndex = newIndex;
        }
        if (currentIndex == -1) {
            currentIndex = 0;
            playbackIndex = 0;
        }
        rebuildIndexMap();
        try {
            dbHelper.savePlayingQueue(queueList);
        } catch (Exception e) {
            Log.e(TAG, "Failed to persist playing queue", e);
        }
        updateShuffleOrder();
        notifyQueueChanged();
    }

    public synchronized void addPlayNext(Track song) {
        if (song == null || song.isContainer()) return;
        sessionIds.add(song.getId());
        smartTrackIds.remove(song.getId());

        // If track is already in queue, remove it from existing position first to prevent duplicates
        int existingIndex = -1;
        for (int i = 0; i < queueList.size(); i++) {
            if (queueList.get(i).getId() == song.getId()) {
                existingIndex = i;
                break;
            }
        }
        boolean wasCurrent = (existingIndex != -1 && existingIndex == currentIndex);
        boolean wasPlayback = (existingIndex != -1 && existingIndex == playbackIndex);
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
            if (anchorRemoved) {
                // The playing track was removed: go in front of the track lined up after it
                insertPos = queueList.size();
                for (int i = 0; i < queueList.size(); i++) {
                    if (anchorSuccessorId != null && queueList.get(i).getId() == anchorSuccessorId) {
                        insertPos = i;
                        break;
                    }
                }
                if (existingIndex != -1 && anchorSuccessorId != null && song.getId() == anchorSuccessorId) {
                    insertPos = existingIndex;
                }
            }
            queueList.add(insertPos, song);
            if (wasCurrent) {
                currentIndex = insertPos;
            }
            if (wasPlayback) {
                playbackIndex = insertPos;
            }
        }
        rebuildIndexMap();
        try {
            dbHelper.savePlayingQueue(queueList);
        } catch (Exception e) {
            Log.e(TAG, "Failed to persist playing queue", e);
        }
        updateShuffleOrder(song.getId());
        if (anchorRemoved) anchorSuccessorId = song.getId();
        persistSmartState();
        notifyQueueChanged();
    }

    public synchronized boolean containsTrack(long trackId) {
        return indexMap.containsKey(trackId);
    }

    public synchronized boolean containsTrack(Track track) {
        return track != null && indexMap.containsKey(track.getId());
    }

    public synchronized void savePlayingQueue(List<Track> songsInContext) {
        clearRemovedAnchor();
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
        reshuffle();
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
        // Hilt builds this while the service starts, on the main thread: load the saved queue in
        // the background there (a database read on the main thread can freeze the UI). Elsewhere,
        // including unit tests, load immediately as before.
        android.os.Looper main = android.os.Looper.getMainLooper();
        if (main != null && android.os.Looper.myLooper() == main) {
            apincer.music.core.utils.MusicMateExecutors.execute(this::initialLoad);
        } else {
            initialLoad();
        }
    }

    private boolean initialLoadDone;
    private final List<Runnable> whenLoadedCallbacks = new ArrayList<>();

    private void initialLoad() {
        loadPlayingQueue();
        List<Runnable> callbacks;
        synchronized (this) {
            initialLoadDone = true;
            callbacks = new ArrayList<>(whenLoadedCallbacks);
            whenLoadedCallbacks.clear();
        }
        for (Runnable callback : callbacks) callback.run();
    }

    /** Runs the callback once the saved queue has been loaded (immediately if it already has). */
    public void whenLoaded(Runnable callback) {
        synchronized (this) {
            if (!initialLoadDone) {
                whenLoadedCallbacks.add(callback);
                return;
            }
        }
        callback.run();
    }

    /**
     * Retrieves an unmodifiable view of the current tracks in the queue.
     *
     * @return A list of {@link Track} objects.
     */
    public List<Track> getSongs() {
        return Collections.unmodifiableList(queueList);
    }

    public synchronized int getQueueSize() {
        return queueList.size();
    }

    public synchronized int getCurrentIndex() {
        return currentIndex;
    }

    public synchronized Track getCurrentTrack() {
        if (currentIndex >= 0 && currentIndex < queueList.size()) {
            return queueList.get(currentIndex);
        }
        return null;
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
            clearRemovedAnchor();
            queueList.clear();
            indexMap.clear();
            for (int i = 0; i < songs.size(); i++) {
                Track track = songs.get(i);
                if (track == null) continue;
                // An unmounted volume must not erase the persisted queue.
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

            restoreSmartState();
            reshuffle();
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
            clearRemovedAnchor();
            currentIndex = idx;

            if (isShuffle && (shuffleOrder.isEmpty() || !shuffleIndexMap.containsKey(idx))) {
                updateShuffleOrder();
            }
        }
    }

    /*
    UPnP LastChange event fires
    Renderer switches track
     */
    public synchronized void setPlaybackTrack(Track track) {
        if (track == null) return;

        Integer idx = indexMap.get(track.getId());
        if (idx == null && anchorRemoved && track.getId() == removedAnchorId) {
            return; // the user removed it while it plays; keep it out of the queue
        }
        clearRemovedAnchor();
        if (idx == null) {
            queueList.add(track);
            idx = queueList.size() - 1;
            rebuildIndexMap();
            try {
                dbHelper.savePlayingQueue(queueList);
            } catch (Exception e) {
                Log.e(TAG, "Failed to persist auto-enqueued playback track", e);
            }
        }
        playbackIndex = idx;
        currentIndex = idx;
        sessionIds.add(track.getId());
        persistSmartState();

        if (isShuffle && (shuffleOrder.isEmpty() || !shuffleIndexMap.containsKey(idx))) {
            updateShuffleOrder();
        }
    }

    /**
     * Identifies the next logical track based on current {@link RepeatMode} and shuffle state.
     * This is primarily used for pre-loading the next URI for gapless UPnP playback and natural completion.
     *
     * @return The next {@link Track}, or {@code null} if the end of the queue is reached.
     */
    public synchronized Track getNextTrack() {
        return getNextTrack(false);
    }

    /**
     * Identifies the next track in the queue.
     * When {@code forceSkip} is true (such as when the user explicitly triggers Next/Skip),
     * this advances to the next track even if {@link RepeatMode#ONE} is active.
     *
     * @param forceSkip {@code true} to advance to the next track regardless of RepeatMode.ONE.
     * @return The next {@link Track}, or {@code null} if the end of the queue is reached.
     */
    public synchronized Track getNextTrack(boolean forceSkip) {
        if (queueList.isEmpty()) return null;

        if (anchorRemoved) {
            // The removed track cannot repeat, so Repeat One also continues with its follower
            Integer follower = anchorSuccessorId != null ? indexMap.get(anchorSuccessorId) : null;
            if (follower == null) return null;
            int nextIndex = skipUnplayable(follower);
            return nextIndex != -1 ? queueList.get(nextIndex) : null;
        }

        int baseIndex = (playbackIndex != -1) ? playbackIndex : currentIndex;
        if (baseIndex == -1) {
            baseIndex = 0;
            currentIndex = 0;
        }

        if (!forceSkip && repeatMode == RepeatMode.ONE && baseIndex != -1) {
            return queueList.get(baseIndex);
        }

        int nextIndex = skipUnplayable(getNextIndex(baseIndex));
        if (nextIndex != -1) {
            return queueList.get(nextIndex);
        }
        return null;
    }

    /** Smart sources skip tracks whose file is gone; bounded so Repeat All cannot spin forever. */
    private int skipUnplayable(int index) {
        int steps = 0;
        while (index != -1 && source != Source.MANUAL && steps++ < queueList.size()
                && (queueList.get(index).getPath() == null
                || !new java.io.File(queueList.get(index).getPath()).isFile())) {
            index = getNextIndex(index);
        }
        return steps > queueList.size() ? -1 : index;
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
        if (anchorRemoved && !queueList.isEmpty()) {
            Integer follower = anchorSuccessorId != null ? indexMap.get(anchorSuccessorId) : null;
            if (follower == null) {
                // The removed track was last: previous is the one now at the end of the order
                return queueList.get(isShuffle && !shuffleOrder.isEmpty()
                        ? shuffleOrder.get(shuffleOrder.size() - 1) : queueList.size() - 1);
            }
            int prevIndex = getPreviousIndex(follower);
            return prevIndex != -1 ? queueList.get(prevIndex) : null;
        }
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

    /** Play order by track id. It survives queue edits; shuffleOrder/shuffleIndexMap are its index view. */
    private final List<Long> shuffleIds = new ArrayList<>();

    /** Starts a fresh order: with shuffle on, the playing track first and the rest random. */
    private synchronized void reshuffle() {
        shuffleIds.clear();
        updateShuffleOrder(null);
    }

    private void updateShuffleOrder() {
        updateShuffleOrder(null);
    }

    /**
     * Brings the play order in line with the queue after an edit, without reshuffling:
     * removed tracks drop out and tracks new to the order are slotted in at random after the
     * playing one, so tracks already played do not come back. {@code playNextId} goes
     * directly after the playing track. With shuffle off the order is the queue order.
     */
    private synchronized void updateShuffleOrder(Long playNextId) {
        shuffleOrder.clear();
        shuffleIndexMap.clear();
        if (queueList.isEmpty()) {
            shuffleIds.clear();
            return;
        }

        java.util.Set<Long> known = new java.util.HashSet<>(shuffleIds);
        if (!isShuffle) {
            shuffleIds.clear();
            for (Track track : queueList) shuffleIds.add(track.getId());
        } else if (known.isEmpty()) {
            Long anchor = anchorTrackId();
            for (Track track : queueList) shuffleIds.add(track.getId());
            if (anchor != null) shuffleIds.remove(anchor);
            Collections.shuffle(shuffleIds);
            if (anchor != null) shuffleIds.add(0, anchor);
        } else {
            mergeIntoShuffle(playNextId);
        }

        // The removed playing track was last: the first newly added track continues the queue
        if (anchorRemoved && anchorSuccessorId == null && !known.isEmpty()) {
            for (Long id : shuffleIds) {
                if (!known.contains(id)) {
                    anchorSuccessorId = id;
                    break;
                }
            }
        }

        for (Long id : shuffleIds) {
            shuffleIndexMap.put(indexMap.get(id), shuffleOrder.size());
            shuffleOrder.add(indexMap.get(id));
        }
    }

    private void mergeIntoShuffle(Long playNextId) {
        shuffleIds.removeIf(id -> !indexMap.containsKey(id));
        Long anchor = anchorRemoved ? null : anchorTrackId();
        boolean placeNext = playNextId != null && !playNextId.equals(anchor) && indexMap.containsKey(playNextId);
        if (placeNext) shuffleIds.remove(playNextId);

        // Upcoming tracks are inserted after this shuffle position
        int insertAfter;
        if (anchorRemoved) {
            int follower = anchorSuccessorId != null ? shuffleIds.indexOf(anchorSuccessorId) : -1;
            insertAfter = follower != -1 ? follower - 1 : shuffleIds.size() - 1;
        } else if (anchor != null) {
            insertAfter = shuffleIds.indexOf(anchor);
            if (insertAfter == -1) {
                shuffleIds.add(0, anchor);
                insertAfter = 0;
            }
        } else {
            insertAfter = -1;
        }

        if (placeNext) shuffleIds.add(++insertAfter, playNextId);

        java.util.Set<Long> present = new java.util.HashSet<>(shuffleIds);
        for (Track track : queueList) {
            if (present.add(track.getId())) {
                int slots = shuffleIds.size() - insertAfter;
                shuffleIds.add(insertAfter + 1 + ThreadLocalRandom.current().nextInt(slots), track.getId());
            }
        }
    }

    private Long anchorTrackId() {
        int base = (playbackIndex != -1) ? playbackIndex : currentIndex;
        return (base >= 0 && base < queueList.size()) ? queueList.get(base).getId() : null;
    }

    /**
     * Enables or disables shuffle mode and rebuilds the shuffle order.
     *
     * @param enabled true to enable shuffle, false to disable
     */
    public synchronized void setShuffle(boolean enabled) {
        if (source != Source.MANUAL && enabled) return;
        if (this.isShuffle == enabled) return;
        this.isShuffle = enabled;
        dbHelper.saveShuffleMode(enabled);
        reshuffle();
        Log.d(TAG, "Shuffle mode set to: " + enabled);
        notifyQueueChanged();
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
        if (source != Source.MANUAL && mode != RepeatMode.OFF) return;
        if (this.repeatMode == mode) return;
        this.repeatMode = mode;
        dbHelper.saveRepeatMode(mode != null ? mode.name() : RepeatMode.OFF.name());
        Log.d(TAG, "Repeat mode set to: " + mode);
        notifyQueueChanged();
    }

    /**
     * Returns the current repeat mode.
     */
    public RepeatMode getRepeatMode() {
        return repeatMode;
    }

    public void addToPlayingQueue(Track song) {
        if (song == null) return;
        try {
            dbHelper.addToPlayingQueue(song);
        } catch (Exception e) {
            Log.e(TAG, "Failed to add track to playing queue in database", e);
        }
    }

    public synchronized void moveTrack(int fromPos, int toPos) {
        if (fromPos < 0 || fromPos >= queueList.size() || toPos < 0 || toPos >= queueList.size()) return;
        Track currentTrack = (currentIndex >= 0 && currentIndex < queueList.size()) ? queueList.get(currentIndex) : null;
        Track playbackTrack = (playbackIndex >= 0 && playbackIndex < queueList.size()) ? queueList.get(playbackIndex) : null;

        Track moved = queueList.remove(fromPos);
        queueList.add(toPos, moved);
        rebuildIndexMap();

        if (currentTrack != null && indexMap.containsKey(currentTrack.getId())) {
            currentIndex = indexMap.get(currentTrack.getId());
        }
        if (playbackTrack != null && indexMap.containsKey(playbackTrack.getId())) {
            playbackIndex = indexMap.get(playbackTrack.getId());
        }

        dbHelper.savePlayingQueue(queueList);
        updateShuffleOrder();
        notifyQueueChanged();
    }

    public synchronized void removeTrack(int position) {
        if (position < 0 || position >= queueList.size()) return;
        Track removed = queueList.get(position);

        // Removing the playing track (or, once it is gone, the track lined up after it)
        // moves the anchor to that track's follower, captured before the order changes.
        int anchorIndex = (playbackIndex != -1) ? playbackIndex : currentIndex;
        boolean removesAnchor = anchorRemoved
                ? removed != null && anchorSuccessorId != null && removed.getId() == anchorSuccessorId
                : position == anchorIndex;
        if (removesAnchor) {
            int follower = getNextIndex(position);
            if (!anchorRemoved) {
                removedAnchorId = removed != null ? removed.getId() : -1;
            }
            anchorRemoved = true;
            anchorSuccessorId = (follower != -1 && follower != position) ? queueList.get(follower).getId() : null;
        }
        if (removed != null) {
            sessionIds.add(removed.getId());
            smartTrackIds.remove(removed.getId());
        }
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
        persistSmartState();
        notifyQueueChanged();
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

    public synchronized void emptyPlayingQueue() {
        clearRemovedAnchor();
        source = Source.MANUAL;
        sourceRevision++;
        sessionIds.clear();
        smartTrackIds.clear();
        dbHelper.emptyPlayingQueue();
        queueList.clear();
        indexMap.clear();
        shuffleOrder.clear();
        shuffleIndexMap.clear();
        shuffleIds.clear();
        currentIndex = -1;
        playbackIndex = -1;
        persistSmartState();
    }
}
