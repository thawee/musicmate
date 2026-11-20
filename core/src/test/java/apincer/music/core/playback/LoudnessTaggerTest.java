package apincer.music.core.playback;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import apincer.music.core.model.AudioTag;
import apincer.music.core.model.Track;

public class LoudnessTaggerTest {
    private final Map<String, Loudness> loudness = new HashMap<>();
    private final Map<String, Loudness[]> written = new LinkedHashMap<>();
    private final Map<String, Boolean> reported = new LinkedHashMap<>();
    private List<Track> library = new ArrayList<>();

    private final LoudnessTagger tagger = new LoudnessTagger(
            track -> loudness.get(track.getPath()),
            (path, track, album) -> {
                written.put(path, new Loudness[]{track, album});
                return true;
            },
            track -> track.getAlbum().isEmpty() ? List.of() : library);

    private final LoudnessTagger.Listener listener = (track, ok) -> reported.put(track.getPath(), ok);

    private Track track(String path, String album, double lufs) {
        AudioTag tag = new AudioTag();
        tag.setPath(path);
        tag.setAlbum(album);
        tag.setAlbumArtist("Artist");
        if (!Double.isNaN(lufs)) loudness.put(path, new Loudness(lufs, -1.0, 200));
        return tag;
    }

    @Test
    public void aWholeAlbumGetsOneAlbumGainOnEveryTrack() {
        Track a = track("/a.flac", "Album", -10.0);
        Track b = track("/b.flac", "Album", -14.0);
        library = Arrays.asList(a, b);

        tagger.tag(Arrays.asList(a, b), listener);

        Loudness albumA = written.get("/a.flac")[1];
        assertNotNull(albumA);
        assertEquals(-8.0, written.get("/a.flac")[0].gainDb(), 1e-9);
        assertEquals(-4.0, written.get("/b.flac")[0].gainDb(), 1e-9);
        assertEquals(albumA.gainDb(), written.get("/b.flac")[1].gainDb(), 1e-9);
    }

    @Test
    public void partOfAnAlbumKeepsTheFilesAlbumGain() {
        Track a = track("/a.flac", "Album", -10.0);
        Track b = track("/b.flac", "Album", -14.0);
        library = Arrays.asList(a, b);

        tagger.tag(List.of(a), listener);

        assertNull(written.get("/a.flac")[1]);
        assertEquals(1, written.size());
    }

    @Test
    public void aTrackThatFailsToMeasureIsReportedAndBlocksAlbumGain() {
        Track a = track("/a.flac", "Album", -10.0);
        Track silent = track("/b.flac", "Album", Double.NaN);
        library = Arrays.asList(a, silent);

        tagger.tag(Arrays.asList(a, silent), listener);

        assertEquals(Boolean.FALSE, reported.get("/b.flac"));
        assertEquals(Boolean.TRUE, reported.get("/a.flac"));
        assertNull(written.get("/a.flac")[1]);
        assertEquals(1, written.size());
    }

    @Test
    public void tracksWithoutAnAlbumStandAloneAndGetNoAlbumGain() {
        Track a = track("/a.mp3", "", -10.0);
        Track b = track("/b.mp3", "", -12.0);
        Track c = track("/c.flac", "Album", -12.0);

        List<List<Track>> groups = new ArrayList<>(LoudnessTagger.byAlbum(Arrays.asList(a, c, b)));
        assertEquals(3, groups.size());

        tagger.tag(groups.get(0), listener);
        assertNull(written.get("/a.mp3")[1]);
    }
}
