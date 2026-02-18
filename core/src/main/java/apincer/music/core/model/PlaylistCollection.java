package apincer.music.core.model;

import java.util.List;
import java.util.Map;

public class PlaylistCollection {
    private Map<String, String> metadata;
    private List<PlaylistEntry> playlists;
    // Optional: metadata if you have it at the root of your JSON
    // private Map<String, String> metadata;


    public List<PlaylistEntry> getPlaylists() { return playlists; }
    public void setPlaylists(List<PlaylistEntry> playlists) { this.playlists = playlists; }

     public Map<String, String> getMetadata() { return metadata; }
     public void setMetadata(Map<String, String> metadata) { this.metadata = metadata; }
}
