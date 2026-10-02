package apincer.android.mmate.service;

import apincer.music.core.playback.PlaybackState.State;

/**
 * Tells a track that an external player app (UAPP, HiBy, Poweramp) finished on its own from one
 * the listener stopped. MusicMate sends such apps one file at a time; when that file ends, the
 * app only reports STOPPED (some report PAUSED at the end), so the queue has to be continued here.
 */
final class ExternalTrackEnd {
    private ExternalTrackEnd() {
    }

    /** STOPPED within 3 s of the end, or PAUSED within 1 s of it (the last known position). */
    static boolean endedNaturally(State state, long lastPositionSeconds, double durationSeconds) {
        if (state == null || durationSeconds <= 0) return false;
        if (state == State.STOPPED) return lastPositionSeconds >= durationSeconds - 3;
        if (state == State.PAUSED) return lastPositionSeconds >= durationSeconds - 1;
        return false;
    }
}
