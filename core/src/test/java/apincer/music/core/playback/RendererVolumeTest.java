package apincer.music.core.playback;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class RendererVolumeTest {

    @Test
    public void percentFollowsThePlayersOwnRange() {
        // A player with volume 0..30: its 15 is half way, not 15%
        assertEquals(50, RendererVolume.toPercent(15, 0, 30));
        assertEquals(100, RendererVolume.toPercent(30, 0, 30));
        assertEquals(15L, RendererVolume.toUnits(50, 0, 30));
        assertEquals(24L, RendererVolume.toUnits(80, 0, 30));
    }

    @Test
    public void aMissingRangeMeansZeroToHundred() {
        assertEquals(42, RendererVolume.toPercent(42, 0, 0));
        assertEquals(42L, RendererVolume.toUnits(42, 0, 0));
    }

    @Test
    public void outOfRangeValuesAreKeptInside() {
        assertEquals(100, RendererVolume.toPercent(40, 0, 30));
        assertEquals(0, RendererVolume.toPercent(-5, 0, 30));
        assertEquals(30L, RendererVolume.toUnits(120, 0, 30));
    }

    @Test
    public void requestsStopAtTheLimit() {
        assertEquals(80, RendererVolume.clamp(95, 80));
        assertEquals(40, RendererVolume.clamp(40, 80));
        assertEquals(0, RendererVolume.clamp(-10, 80));
        // 100 is "Off": anything up to full volume
        assertEquals(100, RendererVolume.clamp(100, 100));
        // A broken setting falls back to the default limit, never to full volume
        assertEquals(RendererVolume.DEFAULT_LIMIT, RendererVolume.clamp(100, 0));
    }

    @Test
    public void unknownVolumeCanOnlyStartWithAnExplicitLowRequest() {
        assertTrue(RendererVolume.isSafeInitialRequest(5));
        assertTrue(RendererVolume.isSafeInitialRequest(0));
        assertFalse(RendererVolume.isSafeInitialRequest(6));
        assertFalse(RendererVolume.isSafeInitialRequest(100));
        assertFalse(RendererVolume.isSafeInitialRequest(-1));
    }
}
