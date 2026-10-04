package apincer.music.core.codec;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class MusicAnalyserDynamicRangeTest {

    @Test
    public void silenceHasNoDynamicRange() {
        // Previously +Infinity, stored as ~9.2e16 after rounding
        assertEquals(0.0, MusicAnalyser.calculateDynamicRange(new byte[4096], 16), 0.0);
        assertEquals(0.0, MusicAnalyser.calculateDynamicRange(new byte[4096 * 3], 24), 0.0);
    }

    @Test
    public void signalUsesPeakOverQuietestSample() {
        // 16-bit little-endian samples: 1000 and 10 -> 20*log10(100) = 40 dB
        byte[] pcm = {(byte) 0xE8, 0x03, 0x0A, 0x00};
        assertEquals(40.0, MusicAnalyser.calculateDynamicRange(pcm, 16), 0.001);
    }
}
