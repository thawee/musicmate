package apincer.android.mmate.service;

import android.app.Activity;
import android.content.Intent;
import android.os.Parcel;
import android.os.Parcelable;

import apincer.android.mmate.objectbox.AudioTag;

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

    private Status status;
    private AudioTag tagInfo;
    private String command = "";
    private String message = "";
    private int totalItems = 0;
    private int countSuccess = 0;
    private int countError = 0;

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
        parcel.writeString(command);
        parcel.writeString(message);
        parcel.writeInt(totalItems);
        parcel.writeInt(countSuccess);
        parcel.writeInt(countError);
        parcel.writeParcelable(tagInfo, flags);
    }

    private BroadcastData(Parcel in) {
        status = Status.values()[in.readInt()];
        command = in.readString();
        message = in.readString();
        totalItems = in.readInt();
        countSuccess = in.readInt();
        countError = in.readInt();
        tagInfo = in.readParcelable(AudioTag.class.getClassLoader());
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

    public BroadcastData setCommand(String command) {
        this.command = command;
        return this;
    }

    public BroadcastData setMessage(String message) {
        this.message = message;
        return this;
    }

    public BroadcastData setTotalItems(int totalItems) {
        this.totalItems = totalItems;
        return this;
    }

    public BroadcastData setCountSuccess(int countSuccess) {
        this.countSuccess = countSuccess;
        return this;
    }

    public String getCommand() {
        return command;
    }

    public String getMessage() {
        return message;
    }

    public int getTotalItems() {
        return totalItems;
    }

    public int getCountSuccess() {
        return countSuccess;
    }

    public int getCountError() {
        return countError;
    }

    public BroadcastData setCountError(int countError) {
        this.countError = countError;
        return this;
    }

    public AudioTag getTagInfo() {
        return tagInfo;
    }

    public BroadcastData setTagInfo(AudioTag tagInfo) {
        this.tagInfo = tagInfo;
        return this;
    }
}