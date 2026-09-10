package apincer.music.core.model;



import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import apincer.music.core.utils.TagUtils;
import kotlinx.serialization.Serializable;

@Serializable
public class PlaylistEntry {
    public static final String TYPE_TITLE = "title";
    public static final String TYPE_GENRE = "genre";
    public static final String TYPE_SMART = "smart";
    private String name;
    private String uuid; // Added from your JSON sample
    private String type = TYPE_TITLE; // "song" or "album"
    private String note;
    private String version;
    private String description;

    // Smart Playlist dynamic criteria
    private double minDrScore = 0.0;
    private boolean hiresOnly = false;
    private boolean dsdOnly = false;
    private boolean losslessOnly = false;
    private int minBitDepth = 0;
    private long minSampleRate = 0;

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

    public double getMinDrScore() { return minDrScore; }
    public void setMinDrScore(double minDrScore) { this.minDrScore = minDrScore; }

    public boolean isHiresOnly() { return hiresOnly; }
    public void setHiresOnly(boolean hiresOnly) { this.hiresOnly = hiresOnly; }

    public boolean isDsdOnly() { return dsdOnly; }
    public void setDsdOnly(boolean dsdOnly) { this.dsdOnly = dsdOnly; }

    public boolean isLosslessOnly() { return losslessOnly; }
    public void setLosslessOnly(boolean losslessOnly) { this.losslessOnly = losslessOnly; }

    public int getMinBitDepth() { return minBitDepth; }
    public void setMinBitDepth(int minBitDepth) { this.minBitDepth = minBitDepth; }

    public long getMinSampleRate() { return minSampleRate; }
    public void setMinSampleRate(long minSampleRate) { this.minSampleRate = minSampleRate; }

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
        if (TYPE_SMART.equals(type)) {
            return;
        }
        if(rules == null) return;

        for (PlaylistRule r : rules) {
            if(TYPE_GENRE.equals(type)) {

                  //  c.genre = normalize(r.getGenre()); //StringUtils.normalizeName(r.getGenre());
                  //  c.mood = normalize(r.getMood()); //StringUtils.normalizeName(r.getMood());
                  //  c.style = normalize(r.getStyle()); //StringUtils.normalizeName(r.getStyle());
                r.setGenre(normalizeList(r.getGenre()));
                r.setMood(normalizeList(r.getMood()));
                r.setStyle(normalizeList(r.getStyle()));
                if (r.getExclude() != null) {
                    r.getExclude().setMood(normalizeList(r.getExclude().getMood()));
                    r.getExclude().setStyle(normalizeList(r.getExclude().getStyle()));
                }

                boolean anyGenre = isAny(r.getGenre());
                boolean anyMood  = isAny(r.getMood());
                boolean anyStyle = isAny(r.getStyle());
                GenreRule c = new GenreRule(r, anyGenre, anyMood, anyStyle);

                    if (c.isSimpleGenre()) {
                        // FAST PATH
                        genreIndexRules.addAll(r.getGenre());
                    } else {
                        genreComplexRules.add(c);       // SLOW PATH
                    }
            }else {
                long key = songKey(r.getTitle(), r.getArtist());
                titleIndexRules.add(key);
            }
        }
    }

    private List<String> normalizeList(List<String> values) {
        if (values == null) return Collections.emptyList();

        return values.stream()
                .filter(Objects::nonNull)
                .map(this::normalize)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toList());
    }

    public boolean isInTitlePlaylist(Track track) {
        if(TYPE_TITLE.equals(type)) {
            Long key = songKey(track.getTitle(), track.getArtist());
            return titleIndexRules.contains(key);
        }
        return false;
    }

    public boolean isInPlaylist(Track track) {
        if (track == null) return false;

        if (TYPE_SMART.equals(type)) {
            return matchesSmartCriteria(track);
        }

        if(TYPE_TITLE.equals(type)) {
            Long key = songKey(track.getTitle(), track.getArtist());
            return titleIndexRules.contains(key);
        }

        if(genreIndexRules.contains(normalize(track.getGenre()))) {
            return true;
        }

        if (genreComplexRules.isEmpty()) return false;
        String genre = normalize(track.getGenre());
        String mood = normalize(track.getMood());
        String style = normalize(track.getStyle());
        for (GenreRule rule : genreComplexRules) {
            if (rule.matches(genre, mood, style)) {
                return true;
            }
        }
        return false;
    }

    public boolean matchesSmartCriteria(Track track) {
        if (track == null) return false;

        // 1. Min DR Score filter
        if (minDrScore > 0.0) {
            double dr = track.getDrScore() > 0 ? track.getDrScore() : track.getDynamicRange();
            if (dr < minDrScore) {
                return false;
            }
        }

        // 2. DSD Only filter
        if (dsdOnly) {
            if (!TagUtils.isDSD(track)) {
                return false;
            }
        }

        // 3. Hi-Res Only filter (Hi-Res 24-bit+ PCM or DSD)
        if (hiresOnly) {
            boolean isHiRes = TagUtils.isHiRes(track) || TagUtils.isHiRes48(track) || track.getAudioBitsDepth() >= 24;
            if (!isHiRes && !TagUtils.isDSD(track)) {
                return false;
            }
        }

        // 4. Lossless Only filter
        if (losslessOnly) {
            if (TagUtils.isLossy(track)) {
                return false;
            }
            boolean isLossless = TagUtils.isLosslessFormat(track) || TagUtils.isLossless(track) || TagUtils.isHiRes(track) || TagUtils.isDSD(track);
            if (!isLossless) {
                return false;
            }
        }

        // 5. Min Bit Depth filter
        if (minBitDepth > 0) {
            if (track.getAudioBitsDepth() < minBitDepth) {
                return false;
            }
        }

        // 6. Min Sample Rate filter
        if (minSampleRate > 0) {
            if (track.getAudioSampleRate() < minSampleRate) {
                return false;
            }
        }

        return true;
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

    public String normalize(String s) {
        if (s == null) return "";
        return s.trim().toLowerCase();
    }

    private boolean isAny(List<String> values) {
        return values == null || values.isEmpty() || values.contains("*");
    }
}