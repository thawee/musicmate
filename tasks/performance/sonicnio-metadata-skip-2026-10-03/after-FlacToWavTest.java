package apincer.music.core.codec;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.EOFException;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.lang.reflect.Field;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.security.MessageDigest;
import java.nio.file.Files;
import java.util.Arrays;

import io.nayuki.flac.common.StreamInfo;
import io.nayuki.flac.decode.FlacDecoder;
import io.nayuki.flac.decode.DataFormatException;
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

    private File withMetadata(File original, int[] types, byte[][] payloads) throws IOException {
        byte[] encoded = Files.readAllBytes(original.toPath());
        encoded[4] &= 0x7F; // STREAMINFO is followed by the inserted blocks.
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(encoded, 0, 42);
        for (int i = 0; i < types.length; i++) {
            int length = payloads[i].length;
            out.write(types[i] | (i == types.length - 1 ? 0x80 : 0));
            out.write(length >>> 16);
            out.write(length >>> 8);
            out.write(length);
            out.write(payloads[i]);
        }
        out.write(encoded, 42, encoded.length - 42);
        File result = temporary.newFile();
        Files.write(result.toPath(), out.toByteArray());
        return result;
    }

    private void sameMetadataFailure(File file) throws Exception {
        Exception legacy;
        try (FlacDecoder decoder = new FlacDecoder(file)) {
            legacy = assertThrows(Exception.class, () -> {
                while (decoder.readAndHandleMetadataBlock() != null) { }
            });
        }
        try (FlacDecoder decoder = new FlacDecoder(file)) {
            Exception streaming = assertThrows(Exception.class, decoder::readMetadataForAudio);
            assertEquals(legacy.getClass(), streaming.getClass());
        }
    }

    @Test
    public void skippedLargeMetadata_preservesWavRangesAndSeekTableOffsets() throws Exception {
        for (int depth : new int[]{16, 24}) {
            File original = flac(samples(depth), depth);
            byte[] seekPoint = ByteBuffer.allocate(18).putLong(0).putLong(0).putShort((short)4096).array();
            File tagged = withMetadata(original, new int[]{3, 6, 1, 126, 127},
                    new byte[][]{seekPoint, new byte[1024 * 1024], new byte[0], new byte[257], new byte[0]});
            long length = FlacToWav.open(original).wavLength();
            byte[] expected = convert(original, 0, length - 1);
            assertArrayEquals(expected, convert(tagged, 0, length - 1));
            long start = 44 + (depth / 8L) * 2 * 12345 + 1;
            assertArrayEquals(Arrays.copyOfRange(expected, (int)start, (int)start + 65536),
                    convert(tagged, start, start + 65535));
            File withoutSeekTable = withMetadata(original, new int[]{6}, new byte[][]{new byte[1024 * 1024]});
            assertArrayEquals(Arrays.copyOfRange(expected, (int)start, (int)start + 65536),
                    convert(withoutSeekTable, start, start + 65535));
            try (FlacDecoder decoder = new FlacDecoder(tagged)) {
                decoder.readMetadataForAudio();
                assertEquals(1, decoder.seekTable.points.size());
                int[][] decoded = new int[2][65536];
                assertEquals(4096, decoder.readAudioBlock(decoded, 0));
                assertEquals(samples(depth)[0][0], decoded[0][0]);
            }
        }
    }

    @Test
    public void streamingMetadata_canFollowLegacyAndIsIdempotent() throws Exception {
        File tagged = withMetadata(flac(samples(16), 16), new int[]{4, 3, 6},
                new byte[][]{new byte[4096], new byte[0], new byte[8193]});
        try (FlacDecoder decoder = new FlacDecoder(tagged)) {
            assertEquals(0, decoder.readAndHandleMetadataBlock()[0]);
            Object[] comment = decoder.readAndHandleMetadataBlock();
            assertArrayEquals(new byte[4096], (byte[])comment[1]);
            decoder.readMetadataForAudio();
            decoder.readMetadataForAudio();
            assertNull(decoder.readAndHandleMetadataBlock());
            assertNotNull(decoder.seekTable);
            int[][] decoded = new int[2][65536];
            assertEquals(samples(16)[0][12345],
                    decodedSampleAt(decoder, decoded, 12345));
        }
    }

    private static int decodedSampleAt(FlacDecoder decoder, int[][] decoded, int frame) throws IOException {
        decoder.seekAndReadAudioBlock(frame, decoded, 0);
        return decoded[0][0];
    }

    @Test
    public void streamingMetadata_rejectsTruncatedUnusedPayloadAndHeader() throws Exception {
        File file = withMetadata(flac(samples(16), 16), new int[]{6}, new byte[][]{new byte[8193]});
        byte[] bytes = Files.readAllBytes(file.toPath());
        for (int end : new int[]{43, 45, 46, 46 + 8192}) {
            Files.write(file.toPath(), Arrays.copyOf(bytes, end));
            try (FlacDecoder decoder = new FlacDecoder(file)) {
                assertThrows(EOFException.class, decoder::readMetadataForAudio);
            }
            sameMetadataFailure(file);
        }
    }

    @Test
    public void streamingMetadata_preservesDuplicateAndLengthChecks() throws Exception {
        File original = flac(samples(16), 16);
        byte[] info = Arrays.copyOfRange(Files.readAllBytes(original.toPath()), 8, 42);
        sameMetadataFailure(withMetadata(original, new int[]{0}, new byte[][]{info}));
        sameMetadataFailure(withMetadata(original, new int[]{3, 3}, new byte[][]{new byte[0], new byte[0]}));
        sameMetadataFailure(withMetadata(original, new int[]{3}, new byte[][]{new byte[17]}));
        byte[] badInfo = Files.readAllBytes(original.toPath());
        badInfo[7] = 33;
        Files.write(original.toPath(), badInfo);
        sameMetadataFailure(original);
    }

    @Test
    public void streamingMetadata_preservesStreamInfoFirstCheck() throws Exception {
        File file = flac(samples(16), 16);
        byte[] bytes = Files.readAllBytes(file.toPath());
        bytes[4] = (byte)0x86;
        Files.write(file.toPath(), bytes);
        sameMetadataFailure(file);
    }

    @Test
    public void skippedMetadata_preservesAudioCrcValidation() throws Exception {
        File file = withMetadata(flac(samples(16), 16), new int[]{6}, new byte[][]{new byte[1024 * 1024]});
        byte[] bytes = Files.readAllBytes(file.toPath());
        bytes[bytes.length - 1] ^= 1;
        Files.write(file.toPath(), bytes);
        long length = FlacToWav.open(file).wavLength();
        assertEquals("CRC-16 mismatch", assertThrows(DataFormatException.class,
                () -> convert(file, 0, length - 1)).getMessage());
    }

    @Test
    public void metadataOnly_doesNotAllocateAudioWorkspace_andAudioReadsReuseIt() throws Exception {
        int[][] expected = samples(16);
        File f = flac(expected, 16);
        Field workspace = FlacDecoder.class.getDeclaredField("frameDec");
        workspace.setAccessible(true);
        try (FlacDecoder decoder = new FlacDecoder(f)) {
            while (decoder.readAndHandleMetadataBlock() != null) {
                assertNull(workspace.get(decoder));
            }
            assertNull(workspace.get(decoder));
            assertEquals(SAMPLES, decoder.streamInfo.numSamples);
            int[][] actual = new int[2][65536];
            assertEquals(4096, decoder.readAudioBlock(actual, 0));
            Object first = workspace.get(decoder);
            assertNotNull(first);
            assertArrayEquals(Arrays.copyOf(expected[0], 4096), Arrays.copyOf(actual[0], 4096));
            assertEquals(4096, decoder.readAudioBlock(actual, 0));
            assertSame(first, workspace.get(decoder));
            assertArrayEquals(Arrays.copyOfRange(expected[1], 4096, 8192), Arrays.copyOf(actual[1], 4096));
            decoder.seekAndReadAudioBlock(12345, actual, 0);
            assertSame(first, workspace.get(decoder));
            assertEquals(expected[0][12345], actual[0][0]);
        }
    }

    @Test
    public void audioReadAndSeek_requireCompletedMetadata_andRejectClosedDecoder() throws Exception {
        File f = flac(samples(16), 16);
        FlacDecoder decoder = new FlacDecoder(f);
        try {
            assertThrows(IllegalStateException.class, () -> decoder.readAudioBlock(null, 0));
            assertThrows(IllegalStateException.class, () -> decoder.seekAndReadAudioBlock(0, null, 0));
            while (decoder.readAndHandleMetadataBlock() != null) { }
        } finally {
            decoder.close();
        }
        assertThrows(IllegalStateException.class, () -> decoder.readAudioBlock(null, 0));
        assertThrows(IllegalStateException.class, () -> decoder.seekAndReadAudioBlock(0, null, 0));
    }

    @Test
    public void seekAsFirstAudioOperation_initializesWorkspace_andMatchesSamples() throws Exception {
        int[][] expected = samples(24);
        File f = flac(expected, 24);
        try (FlacDecoder decoder = new FlacDecoder(f)) {
            while (decoder.readAndHandleMetadataBlock() != null) { }
            int[][] actual = new int[2][65536];
            int count = decoder.seekAndReadAudioBlock(12345, actual, 7);
            assertArrayEquals(Arrays.copyOfRange(expected[0], 12345, 12345 + count),
                    Arrays.copyOfRange(actual[0], 7, 7 + count));
            assertArrayEquals(Arrays.copyOfRange(expected[1], 12345, 12345 + count),
                    Arrays.copyOfRange(actual[1], 7, 7 + count));
        }
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
