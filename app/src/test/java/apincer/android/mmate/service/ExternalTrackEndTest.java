package apincer.android.mmate.service;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import apincer.music.core.playback.PlaybackState.State;

public class ExternalTrackEndTest {

    @Test
    public void stoppedNearTheEnd_isANaturalEnd() {
        assertTrue(ExternalTrackEnd.endedNaturally(State.STOPPED, 238, 240.0));
        assertTrue(ExternalTrackEnd.endedNaturally(State.STOPPED, 240, 240.0));
    }

    @Test
    public void someAppsPauseAtTheEnd() {
        assertTrue(ExternalTrackEnd.endedNaturally(State.PAUSED, 240, 240.4));
        assertFalse("a pause a few seconds before the end is the listener",
                ExternalTrackEnd.endedNaturally(State.PAUSED, 236, 240.0));
    }

    @Test
    public void stoppedMidTrack_isTheListener() {
        assertFalse(ExternalTrackEnd.endedNaturally(State.STOPPED, 120, 240.0));
    }

    @Test
    public void playingOrUnknownLength_isNotAnEnd() {
        assertFalse(ExternalTrackEnd.endedNaturally(State.PLAYING, 240, 240.0));
        assertFalse(ExternalTrackEnd.endedNaturally(State.STOPPED, 240, 0));
        assertFalse(ExternalTrackEnd.endedNaturally(null, 240, 240.0));
    }
}
