package apincer.music.core.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public class PlaylistRule {
    private String title;
    private String artist;
    private String album;
    private List<String> genre;
    private List<String> mood;
    private List<String> style;

    private ExcludeRule exclude;

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

    public List<String> getGenre() {
        return genre;
    }

    public void setGenre(List<String> genre) {
        this.genre = genre;
    }

    public List<String> getMood() {
        return mood;
    }

    public void setMood(List<String> mood) {
        this.mood = mood;
    }

    public List<String> getStyle() {
        return style;
    }

    public void setStyle(List<String> style) {
        this.style = style;
    }

    public ExcludeRule getExclude() {
        return exclude;
    }

    public void setExclude(ExcludeRule exclude) {
        this.exclude = exclude;
    }
}
