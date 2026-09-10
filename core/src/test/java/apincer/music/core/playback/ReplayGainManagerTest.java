package apincer.music.core.playback;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class ReplayGainManagerTest {

    private static final double EPSILON = 0.001;

    @Test
    public void testParseGain() {
        assertEquals(-6.50, ReplayGainManager.parseGain("-6.50 dB"), EPSILON);
        assertEquals(-6.50, ReplayGainManager.parseGain("-6.50db"), EPSILON);
        assertEquals(2.35, ReplayGainManager.parseGain("+2.35 dB"), EPSILON);
        assertEquals(0.0, ReplayGainManager.parseGain("0.0 dB"), EPSILON);
        assertEquals(4.12, ReplayGainManager.parseGain("4.12"), EPSILON);
        assertEquals(0.0, ReplayGainManager.parseGain("invalid"), EPSILON);
        assertEquals(0.0, ReplayGainManager.parseGain(null), EPSILON);
    }

    @Test
    public void testParsePeak() {
        assertEquals(0.988231, ReplayGainManager.parsePeak("0.988231"), EPSILON);
        assertEquals(1.0, ReplayGainManager.parsePeak("1.0"), EPSILON);
        // dB peak specification
        double expectedPeak = Math.pow(10.0, -1.0 / 20.0);
        assertEquals(expectedPeak, ReplayGainManager.parsePeak("-1.0 dB"), EPSILON);
        assertEquals(1.0, ReplayGainManager.parsePeak("invalid"), EPSILON);
        assertEquals(1.0, ReplayGainManager.parsePeak(null), EPSILON);
    }

    @Test
    public void testReplayGainInfoDisplay() {
        ReplayGainManager.ReplayGainInfo info = new ReplayGainManager.ReplayGainInfo();
        info.trackGainDb = -4.2;
        info.hasTrackGain = true;
        info.albumGainDb = -5.1;
        info.hasAlbumGain = true;

        assertEquals("RG -4.2 dB", info.getDisplayString("track"));
        assertEquals("RG -5.1 dB (Album)", info.getDisplayString("album"));
    }

    @Test
    public void testGainLinearCalculation() {
        // Gain calculation formula: linear = 10^(gainDb / 20)
        double gain0Db = Math.pow(10.0, 0.0 / 20.0);
        assertEquals(1.0, gain0Db, EPSILON);

        double gainMinus6Db = Math.pow(10.0, -6.0206 / 20.0);
        assertEquals(0.5, gainMinus6Db, EPSILON);

        // Peak limiter anti-clipping formula: if (linear * peak > 1.0) linear = 1.0 / peak
        double peak = 1.2;
        double requestedLinear = 1.5;
        assertTrue("Linear * peak exceeds 1.0", requestedLinear * peak > 1.0);
        double safeLinear = 1.0 / peak;
        assertTrue("Safe linear * peak does not exceed 1.0", safeLinear * peak <= 1.0 + EPSILON);
    }
}
