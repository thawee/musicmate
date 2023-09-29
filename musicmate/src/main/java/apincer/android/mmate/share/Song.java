package apincer.android.mmate.share;
public class Song {
    String path;
    String name;
    long ctime;
    long size;
    @Override
    public String toString() {
        return "Song [path=" + path + ", name=" + name + ", ctime=" + ctime + ", size=" + size + "]";
    }
}
