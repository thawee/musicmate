package apincer.music.core.repository;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.lang.reflect.Proxy;
import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import apincer.music.core.model.Track;
import apincer.music.core.model.AudioTag;
import apincer.music.core.repository.spi.DbHelper;

public class QueueManagerTest {

    @Rule
    public final TemporaryFolder temporaryFolder = new TemporaryFolder();

    private QueueManager queueManager;
    private List<Track> savedQueue;
    private final List<Track> smartCandidates = new ArrayList<>();
    private String smartState = "";
    private TagRepository repository;
    private Runnable queryHook;
    private final List<Track> unplayedCandidates = new ArrayList<>();
    private final List<Track> rediscoverCandidates = new ArrayList<>();
    private long rediscoverCutoff;

    private Track createDummyTrack(long id, String title) {
        return (Track) Proxy.newProxyInstance(
                Track.class.getClassLoader(),
                new Class<?>[]{Track.class},
                (proxy, method, args) -> {
                    String name = method.getName();
                    if ("getId".equals(name)) return id;
                    if ("getTitle".equals(name)) return title;
                    if ("getUniqueKey".equals(name)) return "key_" + id;
                    if (method.getReturnType().equals(boolean.class)) return false;
                    if (method.getReturnType().equals(int.class)) return 0;
                    if (method.getReturnType().equals(long.class)) return 0L;
                    if (method.getReturnType().equals(double.class)) return 0.0;
                    return null;
                }
        );
    }

    @Before
    public void setUp() {
        savedQueue = new ArrayList<>();
        DbHelper dummyDbHelper = (DbHelper) Proxy.newProxyInstance(
                DbHelper.class.getClassLoader(),
                new Class<?>[]{DbHelper.class},
                (proxy, method, args) -> {
                    String methodName = method.getName();
                    if ("findUnplayed".equals(methodName)) return new ArrayList<>(unplayedCandidates);
                    if ("findRediscover".equals(methodName)) {
                        rediscoverCutoff = (long) args[0];
                        return new ArrayList<>(rediscoverCandidates);
                    }
                    if ("getSmartQueueState".equals(methodName)) return smartState;
                    if ("saveSmartQueueState".equals(methodName)) { smartState = (String) args[0]; return null; }
                    if ("findMySongs".equals(methodName) || "findRecentlyAdded".equals(methodName)) {
                        if (queryHook != null) queryHook.run();
                        return new ArrayList<>(smartCandidates);
                    }
                    if ("getPlayingQueue".equals(methodName)) {
                        return savedQueue;
                    } else if ("savePlayingQueue".equals(methodName)) {
                        if (args != null && args.length > 0 && args[0] instanceof List) {
                            savedQueue = new ArrayList<>((List<Track>) args[0]);
                        }
                        return null;
                    } else if ("getShuffleMode".equals(methodName)) {
                        return false;
                    } else if ("getRepeatMode".equals(methodName)) {
                        return "OFF";
                    } else if ("saveShuffleMode".equals(methodName) || "saveRepeatMode".equals(methodName)) {
                        return null;
                    } else if ("addToPlayingQueue".equals(methodName)) {
                        return null;
                    } else if ("emptyPlayingQueue".equals(methodName)) {
                        savedQueue.clear();
                        return null;
                    }
                    if (method.getReturnType().equals(boolean.class)) return false;
                    if (method.getReturnType().equals(int.class)) return 0;
                    if (method.getReturnType().equals(long.class)) return 0L;
                    return null;
                }
        );

        TagRepository dummyTagRepos = new TagRepository(null, dummyDbHelper);
        queueManager = new QueueManager(dummyTagRepos);
        repository = dummyTagRepos;
    }

    private Track playable(long id) throws Exception {
        AudioTag track = new AudioTag();
        track.setId(id);
        track.setPath(temporaryFolder.newFile("song-" + id + ".flac").getPath());
        track.setUniqueKey("song-" + id);
        return track;
    }

    @Test
    public void unplayedUsesHistoryQueryAndPersistsSource() throws Exception {
        smartCandidates.add(playable(1));
        unplayedCandidates.add(playable(2));
        queueManager.setSource(QueueManager.Source.UNPLAYED);
        queueManager.refreshSmartQueue();
        assertEquals(2, queueManager.getCurrentTrack().getId());
        assertEquals(QueueManager.Source.UNPLAYED, new QueueManager(repository).getSource());
    }

