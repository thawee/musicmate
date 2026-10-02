package apincer.music.server.jupnp.transport;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Files;

public class TimeSeekTest {
    @Rule
    public TemporaryFolder temporary = new TemporaryFolder();

    // --- npt parsing and formatting ---

    @Test
    public void parseNpt_secondsAndClockForms() {
        assertArrayEquals(new double[]{12.5, -1}, TimeSeek.parseNpt("npt=12.5-"), 1e-9);
        assertArrayEquals(new double[]{62.25, 120}, TimeSeek.parseNpt("npt=0:01:02.250-0:02:00"), 1e-9);
        assertArrayEquals(new double[]{30, -1}, TimeSeek.parseNpt(" NPT=00:30 - "), 1e-9);
        assertArrayEquals(new double[]{0, -1}, TimeSeek.parseNpt("npt=0-"), 1e-9);
    }

    @Test
    public void parseNpt_rejectsOtherForms() {
        assertNull(TimeSeek.parseNpt("bytes=0-100"));
        assertNull(TimeSeek.parseNpt("npt=abc-"));
        assertNull(TimeSeek.parseNpt(null));
    }

    @Test
    public void npt_isHMMSSmmm() {
        assertEquals("0:00:12.500", TimeSeek.npt(12.5));
        assertEquals("1:02:03.000", TimeSeek.npt(3723));
    }

    // --- FLAC ---

    private static final int RATE = 44_100;

    /** "fLaC", STREAMINFO (60 s), optional SEEKTABLE (0 s, 10 s, 20 s), then frames with sync codes. */
    private File flac(boolean withSeekTable) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(new byte[]{'f', 'L', 'a', 'C'});
        out.write(withSeekTable ? 0x00 : 0x80); // STREAMINFO, last block unless a seek table follows
        out.write(new byte[]{0, 0, 34});
        ByteBuffer info = ByteBuffer.allocate(34);
        info.putShort((short) 4096).putShort((short) 4096).put(new byte[6]);
        long packed = ((long) RATE << 44) | (1L << 41) | (15L << 36) | (60L * RATE);
        info.putLong(packed).put(new byte[16]);
        out.write(info.array());
        if (withSeekTable) {
            out.write(0x83); // SEEKTABLE, last block
            out.write(new byte[]{0, 0, 18 * 3});
            ByteBuffer table = ByteBuffer.allocate(18 * 3);
            long[][] points = {{0, 0}, {10L * RATE, 50_000}, {20L * RATE, 100_000}};
            for (long[] p : points) table.putLong(p[0]).putLong(p[1]).putShort((short) 4096);
            out.write(table.array());
        }
        byte[] audio = new byte[300_000];
        for (int at : new int[]{0, 50_000, 100_000, 150_000, 200_000, 250_000}) {
            audio[at] = (byte) 0xFF;
            audio[at + 1] = (byte) 0xF8; // FLAC frame sync
        }
        out.write(audio);
        File f = temporary.newFile(withSeekTable ? "seek.flac" : "noseek.flac");
        Files.write(f.toPath(), out.toByteArray());
        return f;
    }

    private static final int FLAC_HEADER_WITH_TABLE = 4 + 4 + 34 + 4 + 54;
    private static final int FLAC_HEADER_NO_TABLE = 4 + 4 + 34;

    @Test
    public void flac_usesTheSeekPointAtOrBeforeTheTime() throws IOException {
        File f = flac(true);
        TimeSeek.Position p = TimeSeek.locate(f, "flac", 15.0, 60.0);
        assertEquals(FLAC_HEADER_WITH_TABLE + 50_000, p.byteOffset);
        assertEquals(10.0, p.startSeconds, 1e-9); // the stream really starts at the seek point
        p = TimeSeek.locate(f, "flac", 25.0, 60.0);
        assertEquals(FLAC_HEADER_WITH_TABLE + 100_000, p.byteOffset);
        assertEquals(20.0, p.startSeconds, 1e-9);
        assertEquals(FLAC_HEADER_WITH_TABLE, TimeSeek.locate(f, "flac", 0.0, 60.0).byteOffset);
    }

    @Test
    public void flac_withoutSeekTable_isProportional_alignedToTheNextFrame() throws IOException {
        File f = flac(false);
        // 30 s of 60 s -> half of the 300,000 audio bytes -> the frame at 150,000
        TimeSeek.Position p = TimeSeek.locate(f, "flac", 30.0, 60.0);
        assertEquals(FLAC_HEADER_NO_TABLE + 150_000, p.byteOffset);
        // 29 s -> 145,000 -> next frame at 150,000
        assertEquals(FLAC_HEADER_NO_TABLE + 150_000, TimeSeek.locate(f, "flac", 29.0, 60.0).byteOffset);
    }

    @Test
    public void pastTheEnd_orUnsupportedFormat_isNull() throws IOException {
        File f = flac(true);
        assertNull(TimeSeek.locate(f, "flac", 60.0, 60.0));
        assertNull(TimeSeek.locate(f, "flac", 10.0, 0.0)); // unknown duration
        assertNull(TimeSeek.locate(f, "dsf", 10.0, 60.0));
    }

    // --- MP3 ---

    @Test
    public void mp3_isProportionalAfterTheId3Tag_alignedToAFrame() throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        // ID3v2.4 header with a syncsafe size of 1,000 bytes
        out.write(new byte[]{'I', 'D', '3', 4, 0, 0, 0, 0, 0x07, 0x68});
        out.write(new byte[1000]);
        byte[] audio = new byte[100_000];
        for (int at = 0; at < audio.length; at += 1000) {
            audio[at] = (byte) 0xFF;
            audio[at + 1] = (byte) 0xFB; // MPEG-1 Layer III
            audio[at + 2] = (byte) 0x90; // 128 kbps, 44.1 kHz
        }
        out.write(audio);
        File f = temporary.newFile("a.mp3");
        Files.write(f.toPath(), out.toByteArray());
        TimeSeek.Position p = TimeSeek.locate(f, "mp3", 50.0, 100.0);
        assertEquals(1010 + 50_000, p.byteOffset);
        // 49.95 s -> 49,950 -> next frame at 50,000
        assertEquals(1010 + 50_000, TimeSeek.locate(f, "mp3", 49.95, 100.0).byteOffset);
    }
}
