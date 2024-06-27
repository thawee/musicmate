package apincer.android.mmate.repository;

public class MusicFolder {
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

    private String name;
    private long childCount;
}
