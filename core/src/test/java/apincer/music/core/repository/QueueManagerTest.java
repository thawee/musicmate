package apincer.music.core.repository;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Before;
import org.junit.Test;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import apincer.music.core.model.Track;
import apincer.music.core.repository.spi.DbHelper;

public class QueueManagerTest {

    private QueueManager queueManager;
    private List<Track> savedQueue;

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
    }

    @Test
    public void getRandomTrack_emptyQueue_returnsNull() {
        assertNull(queueManager.getRandomTrack());
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
}
