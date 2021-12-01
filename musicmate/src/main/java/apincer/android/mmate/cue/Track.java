package apincer.android.mmate.cue;

import java.util.UUID;

import android.os.Parcel;
import android.os.Parcelable;

public class Track implements Parcelable {

    private UUID id;

    private String path;

    private String performer;

    private String title;

    private int startPosition;

    private int duration;

    private String album;

    public Track() {
        id = UUID.randomUUID();
    }

    public Track(String title) {
        id = UUID.randomUUID();
        this.title = title;
    }

    public String getFile() {
        return path;
    }

    public void setFilePath(String file) {
        this.path = file;
    }

    public String getArtist() {
        return performer;
    }

    public void setArtist(String performer) {
        this.performer = performer;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public int getStartPosition() {
        return startPosition;
    }

    public void setStartPosition(int index01) {
        this.startPosition = index01;
    }

    public int getDuration() {
        return duration;
    }

    public void setDuration(int duration) {
        this.duration = duration;
    }

    public UUID getId() {
        return id;
    }

    public boolean isCue() {
        return startPosition > 0;
    }

    public String getAlbum() {
        return album;
    }

    public void setAlbum(String album) {
        this.album = album;
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        Object[] fieldArray = new Object[7];

        fieldArray[0] = this.id;
        fieldArray[1] = this.path;
        fieldArray[2] = this.performer;
        fieldArray[3] = this.title;
        fieldArray[4] = this.startPosition;
        fieldArray[5] = this.duration;
        fieldArray[6] = this.album;

        dest.writeArray(fieldArray);
    }

    public static final Parcelable.Creator<Track> CREATOR = new Parcelable.Creator<Track>() {
        public Track createFromParcel(Parcel in) {
            return new Track(in);
        }

        public Track[] newArray(int size) {
            return new Track[size];
        }
    };

    private Track(Parcel in) {
        Object[] fieldArray = in.readArray(Track.class.getClassLoader());
        this.id = (UUID) fieldArray[0];
        this.path = (String) fieldArray[1];
        this.performer = (String) fieldArray[2];
        this.title = (String) fieldArray[3];
        this.startPosition = (Integer) fieldArray[4];
        this.duration = (Integer) fieldArray[5];
        this.album = (String) fieldArray[6];
    }

}