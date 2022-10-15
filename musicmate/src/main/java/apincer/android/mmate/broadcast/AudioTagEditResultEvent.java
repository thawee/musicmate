package apincer.android.mmate.broadcast;

import apincer.android.mmate.objectbox.MusicTag;

public class AudioTagEditResultEvent {

    public static final String ACTION_DELETE = "DELETE";
    public static final String ACTION_MOVE = "MOVE";
    public static final String ACTION_UPDATE = "UPDATE";

    public String getAction() {
        return action;
    }

    public String getStatus() {
        return status;
    }

    public final String action;
    public final String status;
    public final MusicTag item;

    public AudioTagEditResultEvent(String message, String status, MusicTag item) {
        this.action = message;
        this.status = status;
        this.item = item;
    }

    public MusicTag getItem() {
        return item;
    }
}