    @Test
    public void playlistSourceStateAndPersistence() throws Exception {
        queueManager.setActivePlaylist("Studio Masters");
        assertEquals(QueueManager.Source.PLAYLIST, queueManager.getSource());
        assertEquals("Studio Masters", queueManager.getActivePlaylistName());

        QueueManager restored = new QueueManager(repository);
        assertEquals(QueueManager.Source.PLAYLIST, restored.getSource());
        assertEquals("Studio Masters", restored.getActivePlaylistName());
    }

    @Test
    public void rediscoverUsesThirtyDayCutoffAndRetainsOldestFirstOrder() throws Exception {
        rediscoverCandidates.add(playable(5));
        rediscoverCandidates.add(playable(3));
        long before = System.currentTimeMillis() - java.util.concurrent.TimeUnit.DAYS.toMillis(30);
        queueManager.setSource(QueueManager.Source.REDISCOVER);
        queueManager.refreshSmartQueue();
        long after = System.currentTimeMillis() - java.util.concurrent.TimeUnit.DAYS.toMillis(30);
        assertTrue(rediscoverCutoff >= before && rediscoverCutoff <= after);
        assertEquals(5, queueManager.getCurrentTrack().getId());
        assertEquals(3, queueManager.getNextTrack().getId());
        queueManager.emptyPlayingQueue();
        // Queue clearing must not clear listening-history eligibility.
        queueManager.setSource(QueueManager.Source.REDISCOVER);
        queueManager.refreshSmartQueue();
        assertEquals(2, queueManager.getQueueSize());
    }

    @Test
    public void smartQueue_boundsRefillAndAppendsNewCandidates() throws Exception {
        for (int i = 1; i <= 30; i++) smartCandidates.add(playable(i));
        queueManager.setSource(QueueManager.Source.NEW);
        queueManager.refreshSmartQueue();
        assertEquals(20, queueManager.getQueueSize());
        queueManager.setPlaybackTrack(smartCandidates.get(18));
        long stableNext = queueManager.getNextTrack().getId();
        queueManager.refreshSmartQueue();
        assertEquals(30, queueManager.getQueueSize());
        assertEquals(stableNext, queueManager.getNextTrack().getId());
        queueManager.setPlaybackTrack(smartCandidates.get(29));
        queueManager.refreshSmartQueue();
        assertTrue(queueManager.isSmartQueueCaughtUp());
        smartCandidates.add(playable(31));
        queueManager.refreshSmartQueue();
        assertEquals(31, queueManager.getNextTrack().getId());
    }

    @Test
    public void smartQueue_clearDuringLookupCannotRepopulateQueue() throws Exception {
        smartCandidates.add(playable(1));
        queueManager.setSource(QueueManager.Source.NEW);
        queryHook = () -> queueManager.emptyPlayingQueue();
        queueManager.refreshSmartQueue();
        assertEquals(0, queueManager.getQueueSize());
        assertEquals(QueueManager.Source.MANUAL, queueManager.getSource());
    }

    @Test
    public void smartQueue_downloadPredicateAndMissingFilesAreRespected() throws Exception {
        Track download = playable(1);
        AudioTag managedPath = new AudioTag();
        managedPath.setId(2);
        File music = temporaryFolder.newFolder("Music");
        File file = new File(music, "album.flac");
        assertTrue(file.createNewFile());
        managedPath.setPath(file.getPath());
        smartCandidates.add(managedPath); smartCandidates.add(download);
        queueManager.setSource(QueueManager.Source.DOWNLOADS);
        queueManager.refreshSmartQueue();
        assertEquals(1, queueManager.getQueueSize());
        assertEquals(1, queueManager.getCurrentTrack().getId());
        Track gone = playable(3), available = playable(4);
        queueManager.enqueuePlayingQueue(java.util.Arrays.asList(gone, available));
        assertTrue(new File(gone.getPath()).delete());
        assertEquals(4, queueManager.getNextTrack().getId());
    }

    @Test
    public void smartQueue_preservesNextAndPrioritizesManualChoice() throws Exception {
        Track current = playable(1), next = playable(2), suggestion = playable(3), chosen = playable(4);
        queueManager.setPlayingQueue(java.util.Arrays.asList(current, next));
        queueManager.setSource(QueueManager.Source.NEW);
        smartCandidates.add(suggestion);
        queueManager.refreshSmartQueue();
        assertEquals(2, queueManager.getNextTrack().getId());
        queueManager.addPlayingQueue(chosen);
        assertEquals(4, queueManager.getNextTrack().getId());
        assertEquals(1, queueManager.getCurrentTrack().getId());
    }

