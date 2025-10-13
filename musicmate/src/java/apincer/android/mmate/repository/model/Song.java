package apincer.android.mmate.repository.model;

import apincer.android.mmate.repository.database.MusicTag;

// Represents a song entry within a "song" type playlist in JSON
public class Song {
    private String title;
    private String artist;
    private String genre;
    private String grouping;
    private String album;

    public String getRating() {
        return rating;
    }

    public void setRating(String rating) {
        this.rating = rating;
    }

    private String rating;
    private String notes;
    // Add year if you include it for songs in JSON and want to map it
    // private String year;


    // Getters and Setters
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getArtist() { return artist; }
    public void setArtist(String artist) { this.artist = artist; }
    public String getAlbum() { return album; }
    public void setAlbum(String album) { this.album = album; }
    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }

    public String getGenre() {
        return genre;
    }

    public void setGenre(String genre) {
        this.genre = genre;
    }

    public String getGrouping() {
        return grouping;
    }

    public void setGrouping(String grouping) {
        this.grouping = grouping;
    }

    public MusicTag toMusicTag() {
        MusicTag tag = new MusicTag();
        tag.setTitle(this.title);
        tag.setArtist(this.artist);
        tag.setAlbum(this.album);
        tag.setFileType("-");
        tag.setAudioEncoding("-");
        tag.setAudioBitRate(1);
        tag.setAudioBitsDepth(0);
        tag.setAudioSampleRate(1);
        tag.setPath(this.title);
        return tag;
    }
}