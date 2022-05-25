package apincer.android.mmate.broadcast;

import android.content.Context;

import apincer.android.mmate.objectbox.AudioTag;

public interface Callback {
    void onPlaying(Context context, AudioTag song);
}
