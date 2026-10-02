package apincer.music.core.codec;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.security.MessageDigest;
import java.util.Arrays;

import io.nayuki.flac.common.StreamInfo;
import io.nayuki.flac.encode.BitOutputStream;
import io.nayuki.flac.encode.FlacEncoder;
import io.nayuki.flac.encode.RandomAccessFileOutputStream;
import io.nayuki.flac.encode.SubframeEncoder;

public class FlacToWavTest {
    @Rule
    public TemporaryFolder temporary = new TemporaryFolder();

    private static final int SAMPLES = 100_000;

    /** Deterministic stereo samples within the bit depth. */
    private static int[][] samples(int depth) {
        int[][] s = new int[2][SAMPLES];
        int max = (1 << (depth - 1)) - 1;
        long x = 12345;
        for (int i = 0; i < SAMPLES; i++) {
            x = x * 6364136223846793005L + 1442695040888963407L;
            s[0][i] = (int) (Math.sin(i / 20.0) * max * 0.8) + (int) ((x >>> 40) % 64) - 32;
            s[1][i] = (int) (Math.cos(i / 33.0) * max * 0.5);
        }
        return s;
    }

    private File flac(int[][] samples, int depth) throws IOException {
        File out = temporary.newFile(depth + "bit.flac");
        try (RandomAccessFile raf = new RandomAccessFile(out, "rw")) {
            BitOutputStream bits = new BitOutputStream(new BufferedOutputStream(new RandomAccessFileOutputStream(raf)));
            bits.writeInt(32, 0x664C6143);
            StreamInfo info = new StreamInfo();
            info.sampleRate = 96_000;
            info.numChannels = 2;
            info.sampleDepth = depth;
            info.numSamples = samples[0].length;
            info.md5Hash = StreamInfo.getMd5Hash(samples, depth);
            info.write(true, bits);
            new FlacEncoder(info, samples, 4096, SubframeEncoder.SearchOptions.SUBSET_MEDIUM, bits);
            bits.flush();
            raf.seek(4);
            info.write(true, bits);
            bits.flush();
        }
        return out;
    }

    private static byte[] convert(File flac, long from, long to) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        FlacToWav.open(flac).write(flac, from, to, out::write);
        return out.toByteArray();
    }

    @Test
    public void wholeFile_24bit_isAWavWhoseDataMatchesTheFlacMd5() throws Exception {
        int[][] s = samples(24);
        File f = flac(s, 24);
        FlacToWav wav = FlacToWav.open(f);
        assertEquals(44 + SAMPLES * 2L * 3, wav.wavLength());
        byte[] all = convert(f, 0, wav.wavLength() - 1);
        assertEquals(wav.wavLength(), all.length);

        ByteBuffer h = ByteBuffer.wrap(all, 0, 44).order(ByteOrder.LITTLE_ENDIAN);
        assertEquals("RIFF", new String(all, 0, 4, "US-ASCII"));
        assertEquals(all.length - 8, h.getInt(4));
        assertEquals("WAVE", new String(all, 8, 4, "US-ASCII"));
        assertEquals(1, h.getShort(20));          // PCM
        assertEquals(2, h.getShort(22));          // channels
        assertEquals(96_000, h.getInt(24));       // sample rate (native, not resampled)
        assertEquals(96_000 * 6, h.getInt(28));   // byte rate
        assertEquals(6, h.getShort(32));          // block align
        assertEquals(24, h.getShort(34));         // bits (native depth)
        assertEquals("data", new String(all, 36, 4, "US-ASCII"));
        assertEquals(all.length - 44, h.getInt(40));

        // Bit-perfect: the PCM is exactly what the FLAC's own MD5 describes
        byte[] md5 = MessageDigest.getInstance("MD5").digest(Arrays.copyOfRange(all, 44, all.length));
        assertArrayEquals(StreamInfo.getMd5Hash(s, 24), md5);
        // and the first frame is the first samples, little-endian
        int left = (all[44] & 0xFF) | (all[45] & 0xFF) << 8 | all[46] << 16;
        assertEquals(s[0][0], left);
    }

    @Test
    public void wholeFile_16bit() throws Exception {
        int[][] s = samples(16);
        File f = flac(s, 16);
        byte[] all = convert(f, 0, FlacToWav.open(f).wavLength() - 1);
        assertEquals(44 + SAMPLES * 4L, all.length);
        byte[] md5 = MessageDigest.getInstance("MD5").digest(Arrays.copyOfRange(all, 44, all.length));
        assertArrayEquals(StreamInfo.getMd5Hash(s, 16), md5);
    }

    @Test
    public void ranges_matchTheSameBytesOfTheWholeFile() throws Exception {
        File f = flac(samples(24), 24);
        long length = FlacToWav.open(f).wavLength();
        byte[] all = convert(f, 0, length - 1);
        long[][] ranges = {
                {44 + 6L * 12_345 + 2, 44 + 6L * 12_345 + 5_001}, // mid-file, starting inside a frame
                {30, 200},                                         // across the end of the header
                {length - 777, length - 1},                        // the last bytes
                {44 + 6L * 70_000, length - 1},                    // a seek far into the file
        };
        for (long[] r : ranges) {
            assertArrayEquals("bytes " + r[0] + "-" + r[1],
                    Arrays.copyOfRange(all, (int) r[0], (int) r[1] + 1), convert(f, r[0], r[1]));
        }
    }
}
