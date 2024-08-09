package apincer.android.mmate.player;

import android.content.Context;

import apincer.android.mmate.repository.MusicTag;


public interface PlayerCallback {
    void onPlaying(Context context, MusicTag song);
}
