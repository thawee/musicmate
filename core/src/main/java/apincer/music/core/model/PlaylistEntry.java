package apincer.music.core.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@JsonIgnoreProperties(ignoreUnknown = true)
public class PlaylistEntry {
    public static final String TYPE_TITLE = "title";
    public static final String TYPE_GENRE = "genre";
    private String name;
    private String uuid; // Added from your JSON sample
    private String type = TYPE_TITLE; // "song" or "album"
    private String note;
    private String version;
    private String description;

   private List<PlaylistRule> rules;

    private final List<GenreRule> genreComplexRules = new ArrayList<>();
    private final Set<String> genreIndexRules = new HashSet<>();
    private final Set<Long> titleIndexRules = new HashSet<>();

    // Getters and Setters
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getUuid() { return uuid; }
    public void setUuid(String uuid) { this.uuid = uuid; }
    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
    public List<PlaylistRule> getRules() { return rules; }
    public void setRules(List<PlaylistRule> songs) { this.rules = songs; }

    public String getNote() {
        return note;
    }

    public void setNote(String note) {
        this.note = note;
    }

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public void compileRules() {
        if(rules == null) return;

        for (PlaylistRule r : rules) {
            if(TYPE_GENRE.equals(type)) {
                GenreRule c = new GenreRule();

                    c.genre = normalize(r.getGenre()); //StringUtils.normalizeName(r.getGenre());
                    c.mood = normalize(r.getMood()); //StringUtils.normalizeName(r.getMood());
                    c.style = normalize(r.getStyle()); //StringUtils.normalizeName(r.getStyle());

                    c.anyGenre = "*".equals(r.getGenre()) || c.genre.isEmpty();
                    c.anyMood = "*".equals(r.getMood()) || c.mood.isEmpty();
                    c.anyStyle = "*".equals(r.getStyle()) || c.style.isEmpty();

                    if (c.isSimpleGenre()) {
                        genreIndexRules.add(c.genre);   // FAST PATH
                    } else {
                        genreComplexRules.add(c);       // SLOW PATH
                    }
            }else {
                long key = songKey(r.getTitle(), r.getArtist());
                titleIndexRules.add(key);
            }
        }
    }

    public boolean isInTitlePlaylist(Track track) {
        if(TYPE_TITLE.equals(type)) {
            Long key = songKey(track.getTitle(), track.getArtist());
            return titleIndexRules.contains(key);
        }
        return false;
    }

    public boolean isInPlaylist(Track track) {
        if(TYPE_TITLE.equals(type)) {
            Long key = songKey(track.getTitle(), track.getArtist());
            return titleIndexRules.contains(key);
        }

        if(genreIndexRules.contains(normalize(track.getGenre()))) {
            return true;
        }

        if (genreComplexRules.isEmpty()) return false;
        for (GenreRule rule : genreComplexRules) {
            if (rule.matches(track)) {
                return true;
            }
        }
        return false;
    }

    public static long songKey(String title, String artist) {
        // ignore album, Album often differs (remaster, deluxe, typo)
        // This improves match accuracy
        if (title == null) title = "";
        if (artist == null) artist = "";

        int t = title.trim().toLowerCase().hashCode();
        int a = artist.trim().toLowerCase().hashCode();

        return ((long) t << 32) | (a & 0xffffffffL);
    }

    public static String normalize(String s) {
        if (s == null) return "";
        return s.trim().toLowerCase();
    }
}