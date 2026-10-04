package apincer.music.server.jupnp.content;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.jupnp.support.model.DIDLObject;
import org.jupnp.support.model.SortCriterion;
import org.jupnp.support.model.item.MusicTrack;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import apincer.music.core.model.AudioTag;
import apincer.music.core.model.Track;

public class BrowseSortTest {

    private static MusicTrack item(String title, String artist, String album) {
        return new MusicTrack("id-" + title, "p", title, artist, album, artist,
                new org.jupnp.support.model.Res());
    }

    private static List<String> titles(List<? extends DIDLObject> list) {
        return list.stream().map(DIDLObject::getTitle).collect(Collectors.toList());
    }

    private final List<DIDLObject> items = new ArrayList<>(Arrays.asList(
            item("b song", "Zed", "Alpha"), item("A song", "Abba", "Gamma"), item("c song", "Miles", "Beta")));

    @Test
    public void title_ascendingAndDescending_ignoringCase() {
        items.sort(BrowseSort.didl(SortCriterion.valueOf("+dc:title")));
        assertEquals(Arrays.asList("A song", "b song", "c song"), titles(items));
        items.sort(BrowseSort.didl(SortCriterion.valueOf("-dc:title")));
        assertEquals(Arrays.asList("c song", "b song", "A song"), titles(items));
    }

    @Test
    public void artistThenTitle_andAlbum() {
        items.sort(BrowseSort.didl(SortCriterion.valueOf("+upnp:artist,+dc:title")));
        assertEquals(Arrays.asList("A song", "c song", "b song"), titles(items));
        items.sort(BrowseSort.didl(SortCriterion.valueOf("+upnp:album")));
        assertEquals(Arrays.asList("b song", "c song", "A song"), titles(items));
    }

    @Test
    public void unsupportedOrEmptyCriteria_meanNoSort() {
        assertNull(BrowseSort.didl(SortCriterion.valueOf("")));
        assertNull(BrowseSort.didl(SortCriterion.valueOf("+upnp:rating")));
        assertNull(BrowseSort.didl(null));
    }

    @Test
    public void tracks_sortTheSameWay() {
        AudioTag a = new AudioTag(); a.setTitle("b"); a.setArtist("Zed"); a.setYear("1999");
        AudioTag b = new AudioTag(); b.setTitle("a"); b.setArtist("Abba"); b.setYear("2005");
        List<Track> tracks = new ArrayList<>(Arrays.asList(a, b));
        tracks.sort(BrowseSort.tracks(SortCriterion.valueOf("-dc:date")));
        assertEquals("a", tracks.get(0).getTitle());
        tracks.sort(BrowseSort.tracks(SortCriterion.valueOf("+dc:title")));
        assertEquals("a", tracks.get(0).getTitle());
        tracks.sort(BrowseSort.tracks(SortCriterion.valueOf("-upnp:artist")));
        assertEquals("b", tracks.get(0).getTitle());
    }
}
