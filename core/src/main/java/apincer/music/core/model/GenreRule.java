package apincer.music.core.model;

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

    public boolean matches(String genre, String mood, String style) {

        // include
        if (!matchAny(rule.getGenre(), Collections.singletonList(genre))) return false;
        if (!matchAny(rule.getMood(), Collections.singletonList(mood))) return false;
        if (!matchAny(rule.getStyle(), Collections.singletonList(style))) return false;

        // exclude
        if (rule.getExclude() != null && !rule.getExclude().isEmpty()) {
           // if (isExcluded(rule.getExclude().getGenre(), track.getGenres())) return false;
            if (isExcluded(rule.getExclude().getMood(), Collections.singletonList(mood))) return false;
            return !isExcluded(rule.getExclude().getStyle(), Collections.singletonList(style));
        }

        return true;
    }

    private boolean matchInclude(List<String> ruleValues, String trackValue) {
        if (ruleValues == null || ruleValues.isEmpty()) return true;
        return ruleValues.contains(trackValue);
    }

    private boolean matchAny(List<String> ruleValues, List<String> trackValues) {
        if (ruleValues == null || ruleValues.isEmpty()) return true;

        for (String val : trackValues) {
            if (ruleValues.contains(val)) return true;
        }
        return false;
    }

    private boolean isExcluded(List<String> excludeValues, List<String> trackValues) {
        if (excludeValues == null || excludeValues.isEmpty()) return false;

        for (String val : trackValues) {
            if (excludeValues.contains(val)) return true;
        }
        return false;
    }
}