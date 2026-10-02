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
        assertFalse(PlayerPriority.mayTakeOver(LOCAL, true, false, STREAM));
        assertFalse(PlayerPriority.mayTakeOver(EXTERNAL_APP, true, false, STREAM));
        assertFalse(PlayerPriority.mayTakeOver(RENDERER, true, false, STREAM));
    }

    @Test
    public void anIdlePlayer_neverBlocksOneThatStarts() {
        // Seen on the phone: an idle browser stream blocked Poweramp the listener had started
        assertTrue(PlayerPriority.mayTakeOver(STREAM, false, false, EXTERNAL_APP));
        assertTrue(PlayerPriority.mayTakeOver(LOCAL, false, false, EXTERNAL_APP));
        assertTrue(PlayerPriority.mayTakeOver(LOCAL, false, false, STREAM));
    }

    @Test
    public void twoStartingAtOnce_theHigherRankWins() {
        // the current one took over automatically moments ago and is (now) playing
        assertTrue(PlayerPriority.mayTakeOver(EXTERNAL_APP, true, true, STREAM));
        assertFalse(PlayerPriority.mayTakeOver(STREAM, true, true, EXTERNAL_APP));
        assertFalse(PlayerPriority.mayTakeOver(LOCAL, true, true, LOCAL));
    }
}
