package apincer.music.server.jupnp.transport;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.Locale;

/**
 * DLNA time-based seeking (TimeSeekRange.dlna.org: npt=...). LG webOS TVs from 2022 seek only by
 * time, and Sony TVs prefer it (UMS renderer profiles), while the server streams files by byte.
 * This maps a time to the byte where the stream should start:
 * <ul>
 *   <li>FLAC: the SEEKTABLE point at or before the time (exact; the stream starts at that point),
 *       else a proportional estimate moved forward to the next frame start.</li>
 *   <li>MP3: proportional over the audio after the ID3v2 tag, moved to the next frame start
 *       (exact for constant bitrate, close for variable).</li>
 * </ul>
 * Other formats are not supported (null); WAV is left out because a mid-file fragment has no
 * header to describe it.
 */
public final class TimeSeek {
    private static final int SYNC_SCAN_LIMIT = 256 * 1024;

    /** Where to start streaming, and the time that position really corresponds to. */
    public static final class Position {
        public final long byteOffset;
        public final double startSeconds;

        Position(long byteOffset, double startSeconds) {
            this.byteOffset = byteOffset;
            this.startSeconds = startSeconds;
        }
    }

    private TimeSeek() {
    }

    /** True for the formats {@link #locate} supports: "flac" and "mp3". */
    public static boolean supports(String format) {
        return "flac".equals(format) || "mp3".equals(format);
    }

    /** The byte position for {@code seconds}, or null if unsupported, unknown length or past the end. */
    public static Position locate(File file, String format, double seconds, double durationSeconds) throws IOException {
        if (!supports(format) || durationSeconds <= 0 || seconds < 0 || seconds >= durationSeconds) return null;
        try (RandomAccessFile raf = new RandomAccessFile(file, "r")) {
            return "flac".equals(format) ? flac(raf, seconds, durationSeconds) : mp3(raf, seconds, durationSeconds);
        }
    }

    private static Position flac(RandomAccessFile raf, double seconds, double duration) throws IOException {
        byte[] magic = new byte[4];
        raf.readFully(magic);
        if (magic[0] != 'f' || magic[1] != 'L' || magic[2] != 'a' || magic[3] != 'C') return null;
        long sampleRate = 0;
        long bestSample = -1;
        long bestOffset = 0;
        boolean last = false;
        while (!last) {
            int header = raf.readUnsignedByte();
            last = (header & 0x80) != 0;
            int type = header & 0x7F;
            int length = (raf.readUnsignedByte() << 16) | (raf.readUnsignedByte() << 8) | raf.readUnsignedByte();
            long blockEnd = raf.getFilePointer() + length;
            if (type == 0 && length >= 18) { // STREAMINFO
                raf.skipBytes(10);
                sampleRate = raf.readLong() >>> 44;
            } else if (type == 3 && sampleRate > 0) { // SEEKTABLE (follows STREAMINFO)
                long target = (long) (seconds * sampleRate);
                for (int i = 0; i < length / 18; i++) {
                    long sample = raf.readLong();
                    long offset = raf.readLong();
                    raf.readShort();
                    if (sample == -1L) continue; // placeholder point
                    if (sample <= target && sample >= bestSample) {
                        bestSample = sample;
                        bestOffset = offset;
                    }
                }
            }
            raf.seek(blockEnd);
        }
        long audioStart = raf.getFilePointer();
        if (bestSample >= 0 && sampleRate > 0) {
            return new Position(audioStart + bestOffset, bestSample / (double) sampleRate);
        }
        long estimate = audioStart + (long) ((raf.length() - audioStart) * (seconds / duration));
        long frame = nextSync(raf, estimate, true);
        return frame < 0 ? null : new Position(frame, seconds);
    }

    private static Position mp3(RandomAccessFile raf, double seconds, double duration) throws IOException {
        long audioStart = 0;
        byte[] id3 = new byte[10];
        raf.readFully(id3);
        if (id3[0] == 'I' && id3[1] == 'D' && id3[2] == '3') {
            int size = ((id3[6] & 0x7F) << 21) | ((id3[7] & 0x7F) << 14) | ((id3[8] & 0x7F) << 7) | (id3[9] & 0x7F);
            audioStart = 10 + size + ((id3[5] & 0x10) != 0 ? 10 : 0); // footer flag
        }
        long audioEnd = raf.length();
        if (audioEnd >= 128) {
            raf.seek(audioEnd - 128);
            if (raf.read() == 'T' && raf.read() == 'A' && raf.read() == 'G') audioEnd -= 128; // ID3v1
        }
        long estimate = audioStart + (long) ((audioEnd - audioStart) * (seconds / duration));
        long frame = nextSync(raf, estimate, false);
        return frame < 0 ? null : new Position(frame, seconds);
    }

    /** First frame start at or after {@code from}: FLAC sync 0xFFF8/9, or a valid MPEG audio header. */
    private static long nextSync(RandomAccessFile raf, long from, boolean flac) throws IOException {
        byte[] buf = new byte[(int) Math.min(SYNC_SCAN_LIMIT, Math.max(0, raf.length() - from))];
        raf.seek(from);
        raf.readFully(buf);
        for (int i = 0; i + 2 < buf.length; i++) {
            if ((buf[i] & 0xFF) != 0xFF) continue;
            int b1 = buf[i + 1] & 0xFF;
            if (flac) {
                if ((b1 & 0xFE) == 0xF8) return from + i;
            } else {
                int b2 = buf[i + 2] & 0xFF;
                boolean valid = (b1 & 0xE0) == 0xE0 && ((b1 >> 1) & 3) != 0
                        && (b2 >> 4) != 0xF && ((b2 >> 2) & 3) != 3;
                if (valid) return from + i;
            }
        }
        return -1;
    }

    /** {start, end} in seconds from "npt=<start>-[<end>]" (seconds or H:MM:SS[.mmm]); end -1 if open; null if not npt. */
    public static double[] parseNpt(String header) {
        if (header == null) return null;
        String h = header.trim().toLowerCase(Locale.ROOT);
        if (!h.startsWith("npt=")) return null;
        String[] parts = h.substring(4).split("-", 2);
        try {
            double start = parseTime(parts[0].trim());
            double end = parts.length > 1 && !parts[1].trim().isEmpty() ? parseTime(parts[1].trim().split("/")[0]) : -1;
            return new double[]{start, end};
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static double parseTime(String s) {
        if (s.isEmpty()) throw new NumberFormatException("empty");
        double total = 0;
        for (String part : s.split(":")) total = total * 60 + Double.parseDouble(part);
        return total;
    }

    /** Seconds as the DLNA npt clock form H:MM:SS.mmm. */
    public static String npt(double seconds) {
        long ms = Math.round(seconds * 1000);
        return String.format(Locale.ROOT, "%d:%02d:%02d.%03d", ms / 3_600_000, (ms / 60_000) % 60, (ms / 1000) % 60, ms % 1000);
    }
}
