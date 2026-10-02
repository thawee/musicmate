package apincer.android.mmate.service;

import static apincer.android.mmate.service.PlayerPriority.EXTERNAL_APP;
import static apincer.android.mmate.service.PlayerPriority.LOCAL;
import static apincer.android.mmate.service.PlayerPriority.RENDERER;
import static apincer.android.mmate.service.PlayerPriority.STREAM;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import apincer.music.core.playback.DMRPlayer;
import apincer.music.core.playback.WebStreamingPlayer;

public class PlayerPriorityTest {

    @Test
    public void ranks_followTheAgreedOrder() {
        assertTrue(STREAM > RENDERER && RENDERER > LOCAL && LOCAL > EXTERNAL_APP);
        assertEquals(STREAM, PlayerPriority.rank(WebStreamingPlayer.Factory.create("192.168.1.20", "LG WebOSTV", "192.168.1.20"), false));
        assertEquals(RENDERER, PlayerPriority.rank(DMRPlayer.Factory.create("uuid:wiim", "WiiM", "192.168.1.30"), true));
        // a renderer MusicMate only follows (driven by another app) counts as a stream
        assertEquals(STREAM, PlayerPriority.rank(DMRPlayer.Factory.create("uuid:wiim", "WiiM", "192.168.1.30"), false));
    }

    @Test
    public void nothingInterruptsWhatIsPlaying() {
        assertFalse(PlayerPriority.mayTakeOver(LOCAL, true, STREAM));
        assertFalse(PlayerPriority.mayTakeOver(EXTERNAL_APP, true, STREAM));
        assertFalse(PlayerPriority.mayTakeOver(RENDERER, true, STREAM));
    }

    @Test
    public void whenIdle_aHigherOrEqualSourceTakesOver() {
        assertTrue(PlayerPriority.mayTakeOver(LOCAL, false, STREAM));
        assertTrue(PlayerPriority.mayTakeOver(RENDERER, false, STREAM));
        assertTrue(PlayerPriority.mayTakeOver(STREAM, false, STREAM)); // another TV after the first stopped
        assertTrue(PlayerPriority.mayTakeOver(EXTERNAL_APP, false, LOCAL));
    }

    @Test
    public void whenIdle_aLowerSourceDoesNot() {
        assertFalse(PlayerPriority.mayTakeOver(LOCAL, false, EXTERNAL_APP));
        assertFalse(PlayerPriority.mayTakeOver(RENDERER, false, EXTERNAL_APP));
    }
}
