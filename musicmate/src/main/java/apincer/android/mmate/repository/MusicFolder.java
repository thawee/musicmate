package apincer.android.mmate.repository;

public class MusicFolder extends MusicTag {
    public MusicFolder(String name) {
       this("FLD", name);
    }

    public MusicFolder(String type, String name) {
        this.name = name;
        this.title = name;
        this.uniqueKey = name;
        this.fileType = type;
        this.audioEncoding = type;
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

    public void addChildCount(){
        childCount++;
    }

    private String name;

    public String getUniqueKey() {
        return uniqueKey;
    }

    public void setUniqueKey(String uniqueKey) {
        this.uniqueKey = uniqueKey;
    }

    private String uniqueKey;
    private long childCount = 0;
}
