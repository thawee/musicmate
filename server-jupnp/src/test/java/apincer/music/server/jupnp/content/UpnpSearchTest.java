package apincer.music.server.jupnp.content;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.function.Predicate;

import apincer.music.core.model.AudioTag;
import apincer.music.core.model.Track;

public class UpnpSearchTest {

    private static Track track(String title, String artist, String album, String genre) {
        AudioTag t = new AudioTag();
        t.setTitle(title);
        t.setArtist(artist);
        t.setAlbum(album);
        t.setGenre(genre);
        return t;
    }

    private static final Track LOVE = track("Love Me Do", "The Beatles", "Please Please Me", "Rock");
    private static final Track JAZZ = track("So What", "Miles Davis", "Kind of Blue", "Jazz");

    @Test
    public void star_matchesEverything() {
        Predicate<Track> p = UpnpSearch.parse("*");
        assertTrue(p.test(LOVE));
        assertTrue(p.test(JAZZ));
    }

    @Test
    public void contains_isCaseInsensitive() {
        Predicate<Track> p = UpnpSearch.parse("dc:title contains \"love\"");
        assertTrue(p.test(LOVE));
        assertFalse(p.test(JAZZ));
    }

    @Test
    public void bubbleUpnpStyle_classAndTitle() {
        Predicate<Track> p = UpnpSearch.parse(
                "(upnp:class derivedfrom \"object.item.audioItem\" and dc:title contains \"what\")");
        assertTrue(p.test(JAZZ));
        assertFalse(p.test(LOVE));
    }

    @Test
    public void audioClass_matchesTracks_containerClass_matchesNothing() {
        assertTrue(UpnpSearch.parse("upnp:class derivedfrom \"object.item.audioItem\"").test(LOVE));
        assertTrue(UpnpSearch.parse("upnp:class = \"object.item.audioItem.musicTrack\"").test(LOVE));
        // Container results (albums, artists) are not offered by Search
        assertFalse(UpnpSearch.parse("upnp:class derivedfrom \"object.container.album.musicAlbum\"").test(LOVE));
    }

    @Test
    public void artistAlbumGenre_andCreatorAsArtist() {
        assertTrue(UpnpSearch.parse("upnp:artist contains \"miles\"").test(JAZZ));
        assertTrue(UpnpSearch.parse("dc:creator = \"Miles Davis\"").test(JAZZ));
        assertTrue(UpnpSearch.parse("upnp:album contains \"blue\"").test(JAZZ));
        assertTrue(UpnpSearch.parse("upnp:genre = \"jazz\"").test(JAZZ));
        assertFalse(UpnpSearch.parse("upnp:genre = \"jazz\"").test(LOVE));
    }

    @Test
    public void andBindsTighterThanOr_andParenthesesGroup() {
        // a or (b and c)
        Predicate<Track> p = UpnpSearch.parse(
                "dc:title contains \"love\" or upnp:artist contains \"miles\" and upnp:genre = \"Rock\"");
        assertTrue(p.test(LOVE));
        assertFalse(p.test(JAZZ));
        Predicate<Track> grouped = UpnpSearch.parse(
                "(dc:title contains \"love\" or upnp:artist contains \"miles\") and upnp:genre = \"Jazz\"");
        assertTrue(grouped.test(JAZZ));
        assertFalse(grouped.test(LOVE));
    }

    @Test
    public void negativeOperators_andExists() {
        assertTrue(UpnpSearch.parse("dc:title doesNotContain \"love\"").test(JAZZ));
        assertTrue(UpnpSearch.parse("upnp:genre != \"Rock\"").test(JAZZ));
        assertTrue(UpnpSearch.parse("upnp:album exists true").test(JAZZ));
        assertFalse(UpnpSearch.parse("upnp:album exists false").test(JAZZ));
    }

    @Test
    public void escapedQuotes_inValues() {
        Track quoted = track("Say \"Hello\"", "A", "B", "C");
        assertTrue(UpnpSearch.parse("dc:title contains \"\\\"Hello\\\"\"").test(quoted));
    }

    @Test
    public void unknownProperty_matchesNothing() {
        assertFalse(UpnpSearch.parse("upnp:director contains \"x\"").test(LOVE));
    }

    @Test
    public void malformedCriteria_isRejected() {
        assertThrows(IllegalArgumentException.class, () -> UpnpSearch.parse("dc:title contains"));
        assertThrows(IllegalArgumentException.class, () -> UpnpSearch.parse("(dc:title = \"a\""));
        assertThrows(IllegalArgumentException.class, () -> UpnpSearch.parse("dc:title = \"a\" xor dc:title = \"b\""));
        assertThrows(IllegalArgumentException.class, () -> UpnpSearch.parse("dc:title contains \"open"));
    }
}
