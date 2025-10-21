package apincer.music.core.playback.spi;

import apincer.music.core.playback.PlaybackState;

public abstract class PlaybackCallback {
    public PlaybackCallback() {
    }

    public void onMediaTrackChanged(MediaTrack metadata) {
        throw new RuntimeException("Stub!");
    }

    public void onPlaybackStateChanged(PlaybackState state) {
        throw new RuntimeException("Stub!");
    }

    public void onPlaybackStateTimeElapsedSeconds(long elapsedSeconds) {
        throw new RuntimeException("Stub!");
    }

    public void onMediaTrackChanged(String title, String artist, String album, long duration) {

    }

    public void onPlaybackTargetChanged(PlaybackTarget playbackTarget) {

    }
}