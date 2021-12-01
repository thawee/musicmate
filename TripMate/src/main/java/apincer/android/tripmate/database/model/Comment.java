package apincer.android.tripmate.database.model;

import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.PrimaryKey;

import java.util.Date;

@Entity(tableName = "place_comment", foreignKeys = @ForeignKey(entity = Place.class,
        parentColumns = "id",
        childColumns = "place_id",
        onDelete = ForeignKey.NO_ACTION))
public class Comment {
    @PrimaryKey(autoGenerate = true)
    private int id;

    @ColumnInfo(name = "place_id")
    private int placeId;

    public int getPlaceId() {
        return placeId;
    }

    public void setPlaceId(int placeId) {
        this.placeId = placeId;
    }

    public String getAuthor() {
        return author;
    }

    public void setAuthor(String author) {
        this.author = author;
    }

    public String getText() {
        return text;
    }

    public void setText(String text) {
        this.text = text;
    }

    private String author;
    private String text;

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }
    // private Date date;
}
