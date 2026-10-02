package apincer.music.server.jupnp.content;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Predicate;

import apincer.music.core.model.Track;

/**
 * Parses a UPnP ContentDirectory SearchCriteria string (UPnP-av-ContentDirectory v1, 2.5.5) into a
 * track filter. Supports "*", and/or with parentheses ("and" binds tighter), and the operators
 * =, !=, contains, doesNotContain, derivedfrom and exists, compared case-insensitively.
 * Properties: dc:title, upnp:artist / dc:creator, upnp:album, upnp:genre, upnp:class.
 * Search returns tracks only, so a container class matches nothing; an unknown property also
 * matches nothing. Malformed criteria throw IllegalArgumentException (UPnP error 708).
 */
final class UpnpSearch {
    private static final String TRACK_CLASS = "object.item.audioItem.musicTrack";

    private final List<String> tokens;
    private int pos;

    private UpnpSearch(List<String> tokens) {
        this.tokens = tokens;
    }

    static Predicate<Track> parse(String criteria) {
        String trimmed = criteria == null ? "" : criteria.trim();
        if (trimmed.isEmpty() || trimmed.equals("*")) return track -> true;
        UpnpSearch parser = new UpnpSearch(tokenize(trimmed));
        Predicate<Track> result = parser.orExpression();
        if (parser.pos != parser.tokens.size()) {
            throw new IllegalArgumentException("Unexpected '" + parser.tokens.get(parser.pos) + "'");
        }
        return result;
    }

    private Predicate<Track> orExpression() {
        Predicate<Track> left = andExpression();
        while (peekWord("or")) {
            pos++;
            left = left.or(andExpression());
        }
        return left;
    }

    private Predicate<Track> andExpression() {
        Predicate<Track> left = primary();
        while (peekWord("and")) {
            pos++;
            left = left.and(primary());
        }
        return left;
    }

    private Predicate<Track> primary() {
        if ("(".equals(peek())) {
            pos++;
            Predicate<Track> inner = orExpression();
            expect(")");
            return inner;
        }
        String property = next();
        String op = next();
        String value = next();
        if (op.equalsIgnoreCase("exists")) {
            boolean wanted = Boolean.parseBoolean(value);
            return track -> !isEmpty(field(track, property)) == wanted;
        }
        if (!value.startsWith("\"")) {
            throw new IllegalArgumentException("Expected a quoted value after " + property + " " + op);
        }
        String wanted = value.substring(1).toLowerCase(Locale.ROOT);
        return track -> compare(field(track, property), op, wanted);
    }

    private static boolean compare(String actual, String op, String wanted) {
        if (actual == null) return false;
        String a = actual.toLowerCase(Locale.ROOT);
        switch (op.toLowerCase(Locale.ROOT)) {
            case "=": return a.equals(wanted);
            case "!=": return !a.equals(wanted);
            case "contains": return a.contains(wanted);
            case "doesnotcontain": return !a.contains(wanted);
            // derivedfrom: the item's class equals the given class or extends it
            case "derivedfrom": return a.equals(wanted) || a.startsWith(wanted + ".");
            case "<": return a.compareTo(wanted) < 0;
            case "<=": return a.compareTo(wanted) <= 0;
            case ">": return a.compareTo(wanted) > 0;
            case ">=": return a.compareTo(wanted) >= 0;
            default: throw new IllegalArgumentException("Unsupported operator " + op);
        }
    }

    /** The track's value for a search property; null for properties Search does not support. */
    private static String field(Track track, String property) {
        switch (property) {
            case "dc:title": return track.getTitle();
            case "upnp:artist":
            case "dc:creator": return track.getArtist();
            case "upnp:album": return track.getAlbum();
            case "upnp:genre": return track.getGenre();
            case "upnp:class": return TRACK_CLASS;
            default: return null;
        }
    }

    private static boolean isEmpty(String s) {
        return s == null || s.trim().isEmpty();
    }

    // --- tokens ---

    private String peek() {
        return pos < tokens.size() ? tokens.get(pos) : null;
    }

    private boolean peekWord(String word) {
        String t = peek();
        return t != null && t.equalsIgnoreCase(word);
    }

    private String next() {
        if (pos >= tokens.size()) throw new IllegalArgumentException("Search criteria ended early");
        return tokens.get(pos++);
    }

    private void expect(String token) {
        if (!token.equals(peek())) throw new IllegalArgumentException("Expected '" + token + "'");
        pos++;
    }

    /** Words, parentheses, and quoted strings (kept with a leading quote; \" and \\ unescaped). */
    private static List<String> tokenize(String s) {
        List<String> out = new ArrayList<>();
        int i = 0;
        while (i < s.length()) {
            char c = s.charAt(i);
            if (Character.isWhitespace(c)) {
                i++;
            } else if (c == '(' || c == ')') {
                out.add(String.valueOf(c));
                i++;
            } else if (c == '"') {
                StringBuilder sb = new StringBuilder("\"");
                i++;
                boolean closed = false;
                while (i < s.length()) {
                    char d = s.charAt(i++);
                    if (d == '\\' && i < s.length()) {
                        sb.append(s.charAt(i++));
                    } else if (d == '"') {
                        closed = true;
                        break;
                    } else {
                        sb.append(d);
                    }
                }
                if (!closed) throw new IllegalArgumentException("Unterminated quoted value");
                out.add(sb.toString());
            } else {
                int start = i;
                while (i < s.length() && !Character.isWhitespace(s.charAt(i))
                        && s.charAt(i) != '(' && s.charAt(i) != ')' && s.charAt(i) != '"') {
                    i++;
                }
                out.add(s.substring(start, i));
            }
        }
        return out;
    }
}
