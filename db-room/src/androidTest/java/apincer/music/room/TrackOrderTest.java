package apincer.music.room;

import static org.junit.Assert.assertEquals;

import androidx.room.Room;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.ArrayList;
import java.util.List;

import apincer.music.room.dao.TrackDao;
import apincer.music.room.entity.TrackEntity;

@RunWith(AndroidJUnit4.class)
public class TrackOrderTest {
    private MusicRoomDatabase room;
    private TrackDao dao;

    @Before
    public void open() {
        room = Room.inMemoryDatabaseBuilder(
                InstrumentationRegistry.getInstrumentation().getTargetContext(),
                MusicRoomDatabase.class).build();
        dao = room.trackDao();
    }

    @After
    public void close() {
        room.close();
    }

    @Test
    public void artistTracksPlayInAlbumAndTrackOrder() {
        insert(1, "/m/Aurora Lane/Second/10 Afterglow.flac", "Second", "10", "Afterglow");
        insert(2, "/m/Aurora Lane/First/02 Coastline Lights.flac", "First", "2", "Coastline Lights");
        insert(3, "/m/Aurora Lane/Second/2 Midnight Signal.flac", "Second", "2", "Midnight Signal");
        insert(4, "/m/Aurora Lane/First/01 Neon Avenue.flac", "First", "1", "Neon Avenue");

        assertEquals(List.of("Neon Avenue", "Coastline Lights", "Midnight Signal", "Afterglow"),
                titles(dao.findByArtist("Aurora Lane", 0, -1)));
    }

    @Test
    public void folderTracksPlayInFileOrderAndCueTracksInTrackOrder() {
        insert(1, "/m/Live/02 Encore.flac", "Live", "2", "Encore");
        insert(2, "/m/Live/01 Show.flac", "Live", "3", "Show part 3");
        insert(3, "/m/Live/01 Show.flac", "Live", "1", "Show part 1");
        insert(4, "/m/Live/01 Show.flac", "Live", "2", "Show part 2");

        assertEquals(List.of("Show part 1", "Show part 2", "Show part 3", "Encore"),
                titles(dao.findInPath("/m/Live/")));
    }

    @Test
    public void albumTracksPlayInNumericTrackOrder() {
        insert(1, "/m/Aurora Lane/Long/10 Ten.flac", "Long", "10", "Ten");
        insert(2, "/m/Aurora Lane/Long/02 Two.flac", "Long", "2", "Two");
        insert(3, "/m/Aurora Lane/Long/01 One.flac", "Long", "1", "One");

        assertEquals(List.of("One", "Two", "Ten"),
                titles(dao.findByAlbumAndAlbumArtist("Long", "Aurora Lane", 0, -1)));
    }

    private void insert(long id, String path, String album, String track, String title) {
        TrackEntity entity = new TrackEntity();
        entity.setId(id);
        entity.setUniqueKey("key-" + id);
        entity.setPath(path);
        entity.setAlbum(album);
        entity.setTrack(track);
        entity.setTitle(title);
        entity.setArtist("Aurora Lane");
        dao.insert(entity);
    }

    private static List<String> titles(List<TrackEntity> tracks) {
        List<String> titles = new ArrayList<>();
        for (TrackEntity track : tracks) titles.add(track.getTitle());
        return titles;
    }
}
