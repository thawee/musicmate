package apincer.music.core.playback;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class DMRPlayerTest {

    @Test
    public void portablePlayers_areNotFaded() {
        DMRPlayer r3 = (DMRPlayer) DMRPlayer.Factory.create("uuid:1234", "HiBy R3", "192.168.1.50");
        assertEquals(DMRPlayer.DeviceProfile.HIBY, r3.getDeviceProfile());
        assertFalse(r3.getDeviceProfile().canFadeVolume());
        assertFalse(DMRPlayer.DeviceProfile.SHANLING.canFadeVolume());
        assertFalse(DMRPlayer.DeviceProfile.FIIO.canFadeVolume());
    }

    @Test
    public void streamers_areFaded() {
        assertTrue(DMRPlayer.DeviceProfile.WIIM.canFadeVolume());
        assertTrue(DMRPlayer.DeviceProfile.GENERIC.canFadeVolume());
    }
}
