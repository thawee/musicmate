package apincer.music.core.playback;

import apincer.music.core.playback.spi.MediaTrack;

public class PlaybackState {
    public enum State { PLAYING, PAUSED, STOPPED, BUFFERING, ERROR }
    public State currentState;
    public long currentPositionMs;
    public long durationMs;
    public MediaTrack currentTrack;
    public String errorMessage;
}
