package apincer.music.core.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public class PlaylistRule {
    private String title;
    private String artist;
    private String album;
    private String genre;
    private String mood;
    private String style;

    /*
    public boolean matchesGenre(TrackMeta track) {
        return match(genre, track.getGenre())
                && match(mood, track.getMood())
                && match(style, track.getStyle());
    }

    public boolean matchesTitle(TrackMeta track) {
        return match(title, track.getTitle())
                && match(artist, track.getArtist())
                && match(album, track.getAlbum());
    }

    private boolean match(String rule, String value) {
        return rule == null || rule.isBlank() || "*".equals(rule)
                || rule.equalsIgnoreCase(value);
    } */

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getArtist() {
        return artist;
    }

    public void setArtist(String artist) {
        this.artist = artist;
    }

    public String getAlbum() {
        return album;
    }

    public void setAlbum(String album) {
        this.album = album;
    }

    public String getGenre() {
        return genre;
    }

    public void setGenre(String genre) {
        this.genre = genre;
    }

    public String getMood() {
        return mood;
    }

    public void setMood(String mood) {
        this.mood = mood;
    }

    public String getStyle() {
        return style;
    }

    public void setStyle(String style) {
        this.style = style;
    }
}
