package apincer.music.core.model;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import apincer.music.core.database.MusicTag;

public class PlaylistEntry {
    private String name;
    private String uuid; // Added from your JSON sample
    private String type = "song"; // "song" or "album"
    private String note;
    private String version;
    private String description;

    private String reference_tag;
    private List<MusicTag> songs;   // Used if type is "song"
    private Map<String, MusicTag> mappedSongs = new HashMap<>(); // Used if type is "album"

    // Getters and Setters
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getUuid() { return uuid; }
    public void setUuid(String uuid) { this.uuid = uuid; }
    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
    public List<MusicTag> getSongs() { return songs; }
    public void setSongs(List<MusicTag> songs) { this.songs = songs; }

    public Map<String, MusicTag> getMappedSongs() {
        return mappedSongs;
    }

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
}