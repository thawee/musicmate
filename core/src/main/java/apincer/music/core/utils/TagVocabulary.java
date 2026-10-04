package apincer.music.core.utils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * One vocabulary for genre, mood, style and origin.
 *
 * <p>Older presets contained "/" ("R&B / Soul", "Chill / Relax"). "/" also separates multiple
 * values, so a saved genre came back as two ("R&B, Soul") and stopped matching playlist rules.
 * Presets now use single names; this class maps the old forms to them when tags are read and
 * when playlist rules are matched.</p>
 */
public final class TagVocabulary {

    public enum Field { GENRE, MOOD, STYLE, ORIGIN }

    private TagVocabulary() {}

    private static final Map<Field, Map<String, String>> ALIASES = new HashMap<>();
    /** Legacy genre presets that were split into two values on read: first, second, new name. */
    private static final String[][] LEGACY_GENRE_PAIRS = {
            {"r&b", "soul", "R&B"},
            {"hip-hop", "rap", "Hip-Hop"},
            {"electronic", "edm", "Electronic"},
            {"acoustic", "vocal", "Acoustic"},
            {"soundtrack", "ost", "Soundtrack"},
            {"alternative", "indie", "Alternative"},
    };

    static {
        Map<String, String> genre = new HashMap<>();
        for (String[] pair : LEGACY_GENRE_PAIRS) {
            genre.put(pair[0] + " / " + pair[1], pair[2]);
            genre.put(pair[0] + "/" + pair[1], pair[2]);
        }
        ALIASES.put(Field.GENRE, genre);

        Map<String, String> mood = new HashMap<>();
        mood.put("chill / relax", "Chill");
        mood.put("sad / melancholy", "Melancholy");
        mood.put("moody / dark", "Dark");
        mood.put("focus / study", "Focus");
        ALIASES.put(Field.MOOD, mood);

        Map<String, String> style = new HashMap<>();
        style.put("live / concert", "Live");
        style.put("vocal / audiophile", "Audiophile Vocal");
        ALIASES.put(Field.STYLE, style);

        Map<String, String> origin = new HashMap<>();
        origin.put("us/uk", "Western");
        origin.put("us / uk", "Western");
        origin.put("european", "Western");
        origin.put("asia", "Other Asian");
        ALIASES.put(Field.ORIGIN, origin);
    }

    private static String key(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");
    }

    /** The current name for one value; unknown values are returned trimmed. */
    public static String canonical(Field field, String value) {
        if (value == null) return "";
        String trimmed = value.trim();
        String mapped = ALIASES.get(field).get(key(trimmed));
        return mapped != null ? mapped : trimmed;
    }

    /**
     * Normalises a stored field value: legacy names become current presets. Genre may hold
     * several values; the result is joined with ", " like the tag reader does.
     */
    public static String normalize(Field field, String value) {
        if (StringUtils.isEmpty(value)) return value;
        if (field != Field.GENRE) return canonical(field, value);
        List<String> values = genreValues(value);
        return values.isEmpty() ? value.trim() : String.join(", ", values);
    }

    /** Lower-case keys used for matching playlist rules, one per value. */
    public static Set<String> matchKeys(Field field, String value) {
        if (StringUtils.isEmpty(value)) return Collections.emptySet();
        Set<String> keys = new LinkedHashSet<>();
        if (field == Field.GENRE) {
            for (String v : genreValues(value)) keys.add(key(v));
        } else {
            keys.add(key(canonical(field, value)));
        }
        return keys;
    }

    /** Lower-case key for one playlist-rule value. */
    public static String ruleKey(Field field, String ruleValue) {
        return key(canonical(field, ruleValue));
    }

    private static List<String> genreValues(String value) {
        String whole = canonical(Field.GENRE, value);
        if (!whole.equals(value.trim())) {
            List<String> single = new ArrayList<>();
            single.add(whole);
            return single;
        }
        List<String> parts = StringUtils.splitMultiValue(value);
        List<String> result = new ArrayList<>();
        for (int i = 0; i < parts.size(); i++) {
            String part = canonical(Field.GENRE, parts.get(i));
            if (i + 1 < parts.size()) {
                String merged = mergeLegacyPair(part, parts.get(i + 1));
                if (merged != null) {
                    part = merged;
                    i++;
                }
            }
            if (!part.isEmpty() && !containsIgnoreCase(result, part)) result.add(part);
        }
        return result;
    }

    private static String mergeLegacyPair(String first, String second) {
        String a = key(first);
        String b = key(second);
        for (String[] pair : LEGACY_GENRE_PAIRS) {
            if (pair[0].equals(a) && pair[1].equals(b)) return pair[2];
        }
        return null;
    }

    private static boolean containsIgnoreCase(List<String> list, String value) {
        for (String s : list) if (s.equalsIgnoreCase(value)) return true;
        return false;
    }
}
