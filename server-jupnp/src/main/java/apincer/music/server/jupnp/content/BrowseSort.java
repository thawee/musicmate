package apincer.music.server.jupnp.content;

import org.jupnp.support.model.DIDLObject;
import org.jupnp.support.model.PersonWithRole;
import org.jupnp.support.model.SortCriterion;

import java.util.Comparator;
import java.util.List;
import java.util.function.Function;

import apincer.music.core.model.Track;

/**
 * UPnP SortCriteria for Browse and Search: dc:title, upnp:artist, upnp:album and dc:date,
 * each + (ascending) or - (descending), compared case-insensitively with empty values last.
 * Other properties are ignored; with none supported there is no sort (null).
 */
final class BrowseSort {
    static final List<String> PROPERTIES = List.of("dc:title", "upnp:artist", "upnp:album", "dc:date");

    private BrowseSort() {
    }

    static Comparator<DIDLObject> didl(SortCriterion[] criteria) {
        return build(criteria, BrowseSort::didlValue);
    }

    static Comparator<Track> tracks(SortCriterion[] criteria) {
        return build(criteria, BrowseSort::trackValue);
    }

    private interface Getter<T> {
        String get(T object, String property);
    }

    private static <T> Comparator<T> build(SortCriterion[] criteria, Getter<T> getter) {
        if (criteria == null) return null;
        Comparator<T> result = null;
        for (SortCriterion criterion : criteria) {
            String property = criterion.getPropertyName();
            if (!PROPERTIES.contains(property)) continue;
            Function<T, String> key = object -> getter.get(object, property);
            Comparator<String> order = criterion.isAscending()
                    ? String.CASE_INSENSITIVE_ORDER : String.CASE_INSENSITIVE_ORDER.reversed();
            Comparator<T> next = Comparator.comparing(key, Comparator.nullsLast(order));
            result = result == null ? next : result.thenComparing(next);
        }
        return result;
    }

    private static String didlValue(DIDLObject object, String property) {
        switch (property) {
            case "dc:title": return blankToNull(object.getTitle());
            case "upnp:artist": {
                PersonWithRole artist = object.getFirstPropertyValue(DIDLObject.Property.UPNP.ARTIST.class);
                return blankToNull(artist != null ? artist.getName() : object.getCreator());
            }
            case "upnp:album": return blankToNull(object.getFirstPropertyValue(DIDLObject.Property.UPNP.ALBUM.class));
            case "dc:date": return blankToNull(object.getFirstPropertyValue(DIDLObject.Property.DC.DATE.class));
            default: return null;
        }
    }

    private static String trackValue(Track track, String property) {
        switch (property) {
            case "dc:title": return blankToNull(track.getTitle());
            case "upnp:artist": return blankToNull(track.getArtist());
            case "upnp:album": return blankToNull(track.getAlbum());
            case "dc:date": return AbstractContentBrowser.didlDate(track.getYear());
            default: return null;
        }
    }

    private static String blankToNull(String s) {
        return s == null || s.trim().isEmpty() ? null : s;
    }
}
