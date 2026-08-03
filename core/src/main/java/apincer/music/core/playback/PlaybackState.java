package apincer.music.core.playback;

import apincer.music.core.model.Track;

public class PlaybackState {
    public enum State { PLAYING, PAUSED, STOPPED, BUFFERING, ERROR }
    public State currentState;
    public long currentPositionSecond;
    public long durationSecond;
    public Track currentTrack;
    public String errorMessage;

    /**
     * Creates a shallow copy of this state.
     * Required because StateFlow uses reference equality — setValue() with the same
     * object instance is silently dropped, even if fields have been mutated.
     */
    public PlaybackState copy() {
        PlaybackState copy = new PlaybackState();
        copy.currentState = this.currentState;
        copy.currentPositionSecond = this.currentPositionSecond;
        copy.durationSecond = this.durationSecond;
        copy.currentTrack = this.currentTrack;
        copy.errorMessage = this.errorMessage;
        return copy;
    }
}
