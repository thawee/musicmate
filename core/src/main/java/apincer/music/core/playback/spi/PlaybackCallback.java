package apincer.music.core.playback.spi;

import apincer.music.core.model.Track;
import apincer.music.core.playback.PlaybackState;

public abstract class PlaybackCallback {
    public PlaybackCallback() {
    }

    public void onMediaTrackChanged(Track metadata) {
    }

    public void onPlaybackStateChanged(PlaybackState state) {
    }

    public void onPlaybackStateTimeElapsedSeconds(long elapsedSeconds) {
    }

    public void onMediaTrackChanged(String title, String artist, String album, long duration) {

    }

    public void onPlaybackTargetChanged(PlaybackTarget playbackTarget) {

    }

    public void onPlaybackCompleted() {
    }
}