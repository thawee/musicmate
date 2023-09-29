package apincer.android.mmate.broadcast;

import android.content.Context;

import apincer.android.mmate.repository.MusicTag;


public interface Callback {
    void onPlaying(Context context, MusicTag song);
}
