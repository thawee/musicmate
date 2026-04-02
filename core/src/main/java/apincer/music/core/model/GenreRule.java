package apincer.music.core.model;

import static apincer.music.core.model.PlaylistEntry.normalize;

public class GenreRule{
    String genre;
    String mood;
    String style;

    boolean anyGenre;
    boolean anyMood;
    boolean anyStyle;

    boolean isSimpleGenre() {
        return !anyGenre && anyMood && anyStyle;
    }

    public boolean matches(Track t) {
        /*if("Party".equals(t.getMood())) {
            System.out.println(t);
        }*/
        return (anyGenre || genre.equals(normalize(t.getGenre())))
                && (anyMood || mood.equals(normalize(t.getMood())))
                && (anyStyle || style.equals(normalize(t.getStyle())));
    }
}