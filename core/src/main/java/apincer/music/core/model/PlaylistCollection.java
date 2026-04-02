package apincer.music.core.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;
import java.util.Map;

@JsonIgnoreProperties(ignoreUnknown = true)
public class PlaylistCollection {
    private Map<String, String> metadata;
    private List<PlaylistEntry> playlists;

    public List<PlaylistEntry> getPlaylists() { return playlists; }
    public void setPlaylists(List<PlaylistEntry> playlists) { this.playlists = playlists; }

     public Map<String, String> getMetadata() { return metadata; }
     public void setMetadata(Map<String, String> metadata) { this.metadata = metadata; }

    public void compileRules() {
        if(playlists == null) return;
        for(PlaylistEntry entry: playlists) {
            entry.compileRules();
        }
    }
}
