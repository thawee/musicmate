package apincer.music.core.playback;

import apincer.music.core.model.Track;

public class PlaybackState {
    public enum State { PLAYING, PAUSED, STOPPED, BUFFERING, ERROR }
    public State currentState;
    public long currentPositionSecond;
    public long durationSecond;
    public Track currentTrack;
    public String errorMessage;
}