    @Test
    public void smartQueue_removedSuggestionsDoNotReturnAfterRestart() throws Exception {
        Track first = playable(1), second = playable(2);
        smartCandidates.add(first); smartCandidates.add(second);
        queueManager.setSource(QueueManager.Source.DOWNLOADS);
        queueManager.refreshSmartQueue();
        queueManager.setPlaybackTrack(first);
        queueManager.removeTrackById(2);
        QueueManager restored = new QueueManager(repository);
        restored.refreshSmartQueue();
        assertEquals(QueueManager.Source.DOWNLOADS, restored.getSource());
        assertEquals(1, restored.getQueueSize());
        assertNull(restored.getNextTrack());
    }

    @Test
    public void smartQueue_restoresAnchorAndManualFreezesRefill() throws Exception {
        Track first = playable(1), second = playable(2);
        smartCandidates.add(first); smartCandidates.add(second);
        queueManager.setSource(QueueManager.Source.NEW);
        queueManager.refreshSmartQueue();
        queueManager.setPlaybackTrack(second);
        QueueManager restored = new QueueManager(repository);
        assertEquals(2, restored.getCurrentTrack().getId());
        assertNull(restored.getNextTrack());
        restored.setSource(QueueManager.Source.MANUAL);
        smartCandidates.add(playable(3));
        restored.refreshSmartQueue();
        assertEquals(2, restored.getQueueSize());
        restored.emptyPlayingQueue();
        assertEquals(QueueManager.Source.MANUAL, new QueueManager(repository).getSource());
    }

    @Test
    public void getRandomTrack_emptyQueue_returnsNull() {
        assertNull(queueManager.getRandomTrack());
    }

    @Test
    public void loadPlayingQueue_temporarilyUnavailableStorage_preservesQueueAndPersistence() {
        AudioTag track = new AudioTag();
        track.setId(42L);
        track.setUniqueKey("removable-track");
        track.setPath(new File(temporaryFolder.getRoot(), "unmounted-volume/album/song.flac").getPath());
        savedQueue.add(track);

        queueManager.loadPlayingQueue(true);

        assertEquals(1, queueManager.getQueueSize());
        assertEquals(42L, queueManager.getSongs().get(0).getId());
        assertEquals(1, savedQueue.size());
        assertEquals(42L, savedQueue.get(0).getId());
    }

    @Test
    public void getRandomTrack_singleTrack_returnsThatTrack() {
        Track track = createDummyTrack(101L, "Track 1");

        queueManager.savePlayingQueue(Collections.singletonList(track));

        Track random = queueManager.getRandomTrack();
        assertNotNull(random);
        assertEquals(101L, random.getId());
        assertEquals("Track 1", random.getTitle());
    }

    @Test
    public void getRandomTrack_multipleTracks_returnsValidTrackFromQueue() {
        List<Track> tracks = new ArrayList<>();
        for (int i = 1; i <= 10; i++) {
            tracks.add(createDummyTrack((long) i, "Track " + i));
        }

        queueManager.savePlayingQueue(tracks);

        for (int i = 0; i < 50; i++) {
            Track random = queueManager.getRandomTrack();
            assertNotNull(random);
            assertTrue(random.getId() >= 1L && random.getId() <= 10L);
        }
    }

    @Test
    public void containsTrack_checksIndexMapCorrectly() {
        Track t1 = createDummyTrack(1L, "Track 1");
        Track t2 = createDummyTrack(2L, "Track 2");
        queueManager.savePlayingQueue(Collections.singletonList(t1));

        assertTrue(queueManager.containsTrack(1L));
        assertTrue(queueManager.containsTrack(t1));
        org.junit.Assert.assertFalse(queueManager.containsTrack(2L));
        org.junit.Assert.assertFalse(queueManager.containsTrack(t2));
    }

    @Test
    public void moveTrack_preservesActiveCurrentTrackIndex() {
        Track t1 = createDummyTrack(1L, "Track 1");
        Track t2 = createDummyTrack(2L, "Track 2");
        Track t3 = createDummyTrack(3L, "Track 3");
        List<Track> list = new ArrayList<>();
        list.add(t1);
        list.add(t2);
        list.add(t3);
        queueManager.savePlayingQueue(list);
        queueManager.setCurrentTrack(t1); // currentIndex = 0

        // Move t3 (index 2) to index 0. Now order is t3, t1, t2. t1 should now be at index 1!
        queueManager.moveTrack(2, 0);
        assertEquals(1, queueManager.getCurrentIndex());
        assertEquals(1L, queueManager.getCurrentTrack().getId());
    }

