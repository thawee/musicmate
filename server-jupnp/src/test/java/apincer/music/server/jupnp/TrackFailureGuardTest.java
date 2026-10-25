package apincer.music.server.jupnp;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class TrackFailureGuardTest {

    @Test
    public void skipsRefusedTracksThenStopsAfterThreeInARow() {
        TrackFailureGuard guard = new TrackFailureGuard();
        assertTrue(guard.shouldSkip());
        assertTrue(guard.shouldSkip());
        assertTrue(guard.shouldSkip());
        assertFalse("a fourth refusal in a row stops the queue", guard.shouldSkip());
        assertTrue("the next play attempt starts a fresh count", guard.shouldSkip());
    }

    @Test
    public void aTrackThatPlaysResetsTheCount() {
        TrackFailureGuard guard = new TrackFailureGuard();
        guard.shouldSkip();
        guard.shouldSkip();
        guard.playbackProgressed();
        assertTrue(guard.shouldSkip());
        assertTrue(guard.shouldSkip());
        assertTrue(guard.shouldSkip());
        assertFalse(guard.shouldSkip());
    }
}
