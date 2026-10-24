package apincer.music.core.codec;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.List;

public class FfmpegPcmTest {

    @Test
    public void alacKeepsItsRateAndDepth() {
        FfmpegPcm pcm = FfmpegPcm.of("alac", 48_000, "s32p", 24, 2, 180.0);
        assertEquals(48_000, pcm.sampleRate);
        assertEquals(24, pcm.bitsPerSample);
        assertFalse(pcm.floatSamples);
        assertEquals(48_000L * 180, pcm.numFrames);
        assertEquals(6, pcm.frameSize());
    }

    @Test
    public void aiffSixteenBitStaysSixteenBit() {
        FfmpegPcm pcm = FfmpegPcm.of("pcm_s16be", 44_100, "s16", 16, 2, 10.0);
        assertEquals(16, pcm.bitsPerSample);
        assertEquals(List.of("-f", "s16le", "-acodec", "pcm_s16le"),
                pcm.arguments("in.aiff", 0, "pipe").subList(7, 11));
    }

    @Test
    public void dsdBecomesFloatAt88kWhateverItsRate() {
        // ffprobe reports DSD64 at the DSD rate divided by 8
        FfmpegPcm pcm = FfmpegPcm.of("dsd_lsbf_planar", 352_800, "fltp", 0, 2, 60.0);
        assertTrue(pcm.dsd);
        assertTrue(pcm.floatSamples);
        assertEquals(FfmpegPcm.DSD_PCM_RATE, pcm.sampleRate);
        assertEquals(32, pcm.bitsPerSample);
        assertEquals(88_200L * 60, pcm.numFrames);
        List<String> args = pcm.arguments("in.dsf", 0, "pipe");
        assertEquals("88200", args.get(args.indexOf("-ar") + 1));
        assertEquals("pcm_f32le", args.get(args.indexOf("-acodec") + 1));
    }

    @Test
    public void floatHeaderSaysIeeeFloat() {
        FfmpegPcm pcm = FfmpegPcm.of("dsd_msbf", 352_800, "fltp", 0, 2, 1.0);
        ByteBuffer header = ByteBuffer.wrap(pcm.header()).order(ByteOrder.LITTLE_ENDIAN);
        assertEquals(FlacToWav.WAVE_FORMAT_IEEE_FLOAT, header.getShort(20));
        assertEquals(32, header.getShort(34));
        assertEquals(88_200L * 8, header.getInt(40));
        assertEquals(FlacToWav.HEADER_SIZE + 88_200L * 8, pcm.wavLength());
    }

    @Test
    public void integerHeaderIsPlainPcm() {
        FfmpegPcm pcm = FfmpegPcm.of("alac", 44_100, "s16p", 16, 2, 1.0);
        assertEquals(FlacToWav.WAVE_FORMAT_PCM, ByteBuffer.wrap(pcm.header()).order(ByteOrder.LITTLE_ENDIAN).getShort(20));
    }

    @Test
    public void seekStartsAtTheExactFrame() {
        FfmpegPcm pcm = FfmpegPcm.of("alac", 44_100, "s32p", 24, 2, 100.0);
        List<String> args = pcm.arguments("in.m4a", 44_100L * 30 + 1, "pipe");
        assertEquals("30.000023", args.get(args.indexOf("-ss") + 1));
        assertTrue(args.indexOf("-ss") < args.indexOf("-i"));
        assertFalse(pcm.arguments("in.m4a", 0, "pipe").contains("-ss"));
    }

    @Test
    public void unknownFormatIsRejected() {
        assertNull(FfmpegPcm.of("alac", 0, "s16p", 16, 2, 1.0));
        assertNull(FfmpegPcm.of("alac", 44_100, "s16p", 16, 0, 1.0));
        assertNull(FfmpegPcm.of("alac", 44_100, "s16p", 16, 2, -1));
    }

    @Test
    public void secondsPreferTheExactTimeBase() {
        assertEquals(235.12, FfmpegPcm.seconds("10368792", "1/44100", "999"), 1e-9);
        assertEquals(12.5, FfmpegPcm.seconds(null, null, "12.5"), 1e-9);
        assertEquals(-1, FfmpegPcm.seconds("x", "1/44100", null), 0);
    }
}