    @Test
    public void shuffleOrder_preservesSequenceAcrossPlaybackTransitions() {
        List<Track> tracks = new ArrayList<>();
        for (long i = 1; i <= 5; i++) {
            tracks.add(createDummyTrack(i, "Track " + i));
        }
        queueManager.savePlayingQueue(tracks);
        queueManager.setShuffle(true);

        // First track is tracks.get(0) -> track 1
        Track current = tracks.get(0);
        queueManager.setPlaybackTrack(current);

        // Get next track according to shuffle
        Track next1 = queueManager.getNextTrack();
        assertNotNull(next1);

        // Transition to next1
        queueManager.setPlaybackTrack(next1);

        // The next track after next1 must NOT be current (track 1) again
        Track next2 = queueManager.getNextTrack();
        assertNotNull(next2);
        assertTrue(next2.getId() != current.getId());
    }

    @Test
    public void emptyPlayingQueue_resetsAllState() {
        Track t1 = createDummyTrack(1L, "Track 1");
        queueManager.savePlayingQueue(Collections.singletonList(t1));
        queueManager.setShuffle(true);
        assertEquals(1, queueManager.getQueueSize());

        queueManager.emptyPlayingQueue();
        assertEquals(0, queueManager.getQueueSize());
        assertEquals(-1, queueManager.getCurrentIndex());
        assertNull(queueManager.getNextTrack());
    }

    @Test
    public void setPlaybackTrack_unknownTrack_autoEnqueuesAndSetsPlaybackIndex() {
        Track t1 = createDummyTrack(1L, "Track 1");
        queueManager.savePlayingQueue(Collections.singletonList(t1));
        assertEquals(1, queueManager.getQueueSize());

        Track tExternal = createDummyTrack(999L, "External Stream");
        queueManager.setPlaybackTrack(tExternal);

        assertEquals(2, queueManager.getQueueSize());
        assertEquals(1, queueManager.getCurrentIndex());
        assertNotNull(queueManager.getCurrentTrack());
        assertEquals(999L, queueManager.getCurrentTrack().getId());
        assertTrue(queueManager.containsTrack(999L));
    }

    @Test
    public void addPlayingQueue_existingTrack_movesToEndWithoutDuplicate() {
        Track t1 = createDummyTrack(1L, "Track 1");
        Track t2 = createDummyTrack(2L, "Track 2");
        List<Track> list = new ArrayList<>();
        list.add(t1);
        list.add(t2);
        queueManager.savePlayingQueue(list);
        assertEquals(2, queueManager.getQueueSize());

        // Re-adding t1 should reposition t1 to the end, total size remains 2
        queueManager.addPlayingQueue(t1);
        assertEquals(2, queueManager.getQueueSize());
        assertEquals(2L, queueManager.getSongs().get(0).getId());
        assertEquals(1L, queueManager.getSongs().get(1).getId());
    }

    @Test
    public void addPlayingQueue_currentTrack_updatesCurrentIndexToEnd() {
        Track t1 = createDummyTrack(1L, "Track 1");
        Track t2 = createDummyTrack(2L, "Track 2");
        List<Track> list = new ArrayList<>();
        list.add(t1);
        list.add(t2);
        queueManager.savePlayingQueue(list);
        queueManager.setCurrentTrack(t1); // index 0
        assertEquals(0, queueManager.getCurrentIndex());

        // Re-adding the currently playing track t1 moves it to the end (index 1)
        queueManager.addPlayingQueue(t1);
        assertEquals(2, queueManager.getQueueSize());
        assertEquals(1, queueManager.getCurrentIndex());
        assertEquals(t1.getId(), queueManager.getCurrentTrack().getId());
    }

    @Test
    public void addPlayNext_currentTrack_preservesCurrentIndex() {
        Track t1 = createDummyTrack(1L, "Track 1");
        Track t2 = createDummyTrack(2L, "Track 2");
        List<Track> list = new ArrayList<>();
        list.add(t1);
        list.add(t2);
        queueManager.savePlayingQueue(list);
        queueManager.setCurrentTrack(t1); // index 0

        queueManager.addPlayNext(t1);
        assertEquals(2, queueManager.getQueueSize());
        assertEquals(t1.getId(), queueManager.getCurrentTrack().getId());
    }
}
