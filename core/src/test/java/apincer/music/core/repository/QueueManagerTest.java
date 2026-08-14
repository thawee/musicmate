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
}
