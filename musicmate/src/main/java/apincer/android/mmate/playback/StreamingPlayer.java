
package apincer.android.mmate.playback;

import android.content.Context;
import android.graphics.drawable.Drawable;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import apincer.android.mmate.repository.database.MusicTag;

public class StreamingPlayer implements Player {
    private final Context context;
    private final String ipAddress;
    private final String displayName;
    private final MutableLiveData<NowPlaying> nowPlaying = new MutableLiveData<>();
    private final MutableLiveData<Boolean> isConnected = new MutableLiveData<>(true);

    public StreamingPlayer(Context context, String ipAddress, String displayName) {
        this.context = context;
        this.ipAddress = ipAddress;
        this.displayName = displayName;
    }

    @Override
    public String getDisplayName() {
        return displayName;
    }

    @Override
    public String getId() {
        return ipAddress;
    }

    @Override
    public Drawable getIcon() {
        return null;
    }

    @Override
    public void play(MusicTag song) {

    }

    @Override
    public void next() {

    }

    @Override
    public void pause() {
    }

    @Override
    public void resume() {
    }

    @Override
    public void stop() {
    }

    @Override
    public String getDetails() {
        return ipAddress;
    }
}
