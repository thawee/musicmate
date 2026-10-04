package apincer.music.core.model;

import java.util.Collection;
import java.util.Collections;
import java.util.List;

public class GenreRule{
    public GenreRule(PlaylistRule rule, boolean anyGenre, boolean anyMood, boolean anyStyle) {
        this.rule = rule;
        this.anyGenre = anyGenre;
        this.anyMood = anyMood;
        this.anyStyle = anyStyle;
    }

    PlaylistRule rule;

    boolean anyGenre;
    boolean anyMood;
    boolean anyStyle;

    boolean isSimpleGenre() {
       // return !anyGenre && anyMood && anyStyle;
        return !anyGenre
                && anyMood
                && anyStyle
                && (rule.getExclude() == null || rule.getExclude().isEmpty());
    }

    /*
    @Deprecated
    public boolean matches(Track t) {
        return (anyGenre || genre.equals(normalize(t.getGenre())))
                && (anyMood || mood.equals(normalize(t.getMood())))
                && (anyStyle || style.equals(normalize(t.getStyle())));
    } */

    /** Each argument holds the track's matching keys for that field (a track may have several genres). */
    public boolean matches(Collection<String> genres, Collection<String> moods, Collection<String> styles) {

        // include ("*" or an empty rule list matches anything)
        if (!anyGenre && !matchAny(rule.getGenre(), genres)) return false;
        if (!anyMood && !matchAny(rule.getMood(), moods)) return false;
        if (!anyStyle && !matchAny(rule.getStyle(), styles)) return false;

        // exclude
        if (rule.getExclude() != null && !rule.getExclude().isEmpty()) {
            if (isExcluded(rule.getExclude().getMood(), moods)) return false;
            return !isExcluded(rule.getExclude().getStyle(), styles);
        }

        return true;
    }

    private boolean matchInclude(List<String> ruleValues, String trackValue) {
        if (ruleValues == null || ruleValues.isEmpty()) return true;
        return ruleValues.contains(trackValue);
    }

    private boolean matchAny(List<String> ruleValues, Collection<String> trackValues) {
        if (ruleValues == null || ruleValues.isEmpty()) return true;

        for (String val : trackValues) {
            if (ruleValues.contains(val)) return true;
        }
        return false;
    }

    private boolean isExcluded(List<String> excludeValues, Collection<String> trackValues) {
        if (excludeValues == null || excludeValues.isEmpty()) return false;

        for (String val : trackValues) {
            if (excludeValues.contains(val)) return true;
        }
        return false;
    }
}