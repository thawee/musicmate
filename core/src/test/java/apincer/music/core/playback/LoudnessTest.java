package apincer.music.core.playback;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

import java.util.Arrays;

public class LoudnessTest {
    // Captured from ffmpeg 9.0.2: a 5 s 440 Hz tone at -6 dB through ebur128=peak=true
    private static final String TONE_LOG =
            "[Parsed_ebur128_0 @ 0xa9b004e40] t: 0.0999773  TARGET:-23 LUFS    M:-120.7 S:-120.7     I: -70.0 LUFS       LRA:   0.0 LU  FTPK: -24.1 dBFS  TPK: -24.1 dBFS\n"
            + "[Parsed_ebur128_0 @ 0xa9b004e40] t: 0.399977   TARGET:-23 LUFS    M: -27.8 S:-120.7     I: -27.8 LUFS       LRA:   0.0 LU  FTPK: -24.1 dBFS  TPK: -24.1 dBFS\n"
            + "[Parsed_ebur128_0 @ 0xa84c34900] Summary:\n"
            + "\n"
            + "  Integrated loudness:\n"
            + "    I:         -27.8 LUFS\n"
            + "    Threshold: -37.8 LUFS\n"
            + "\n"
            + "  Loudness range:\n"
            + "    LRA:         0.0 LU\n"
            + "    Threshold: -47.8 LUFS\n"
            + "    LRA low:   -27.8 LUFS\n"
            + "    LRA high:  -27.8 LUFS\n"
            + "\n"
            + "  True peak:\n"
            + "    Peak:      -24.1 dBFS\n"
            + "[out#0/null @ 0xa84c34180] video:0KiB audio:431KiB\n";

    private static final String SILENCE_LOG =
            "[Parsed_ebur128_0 @ 0x96b058900] Summary:\n\n  Integrated loudness:\n    I:         -70.0 LUFS\n"
            + "    Threshold:   0.0 LUFS\n\n  True peak:\n    Peak:       -inf dBFS\n";

    @Test
    public void readsTheSummaryNotTheFirstFrameLine() {
        Loudness loudness = Loudness.parseEbur128(TONE_LOG, 5.0);
        assertEquals(-27.8, loudness.integratedLufs, 1e-9);
        assertEquals(-24.1, loudness.truePeakDbfs, 1e-9);
        assertEquals(9.8, loudness.gainDb(), 1e-9);
        assertEquals(0.0624, loudness.peakLinear(), 1e-4);
    }

    @Test
    public void silenceAndTruncatedLogsGiveNoMeasurement() {
        assertNull(Loudness.parseEbur128(SILENCE_LOG, 3.0));
        assertNull(Loudness.parseEbur128(TONE_LOG.substring(0, TONE_LOG.indexOf("True peak")), 5.0));
        assertNull(Loudness.parseEbur128("[out#0/null] error", 5.0));
        assertNull(Loudness.parseEbur128(null, 5.0));
    }

    @Test
    public void albumIsTheDurationWeightedEnergyMeanAndTheHighestPeak() {
        Loudness album = Loudness.album(Arrays.asList(
                new Loudness(-10.0, -0.5, 300),
                new Loudness(-20.0, -3.0, 100)));
        // 10*log10((300*0.1 + 100*0.01) / 400) = -11.1070
        assertEquals(-11.1070, album.integratedLufs, 1e-4);
        assertEquals(-0.5, album.truePeakDbfs, 1e-9);
        assertEquals(400, album.seconds, 1e-9);
    }

    @Test
    public void equallyLoudTracksMakeAnAlbumAtThatLoudness() {
        Loudness album = Loudness.album(Arrays.asList(new Loudness(-14.0, -1.0, 200), new Loudness(-14.0, -2.0, 50)));
        assertEquals(-14.0, album.integratedLufs, 1e-9);
        assertEquals(-4.0, album.gainDb(), 1e-9);
    }
}
