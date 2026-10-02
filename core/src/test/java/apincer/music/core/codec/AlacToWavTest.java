package apincer.music.core.codec;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class AlacToWavTest {

    @Test
    public void sampleCount_fromFfprobeDurationAndTimeBase() {
        // the usual MP4 audio time base is the sample rate itself
        assertEquals(10_369_380, AlacToWav.sampleCount("10369380", "1/44100", 44_100));
        // a coarser time base still converts to frames
        assertEquals(44_100 * 3, AlacToWav.sampleCount("3000", "1/1000", 44_100));
        assertEquals(-1, AlacToWav.sampleCount(null, "1/44100", 44_100));
        assertEquals(-1, AlacToWav.sampleCount("100", null, 44_100));
    }
}
