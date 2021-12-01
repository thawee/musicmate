package apincer.android.mmate.item;

import java.io.Serializable;

public class MediaFilter implements Serializable {
    public MediaFilter(TYPE type, String keyword) {
        this.type = type;
        this.keyword = keyword;
        this.displayKeyword = keyword;
    }

    public String getKeyword() {
        return keyword;
    }

    public void setKeyword(String keyword) {
        this.keyword = keyword;
    }

    public void setDisplayKeyword(String keyword) {
        this.displayKeyword = keyword;
    }

    public String getDisplayKeyword() {
        return displayKeyword;
    }

    public TYPE getType() {
        return type;
    }

    public void setType(TYPE type) {
        this.type = type;
    }

    public enum TYPE {ARTIST, ALBUM,ALBUM_ARTIST,GENRE,GROUPING,PATH}
    private String keyword;
    private String displayKeyword;
    private TYPE type;
}
