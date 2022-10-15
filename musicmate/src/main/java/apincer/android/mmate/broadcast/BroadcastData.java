package apincer.android.mmate.broadcast;

import android.app.Activity;
import android.content.Intent;
import android.os.Parcel;
import android.os.Parcelable;

import apincer.android.mmate.objectbox.MusicTag;

/**
 * Class which contains all the data passed in broadcast intents to notify task progress, errors,
 * completion or cancellation.
 *
 * @author gotev (Aleksandar Gotev)
 */
public class BroadcastData implements Parcelable {
    public static final String BROADCAST_ACTION = "com.apincer.mmate.BROADCAST_ACTION";
    public static final String BROADCAST_DATA = "BROADCAST_DATA";
    public enum Status {
        IN_PROGRESS,
        ERROR,
        COMPLETED,
        CANCELLED
    }
    public enum Action {
        IMPORT,
        UPDATE,
        DELETE,
        PLAYING
    }

    private Status status;
    private MusicTag tagInfo;
    private Action action;
    private String message = "";

    public BroadcastData() {

    }

    public static BroadcastData getBroadcastData(Intent intent) {
        return intent.getParcelableExtra(BROADCAST_DATA);
    }

    public Intent getIntent() {
        Intent intent = new Intent(BROADCAST_ACTION);
        intent.setPackage("com.apincer.mmate");
        intent.putExtra("resultCode", Activity.RESULT_OK);
        intent.putExtra(BROADCAST_DATA, this);
        return intent;
    }



    // This is used to regenerate the object.
    // All Parcelables must have a CREATOR that implements these two methods
    public static final Parcelable.Creator<BroadcastData> CREATOR =
            new Parcelable.Creator<BroadcastData>() {
                @Override
                public BroadcastData createFromParcel(final Parcel in) {
                    return new BroadcastData(in);
                }

                @Override
                public BroadcastData[] newArray(final int size) {
                    return new BroadcastData[size];
                }
            };

    @Override
    public void writeToParcel(Parcel parcel, int flags) {
        parcel.writeInt(status.ordinal());
        parcel.writeInt(action.ordinal());
        parcel.writeString(message);
        parcel.writeParcelable(tagInfo, flags);
    }

    private BroadcastData(Parcel in) {
        status = Status.values()[in.readInt()];
        action = Action.values()[in.readInt()];
        message = in.readString();
        tagInfo = in.readParcelable(MusicTag.class.getClassLoader());
    }

    @Override
    public int describeContents() {
        return 0;
    }

    public Status getStatus() {
        if (status == null) {
            return Status.CANCELLED;
        }

        return status;
    }

    public BroadcastData setStatus(Status status) {
        this.status = status;
        return this;
    }

    public BroadcastData setAction(Action action) {
        this.action = action;
        return this;
    }

    public BroadcastData setMessage(String message) {
        this.message = message;
        return this;
    }

    public Action getAction() {
        return action;
    }

    public String getMessage() {
        return message;
    }

    public MusicTag getTagInfo() {
        return tagInfo;
    }

    public BroadcastData setTagInfo(MusicTag tagInfo) {
        this.tagInfo = tagInfo;
        return this;
    }
}