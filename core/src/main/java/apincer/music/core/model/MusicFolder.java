package apincer.music.core.model;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import apincer.music.core.database.MusicTag;

public class MusicFolder extends MusicTag {

    public MusicFolder(SearchCriteria.TYPE type, String name) {
        this.name = name;
        this.title = name;
       // this.uniqueKey = name;
        this.fileType = "FLD";
        this.audioEncoding = "FLD";
        this.mmManaged = true;
        this.type = type;

        // --- 1. Build the correct, full path based on type ---
        this.path = type.name().toLowerCase()+"/"+name;

        // --- 2. Use the full path as the unique key ---
        // This is now unique (e.g., "Music/Rock" vs "Covers/genres/Rock")
        this.uniqueKey = this.path;

        // --- Generate Stable ID ---
        // 1. Create a stable, name-based 128-bit UUID
        UUID uuid = UUID.nameUUIDFromBytes(name.getBytes(StandardCharsets.UTF_8));

        // 2. Fold the 128-bit UUID into a 64-bit long ID by XORing its two halves.
        // This is much more stable and unique than name.hashCode()
        this.id = uuid.getMostSignificantBits() ^ uuid.getLeastSignificantBits();
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public long getChildCount() {
        return childCount;
    }

    public void setChildCount(long childCount) {
        this.childCount = childCount;
    }

    public void increaseChildCount(){
        childCount++;
    }

    private String name;

    public String getUniqueKey() {
        return uniqueKey;
    }

    public void setUniqueKey(String uniqueKey) {
        this.uniqueKey = uniqueKey;
    }

    private final SearchCriteria.TYPE type;
    private String uniqueKey;
    private long childCount = 0;

    public SearchCriteria.TYPE getType() {
        return type;
    }
}
